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
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal.PublishedProposalEvent.ReindexProposal
import org.make.api.proposal._
import org.make.api.technical.auth.UserTokenGeneratorComponent
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, ShortenedNames}
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user.{
  PersistentUserServiceComponent,
  PersistentUserToAnonymizeServiceComponent,
  UserServiceComponent
}
import org.make.api.userhistory.UserHistoryActor.{RequestUserVotedProposals, RequestVoteValues}
import org.make.api.userhistory.{
  OrganisationEmailChangedEvent,
  OrganisationRegisteredEvent,
  OrganisationUpdatedEvent,
  UserHistoryCoordinatorServiceComponent
}
import org.make.core.common.indexed.Sort
import org.make.core.history.HistoryActions
import org.make.core.operation.OperationKind
import org.make.core.profile.Profile
import org.make.core.proposal.{CountrySearchFilter => _, LanguageSearchFilter => _, _}
import org.make.core.reference.{Country, Language}
import org.make.core.user._
import org.make.core.user.indexed.OrganisationSearchResult
import org.make.core.{user, DateHelper, Order, RequestContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait OrganisationServiceComponent {
  def organisationService: OrganisationService
}

trait OrganisationService extends ShortenedNames {
  def getOrganisation(id: UserId): Future[Option[User]]
  def getOrganisations: Future[Seq[User]]
  def find(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    organisationName: Option[String]
  ): Future[Seq[User]]
  def count(organisationName: Option[String]): Future[Int]
  def search(
    organisationName: Option[String],
    slug: Option[String],
    organisationIds: Option[Seq[UserId]],
    country: Option[Country],
    language: Option[Language]
  ): Future[OrganisationSearchResult]
  def searchWithQuery(query: OrganisationSearchQuery): Future[OrganisationSearchResult]
  def register(organisationRegisterData: OrganisationRegisterData, requestContext: RequestContext): Future[User]
  def update(
    organisation: User,
    moderatorId: Option[UserId],
    oldEmail: String,
    requestContext: RequestContext
  ): Future[UserId]
  def getVotedProposals(
    organisationId: UserId,
    maybeUserId: Option[UserId],
    filterVotes: Option[Seq[VoteKey]],
    filterQualifications: Option[Seq[QualificationKey]],
    sort: Option[Sort],
    limit: Option[Int],
    skip: Option[Int],
    requestContext: RequestContext
  ): Future[ProposalsResultWithUserVoteSeededResponse]
}

final case class OrganisationRegisterData(
  name: String,
  email: String,
  password: Option[String],
  avatar: Option[String],
  description: Option[String],
  country: Country,
  website: Option[String]
)

final case class OrganisationUpdateData(
  name: Option[String],
  email: Option[String],
  avatar: Option[String],
  description: Option[String],
  website: Option[String]
)

trait DefaultOrganisationServiceComponent extends OrganisationServiceComponent with ShortenedNames {
  this: IdGeneratorComponent
    with UserServiceComponent
    with PersistentUserServiceComponent
    with EventBusServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with ProposalServiceComponent
    with ProposalSearchEngineComponent
    with OrganisationSearchEngineComponent
    with PersistentUserToAnonymizeServiceComponent
    with UserTokenGeneratorComponent
    with MakeSettingsComponent =>

  override lazy val organisationService: OrganisationService = new DefaultOrganisationService

  class DefaultOrganisationService extends OrganisationService {

    val resetTokenB2BExpiresIn: Long = makeSettings.resetTokenB2BExpiresIn.toSeconds

    override def getOrganisation(userId: UserId): Future[Option[User]] = {
      persistentUserService.findByUserIdAndUserType(userId, UserType.UserTypeOrganisation)
    }

    override def getOrganisations: Future[Seq[User]] = {
      persistentUserService.findAllOrganisations()
    }

    /**
      * This method fetch the organisations from cockroach
      * start and end are here to paginate the result
      * sort and order are here to sort the result
      */
    override def find(
      start: Start,
      end: Option[End],
      sort: Option[String],
      order: Option[Order],
      organisationName: Option[String]
    ): Future[Seq[User]] = {
      persistentUserService.findOrganisations(start, end, sort, order, organisationName)
    }

    override def count(organisationName: Option[String]): Future[Int] = {
      persistentUserService.countOrganisations(organisationName)
    }

    /**
      * This method fetch the organisations from elasticsearch
      * organisations can be search by name or slug
      */
    override def search(
      organisationName: Option[String],
      slug: Option[String],
      organisationIds: Option[Seq[UserId]],
      country: Option[Country],
      language: Option[Language]
    ): Future[OrganisationSearchResult] = {
      elasticsearchOrganisationAPI.searchOrganisations(
        OrganisationSearchQuery(filters = OrganisationSearchFilters
          .parse(
            organisationName = organisationName.map(orgaName => OrganisationNameSearchFilter(orgaName)),
            slug = slug.map(user.SlugSearchFilter.apply),
            organisationIds = organisationIds.map(OrganisationIdsSearchFilter.apply),
            country = country.map(CountrySearchFilter.apply),
            language = language.map(LanguageSearchFilter.apply)
          )
        )
      )
    }

    override def searchWithQuery(query: OrganisationSearchQuery): Future[OrganisationSearchResult] = {
      elasticsearchOrganisationAPI.searchOrganisations(query)
    }

    private def registerOrganisation(
      organisationRegisterData: OrganisationRegisterData,
      lowerCasedEmail: String,
      country: Country,
      resetToken: String
    ): Future[User] = {
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
        userType = UserType.UserTypeOrganisation,
        lastConnection = DateHelper.now(),
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = Some(resetToken),
        resetTokenExpiresAt = Some(DateHelper.now().plusSeconds(resetTokenB2BExpiresIn)),
        roles = Seq(Role.RoleActor),
        country = country,
        profile = Profile.parseProfile(
          avatarUrl = organisationRegisterData.avatar,
          description = organisationRegisterData.description,
          optInNewsletter = false,
          website = organisationRegisterData.website
        ),
        publicProfile = true,
        availableQuestions = Seq.empty,
        anonymousParticipation = false,
        privacyPolicyApprovalDate = Some(DateHelper.now())
      )
      persistentUserService.persist(user)
    }

    override def register(
      organisationRegisterData: OrganisationRegisterData,
      requestContext: RequestContext
    ): Future[User] = {

      val country = organisationRegisterData.country

      val lowerCasedEmail: String = organisationRegisterData.email.toLowerCase()

      val result = for {
        emailExists <- persistentUserService.emailExists(lowerCasedEmail)
        _           <- verifyEmail(lowerCasedEmail, emailExists)
        resetToken  <- userTokenGenerator.generateResetToken().map { case (_, token) => token }
        user        <- registerOrganisation(organisationRegisterData, lowerCasedEmail, country, resetToken)
      } yield user

      result.map { user =>
        eventBusService.publish(
          OrganisationRegisteredEvent(
            connectedUserId = Some(user.userId),
            userId = user.userId,
            requestContext = requestContext,
            email = user.email,
            country = user.country,
            eventDate = DateHelper.now(),
            eventId = Some(idGenerator.nextEventId())
          )
        )
        user
      }
    }

    private def updateProposalsFromOrganisation(
      organisationId: UserId,
      requestContext: RequestContext
    ): Future[Unit] = {
      for {
        _ <- getVotedProposals(
          organisationId,
          maybeUserId = None,
          filterVotes = None,
          filterQualifications = None,
          sort = None,
          limit = None,
          skip = None,
          requestContext
        ).map(
          result =>
            result.results.foreach(
              proposalWithVote =>
                eventBusService
                  .publish(
                    ReindexProposal(
                      proposalWithVote.proposal.id,
                      DateHelper.now(),
                      requestContext,
                      Some(idGenerator.nextEventId())
                    )
                  )
            )
        )
        _ <- elasticsearchProposalAPI
          .searchProposals(searchQuery = SearchQuery(filters = Some(
            SearchFilters(
              users = Some(UserSearchFilter(userIds = Seq(organisationId))),
              status = Some(StatusSearchFilter(ProposalStatus.values.filter(_ != ProposalStatus.Archived)))
            )
          )
          )
          )
          .map(
            result =>
              result.results.foreach(
                proposal =>
                  eventBusService
                    .publish(
                      ReindexProposal(proposal.id, DateHelper.now(), requestContext, Some(idGenerator.nextEventId()))
                    )
              )
          )
      } yield {}
    }

    private def updateOrganisationEmail(
      organisation: User,
      moderatorId: Option[UserId],
      newEmail: Option[String],
      oldEmail: String,
      requestContext: RequestContext
    ): Future[Unit] = {
      newEmail match {
        case Some(email) =>
          persistentUserToAnonymizeService.create(oldEmail).map { _ =>
            eventBusService.publish(
              OrganisationEmailChangedEvent(
                connectedUserId = moderatorId,
                userId = organisation.userId,
                requestContext = requestContext,
                country = organisation.country,
                eventDate = DateHelper.now(),
                oldEmail = oldEmail,
                newEmail = email,
                eventId = Some(idGenerator.nextEventId())
              )
            )
          }
        case None => Future.unit
      }
    }

    override def update(
      organisation: User,
      moderatorId: Option[UserId],
      oldEmail: String,
      requestContext: RequestContext
    ): Future[UserId] = {

      val newEmail: Option[String] = organisation.email match {
        case email if email.toLowerCase == oldEmail.toLowerCase => None
        case email                                              => Some(email)
      }

      for {
        emailExists <- updateMailExists(newEmail.map(_.toLowerCase))
        _           <- verifyEmail(organisation.email.toLowerCase, emailExists)
        update <- persistentUserService.modifyOrganisation(organisation).flatMap {
          case Right(orga) =>
            updateOrganisationEmail(organisation, moderatorId, newEmail, oldEmail, requestContext).flatMap { _ =>
              updateProposalsFromOrganisation(orga.userId, requestContext).flatMap { _ =>
                eventBusService.publish(
                  OrganisationUpdatedEvent(
                    connectedUserId = Some(orga.userId),
                    userId = orga.userId,
                    requestContext = requestContext,
                    country = orga.country,
                    eventDate = DateHelper.now(),
                    eventId = Some(idGenerator.nextEventId())
                  )
                )
                Future.successful(orga.userId)
              }
            }
          case Left(e) => Future.failed(e)
        }
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
      sort: Option[Sort],
      limit: Option[Int],
      skip: Option[Int],
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
          val proposalIds: Seq[ProposalId] = proposalIdsWithVotes.toSeq.sortWith {
            case ((_, firstVotesAndQualifications), (_, nextVotesAndQualifications)) =>
              firstVotesAndQualifications.date.isAfter(nextVotesAndQualifications.date)
          }.map {
            case (proposalId, _) => proposalId
          }
          proposalService
            .searchForUser(
              userId = Some(organisationId),
              query = SearchQuery(
                filters = Some(
                  SearchFilters(
                    proposal = Some(ProposalSearchFilter(proposalIds = proposalIds)),
                    operationKinds = Some(OperationKindsSearchFilter(OperationKind.publicKinds))
                  )
                ),
                sort = sort,
                limit = limit,
                skip = skip
              ),
              requestContext = requestContext
            )
            .map { proposalResultSeededResponse =>
              proposalResultSeededResponse.copy(results = proposalResultSeededResponse.results.sortWith {
                case (first, next) => proposalIds.indexOf(first.id) < proposalIds.indexOf(next.id)
              })
            }
            .map { proposalResults =>
              ProposalsResultWithUserVoteSeededResponse(
                total = proposalResults.total,
                results = proposalResults.results.map { proposal =>
                  val proposalVoteAndQualification = proposalIdsWithVotes(proposal.id)
                  ProposalResultWithUserVote(
                    proposal,
                    proposalVoteAndQualification.voteKey,
                    proposalVoteAndQualification.date,
                    proposal.votes.find(_.voteKey == proposalVoteAndQualification.voteKey)
                  )
                },
                seed = None
              )
            }
      }
    }

  }
}
