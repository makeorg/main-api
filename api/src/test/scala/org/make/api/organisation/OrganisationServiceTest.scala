package org.make.api.organisation

import org.make.api.MakeUnitTest
import org.make.api.technical.{EventBusService, EventBusServiceComponent, IdGenerator, IdGeneratorComponent}
import org.make.api.user.DefaultPersistentUserServiceComponent.UpdateFailed
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user._
import org.make.api.userhistory.UserEvent.OrganisationRegisteredEvent
import org.make.core.profile.Profile
import org.make.core.user.Role.RoleOrganisation
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify}
import org.mockito.{ArgumentMatcher, ArgumentMatchers, Mockito}
import org.scalatest.RecoverMethods
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class OrganisationServiceTest
    extends MakeUnitTest
    with DefaultOrganisationServiceComponent
    with IdGeneratorComponent
    with PersistentUserServiceComponent
    with EventBusServiceComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val eventBusService: EventBusService = mock[EventBusService]

  class MatchRegisterEvents(maybeUserId: Option[UserId]) extends ArgumentMatcher[AnyRef] {
    override def matches(argument: AnyRef): Boolean =
      argument match {
        case i: OrganisationRegisteredEvent if maybeUserId.contains(i.userId) => true
        case _                                                                => false
      }
  }

  val returnedOrganisation = User(
    userId = UserId("AAA-BBB-CCC"),
    email = "any@mail.com",
    firstName = None,
    lastName = None,
    organisationName = Some("John Doe Corp."),
    lastIp = None,
    hashedPassword = Some("passpass"),
    enabled = true,
    emailVerified = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleOrganisation),
    country = "FR",
    language = "fr",
    profile = Some(Profile(None, Some("avatarUrl"), None, None, None, None, None, None, None, None, None, None))
  )

  val returnedOrganisation2 = User(
    userId = UserId("AAA-BBB-CCC-DDD"),
    email = "some@mail.com",
    firstName = None,
    lastName = None,
    organisationName = Some("Jeanne Done Corp."),
    lastIp = None,
    hashedPassword = Some("passpass"),
    enabled = true,
    emailVerified = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleOrganisation),
    country = "FR",
    language = "fr",
    profile = None
  )

  feature("Get organisation") {
    scenario("get organisation") {
      Mockito.when(persistentUserService.get(any[UserId])).thenReturn(Future.successful(Some(returnedOrganisation)))

      whenReady(organisationService.getOrganisation(UserId("AAA-BBB-CCC")), Timeout(2.seconds)) { user =>
        user shouldBe a[Option[_]]
        user.isDefined shouldBe true
        user.get.email shouldBe "any@mail.com"
      }
    }
  }

  feature("register organisation") {
    scenario("successfully register an organisation") {
      Mockito.reset(eventBusService)
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(false))

      Mockito
        .when(
          persistentUserService
            .persist(any[User])
        )
        .thenReturn(Future.successful(returnedOrganisation))

      val futureOrganisation = organisationService.register(
        OrganisationRegisterData(
          name = "John Doe Corp.",
          email = "any@mail.com",
          password = Some("passopasso"),
          avatar = None,
          country = "FR",
          language = "fr"
        ),
        RequestContext.empty
      )

      whenReady(futureOrganisation, Timeout(2.seconds)) { user =>
        user shouldBe a[User]
        user.email should be("any@mail.com")
        user.organisationName should be(Some("John Doe Corp."))
      }

      verify(eventBusService, times(1))
        .publish(
          ArgumentMatchers
            .argThat(new MatchRegisterEvents(Some(returnedOrganisation.userId)))
        )
    }

    scenario("email already exists") {
      Mockito.reset(eventBusService)
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(true))

      val futureOrganisation = organisationService.register(
        OrganisationRegisterData(
          name = "John Doe Corp.",
          email = "any@mail.com",
          password = Some("passopasso"),
          avatar = None,
          country = "FR",
          language = "fr"
        ),
        RequestContext.empty
      )

      RecoverMethods.recoverToSucceededIf[EmailAlreadyRegisteredException](futureOrganisation)

      verify(eventBusService, times(0))
        .publish(
          ArgumentMatchers
            .argThat(new MatchRegisterEvents(Some(returnedOrganisation.userId)))
        )
    }
  }

  feature("update organisation") {
    scenario("successfully update an organisation by changing the name and avatar") {
      Mockito.when(persistentUserService.get(any[UserId])).thenReturn(Future.successful(Some(returnedOrganisation)))
      Mockito.when(persistentUserService.modify(any[User])).thenReturn(Future.successful(Right(returnedOrganisation)))

      val futureOrganisation = organisationService.update(
        UserId("AAA-BBB-CCC"),
        OrganisationUpdateData(name = Some("Jeanne Done Corp."), email = None, avatar = Some("anotherAvatarUrl"))
      )

      whenReady(futureOrganisation, Timeout(2.seconds)) { organisationId =>
        organisationId.isDefined shouldBe true
      }
    }

    scenario("successfully update an organisation without changing anything") {
      Mockito.when(persistentUserService.get(any[UserId])).thenReturn(Future.successful(Some(returnedOrganisation)))
      Mockito.when(persistentUserService.modify(any[User])).thenReturn(Future.successful(Right(returnedOrganisation)))

      val futureOrganisation = organisationService.update(
        UserId("AAA-BBB-CCC"),
        OrganisationUpdateData(name = None, email = None, avatar = None)
      )

      whenReady(futureOrganisation, Timeout(2.seconds)) { organisationId =>
        organisationId.isDefined shouldBe true
      }
    }

    scenario("try to update with mail already exists") {
      Mockito.when(persistentUserService.get(any[UserId])).thenReturn(Future.successful(Some(returnedOrganisation)))
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(true))

      val futureOrganisation = organisationService.update(
        UserId("AAA-BBB-CCC"),
        OrganisationUpdateData(name = None, email = Some("any@mail.com"), avatar = None)
      )

      RecoverMethods.recoverToSucceededIf[EmailAlreadyRegisteredException](futureOrganisation)
    }

    scenario("Fail update") {
      Mockito.when(persistentUserService.get(any[UserId])).thenReturn(Future.successful(Some(returnedOrganisation)))
      Mockito.when(persistentUserService.modify(any[User])).thenReturn(Future.successful(Left(UpdateFailed())))

      val futureOrganisation = organisationService.update(
        UserId("AAA-BBB-CCC"),
        OrganisationUpdateData(name = None, email = None, avatar = None)
      )

      whenReady(futureOrganisation, Timeout(2.seconds)) { organisationId =>
        organisationId.isDefined shouldBe false
      }
    }

    scenario("Organisation not found") {
      Mockito.when(persistentUserService.get(any[UserId])).thenReturn(Future.successful(None))

      val futureOrganisation = organisationService.update(
        UserId("AAA-BBB-CCC"),
        OrganisationUpdateData(name = None, email = None, avatar = None)
      )

      whenReady(futureOrganisation, Timeout(2.seconds)) { organisationId =>
        organisationId.isDefined shouldBe false
      }
    }
  }

  feature("Get organisations") {
    scenario("get organisations") {
      Mockito
        .when(persistentUserService.findAllOrganisations())
        .thenReturn(Future.successful(Seq(returnedOrganisation, returnedOrganisation2)))

      whenReady(organisationService.getOrganisations, Timeout(2.seconds)) { organisationList =>
        organisationList shouldBe a[Seq[_]]
        organisationList.size shouldBe 2
        organisationList.head.email shouldBe "any@mail.com"
      }
    }
  }
}
