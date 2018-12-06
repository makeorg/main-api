/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.organisation

import com.github.t3hnar.bcrypt._
import com.sksamuel.elastic4s.searches.suggestion.Fuzziness
import org.make.api.proposal.PublishedProposalEvent.ReindexProposal
import org.make.api.proposal.{
  ProposalResultWithUserVote,
  ProposalServiceComponent,
  ProposalsResultWithUserVoteSeededResponse
}
import org.make.api.technical.businessconfig.BusinessConfig
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, ShortenedNames}
import org.make.api.user.PersistentUserServiceComponent
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.userhistory.UserEvent.{OrganisationRegisteredEvent, OrganisationUpdatedEvent}
import org.make.api.userhistory.UserHistoryActor.{RequestUserVotedProposals, RequestVoteValues}
import org.make.api.userhistory.UserHistoryCoordinatorServiceComponent
import org.make.core.history.HistoryActions
import org.make.core.profile.Profile
import org.make.core.proposal._
import org.make.core.reference.{Country, Language}
import org.make.core.user._
import org.make.core.user.indexed.OrganisationSearchResult
import org.make.core.{user, DateHelper, RequestContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OrganisationServiceComponent {
  def organisationService: OrganisationService
}

trait OrganisationService extends ShortenedNames {
  def getOrganisation(id: UserId): Future[Option[User]]
  def getOrganisations: Future[Seq[User]]
  def search(organisationName: Option[String], slug: Option[String]): Future[OrganisationSearchResult]
  def register(organisationRegisterData: OrganisationRegisterData, requestContext: RequestContext): Future[User]
  def update(organisationId: UserId,
             organisationUpdateDate: OrganisationUpdateData,
             requestContext: RequestContext): Future[Option[UserId]]
  def getVotedProposals(organisationId: UserId,
                        maybeUserId: Option[UserId],
                        filterVotes: Option[Seq[VoteKey]],
                        filterQualifications: Option[Seq[QualificationKey]],
                        requestContext: RequestContext): Future[ProposalsResultWithUserVoteSeededResponse]
}

case class OrganisationRegisterData(name: String,
                                    email: String,
                                    password: Option[String],
                                    avatar: Option[String],
                                    description: Option[String],
                                    country: Country,
                                    language: Language)

case class OrganisationUpdateData(name: Option[String],
                                  email: Option[String],
                                  avatar: Option[String],
                                  description: Option[String])

trait DefaultOrganisationServiceComponent extends OrganisationServiceComponent with ShortenedNames {
  this: IdGeneratorComponent
    with PersistentUserServiceComponent
    with EventBusServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with ProposalServiceComponent
    with OrganisationSearchEngineComponent =>

  val organisationService: OrganisationService = new OrganisationService {
    override def getOrganisation(userId: UserId): Future[Option[User]] = {
      persistentUserService.get(userId).map(_.filter(_.isOrganisation))
    }

    override def getOrganisations: Future[Seq[User]] = {
      persistentUserService.findAllOrganisations()
    }

    override def search(organisationName: Option[String], slug: Option[String]): Future[OrganisationSearchResult] = {
      elasticsearchOrganisationAPI.searchOrganisations(
        OrganisationSearchQuery(
          filters = OrganisationSearchFilters
            .parse(
              organisationName =
                organisationName.map(orgaName => OrganisationNameSearchFilter(orgaName, Some(Fuzziness.Auto))),
              slug = slug.map(user.SlugSearchFilter.apply)
            )
        )
      )
    }

    private def registerOrganisation(organisationRegisterData: OrganisationRegisterData,
                                     lowerCasedEmail: String,
                                     country: Country,
                                     language: Language): Future[User] = {
      val user = User(
        userId = idGenerator.nextUserId(),
        email = lowerCasedEmail,
        firstName = None,
        lastName = None,
        organisationName = Some(organisationRegisterData.name),
        lastIp = None,
        hashedPassword = organisationRegisterData.password.map(_.bcrypt),
        enabled = true,
        emailVerified = true,
        isOrganisation = true,
        lastConnection = DateHelper.now(),
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq(Role.RoleActor),
        country = country,
        language = language,
        profile = Some(
          Profile(
            dateOfBirth = None,
            avatarUrl = organisationRegisterData.avatar,
            profession = None,
            phoneNumber = None,
            description = organisationRegisterData.description,
            twitterId = None,
            facebookId = None,
            googleId = None,
            gender = None,
            genderName = None,
            postalCode = None,
            karmaLevel = None,
            locale = None,
            optInNewsletter = false,
            socioProfessionalCategory = None
          )
        ),
        publicProfile = true
      )
      persistentUserService.persist(user)
    }

    override def register(organisationRegisterData: OrganisationRegisterData,
                          requestContext: RequestContext): Future[User] = {

      val country = BusinessConfig.validateCountry(organisationRegisterData.country)
      val language =
        BusinessConfig.validateLanguage(organisationRegisterData.country, organisationRegisterData.language)

      val lowerCasedEmail: String = organisationRegisterData.email.toLowerCase()

      val result = for {
        emailExists <- persistentUserService.emailExists(lowerCasedEmail)
        _           <- verifyEmail(lowerCasedEmail, emailExists)
        user        <- registerOrganisation(organisationRegisterData, lowerCasedEmail, country, language)
      } yield user

      result.map { user =>
        eventBusService.publish(
          OrganisationRegisteredEvent(
            connectedUserId = Some(user.userId),
            userId = user.userId,
            requestContext = requestContext,
            email = user.email,
            country = user.country,
            language = user.language,
            eventDate = DateHelper.now()
          )
        )
        user
      }
    }

    private def updateProposalsFromOrganisation(organisationId: UserId): Future[Unit] = {
      for {
        _ <- getVotedProposals(organisationId, None, None, None, RequestContext.empty)
          .map(
            result =>
              result.results.foreach(
                proposalWithVote =>
                  eventBusService
                    .publish(ReindexProposal(proposalWithVote.proposal.id, DateHelper.now(), RequestContext.empty))
            )
          )
        _ <- proposalService
          .searchForUser(
            userId = Some(organisationId),
            query = SearchQuery(
              filters = Some(
                SearchFilters(
                  user = Some(UserSearchFilter(userId = organisationId)),
                  status = Some(StatusSearchFilter(ProposalStatus.statusMap.filter {
                    case (_, status) => status != ProposalStatus.Archived
                  }.values.toSeq))
                )
              )
            ),
            requestContext = RequestContext.empty
          )
          .map(
            result =>
              result.results.foreach(
                proposal =>
                  eventBusService
                    .publish(ReindexProposal(proposal.id, DateHelper.now(), RequestContext.empty))
            )
          )
      } yield {}
    }

    override def update(organisationId: UserId,
                        organisationUpdateData: OrganisationUpdateData,
                        requestContext: RequestContext): Future[Option[UserId]] = {
      for {
        emailExists <- updateMailExists(organisationUpdateData.email.map(_.toLowerCase))
        _           <- verifyEmail(organisationUpdateData.email.map(_.toLowerCase).getOrElse(""), emailExists)
        update <- persistentUserService
          .get(organisationId)
          .flatMap(_.map { registeredOrganisation =>
            val updateOrganisation =
              registeredOrganisation.copy(
                organisationName = organisationUpdateData.name.orElse(registeredOrganisation.organisationName),
                email = organisationUpdateData.email.getOrElse(registeredOrganisation.email),
                profile = registeredOrganisation.profile.map(
                  _.copy(
                    avatarUrl =
                      organisationUpdateData.avatar.orElse(registeredOrganisation.profile.flatMap(_.avatarUrl)),
                    description =
                      organisationUpdateData.description.orElse(registeredOrganisation.profile.flatMap(_.description))
                  )
                )
              )
            persistentUserService.modify(updateOrganisation).flatMap {
              case Right(organisation) =>
                eventBusService.publish(
                  OrganisationUpdatedEvent(
                    connectedUserId = Some(organisation.userId),
                    userId = organisation.userId,
                    requestContext = requestContext,
                    country = organisation.country,
                    language = organisation.language,
                    eventDate = DateHelper.now()
                  )
                )
                updateProposalsFromOrganisation(organisationId)
                  .flatMap(_ => Future.successful(Some(organisation.userId)))
              case Left(_) => Future.successful(None)
            }
          }.getOrElse(Future.successful(None)))
      } yield update
    }

    private def verifyEmail(lowerCasedEmail: String, emailExists: Boolean): Future[Boolean] = {
      if (emailExists) {
        Future.failed(EmailAlreadyRegisteredException(lowerCasedEmail))
      } else {
        Future.successful(true)
      }
    }

    private def updateMailExists(lowerCasedEmail: Option[String]): Future[Boolean] = {
      lowerCasedEmail.map { mail =>
        persistentUserService.emailExists(mail)
      }.getOrElse(Future.successful(false))
    }

    override def getVotedProposals(
      organisationId: UserId,
      maybeUserId: Option[UserId],
      filterVotes: Option[Seq[VoteKey]],
      filterQualifications: Option[Seq[QualificationKey]],
      requestContext: RequestContext
    ): Future[ProposalsResultWithUserVoteSeededResponse] = {
      val futureProposalWithVotes: Future[Map[ProposalId, HistoryActions.VoteAndQualifications]] = for {
        proposalIds <- userHistoryCoordinatorService.retrieveVotedProposals(
          RequestUserVotedProposals(
            organisationId,
            filterVotes = filterVotes,
            filterQualifications = filterQualifications
          )
        )
        withVotes <- userHistoryCoordinatorService
          .retrieveVoteAndQualifications(RequestVoteValues(organisationId, proposalIds))
      } yield withVotes

      futureProposalWithVotes.flatMap {
        case proposalIdsWithVotes if proposalIdsWithVotes.isEmpty =>
          Future.successful(ProposalsResultWithUserVoteSeededResponse(total = 0, Seq.empty, None))
        case proposalIdsWithVotes =>
          val proposalIds: Seq[ProposalId] = proposalIdsWithVotes.map {
            case (proposalId, _) => proposalId
          }.toSeq
          proposalService
            .searchForUser(
              userId = maybeUserId,
              query = SearchQuery(Some(SearchFilters(proposal = Some(ProposalSearchFilter(proposalIds))))),
              requestContext = requestContext
            )
            .map { results =>
              ProposalsResultWithUserVoteSeededResponse(
                total = results.total,
                results = results.results.map { proposal =>
                  val proposalVoteAndQualification = proposalIdsWithVotes(proposal.id)
                  ProposalResultWithUserVote(
                    proposal,
                    proposalVoteAndQualification.voteKey,
                    proposalVoteAndQualification.date
                  )
                },
                seed = results.seed
              )
            }
      }
    }

  }
}
