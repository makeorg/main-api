package org.make.api.user

import com.github.t3hnar.bcrypt._
import org.make.api.proposal.{ProposalServiceComponent, ProposalsResultSeededResponse}
import org.make.api.technical.businessconfig.BusinessConfig
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, ShortenedNames}
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.userhistory.UserEvent.OrganisationRegisteredEvent
import org.make.api.userhistory.UserHistoryActor.RequestUserVotedProposals
import org.make.api.userhistory.UserHistoryCoordinatorServiceComponent
import org.make.core.profile.Profile
import org.make.core.proposal.{ProposalSearchFilter, SearchFilters, SearchQuery}
import org.make.core.user._
import org.make.core.{DateHelper, RequestContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success

trait OrganisationServiceComponent {
  def organisationService: OrganisationService
}

trait OrganisationService extends ShortenedNames {
  def getOrganisation(id: UserId): Future[Option[User]]
  def getOrganisations: Future[Seq[User]]
  def register(organisationRegisterData: OrganisationRegisterData, requestContext: RequestContext): Future[User]
  def update(organisationId: UserId, organisationUpdateDate: OrganisationUpdateData): Future[Option[UserId]]
  def getVotedProposals(organisationId: UserId,
                        maybeUserId: Option[UserId],
                        requestContext: RequestContext): Future[ProposalsResultSeededResponse]
}

case class OrganisationRegisterData(name: String,
                                    email: String,
                                    password: Option[String],
                                    avatar: Option[String],
                                    country: String,
                                    language: String)

case class OrganisationUpdateData(name: Option[String], email: Option[String], avatar: Option[String])

trait DefaultOrganisationServiceComponent extends OrganisationServiceComponent with ShortenedNames {
  this: IdGeneratorComponent
    with PersistentUserServiceComponent
    with EventBusServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with ProposalServiceComponent =>

  val organisationService: OrganisationService = new OrganisationService {
    override def getOrganisation(userId: UserId): Future[Option[User]] = {
      persistentUserService.get(userId).map(_.filter(_.isOrganisation))
    }

    override def getOrganisations: Future[Seq[User]] = {
      persistentUserService.findAllOrganisations()
    }

    private def registerOrganisation(organisationRegisterData: OrganisationRegisterData,
                                     lowerCasedEmail: String,
                                     country: String,
                                     language: String): Future[User] = {
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
            twitterId = None,
            facebookId = None,
            googleId = None,
            gender = None,
            genderName = None,
            postalCode = None,
            karmaLevel = None,
            locale = None,
            optInNewsletter = false
          )
        )
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

      result.onComplete {
        case Success(user) =>
          eventBusService.publish(
            OrganisationRegisteredEvent(
              connectedUserId = Some(user.userId),
              userId = user.userId,
              requestContext = requestContext,
              email = user.email,
              country = user.country,
              language = user.language
            )
          )
        case _ =>
      }

      result
    }

    override def update(organisationId: UserId,
                        organisationUpdateDate: OrganisationUpdateData): Future[Option[UserId]] = {
      for {
        emailExists <- updateMailExists(organisationUpdateDate.email.map(_.toLowerCase))
        _           <- verifyEmail(organisationUpdateDate.email.map(_.toLowerCase).getOrElse(""), emailExists)
        update <- persistentUserService
          .get(organisationId)
          .flatMap(_.map { registeredOrganisation =>
            val updateOrganisation =
              registeredOrganisation.copy(
                organisationName = organisationUpdateDate.name.orElse(registeredOrganisation.organisationName),
                email = organisationUpdateDate.email.getOrElse(registeredOrganisation.email),
                profile = registeredOrganisation.profile.map(
                  _.copy(
                    avatarUrl =
                      organisationUpdateDate.avatar.orElse(registeredOrganisation.profile.flatMap(_.avatarUrl))
                  )
                )
              )
            persistentUserService.modify(updateOrganisation).map {
              case Right(organisation) => Some(organisation.userId)
              case Left(_)             => None
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

    override def getVotedProposals(organisationId: UserId,
                                   maybeUserId: Option[UserId],
                                   requestContext: RequestContext): Future[ProposalsResultSeededResponse] = {
      userHistoryCoordinatorService.retrieveVotedProposals(RequestUserVotedProposals(organisationId)).flatMap {
        case proposalIds if proposalIds.isEmpty =>
          Future.successful(ProposalsResultSeededResponse(total = 0, Seq.empty, None))
        case proposalIds =>
          proposalService.searchForUser(
            userId = maybeUserId,
            query = SearchQuery(Some(SearchFilters(proposal = Some(ProposalSearchFilter(proposalIds))))),
            maybeSeed = None,
            requestContext = requestContext
          )
      }
    }
  }
}