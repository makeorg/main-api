package org.make.api.organisation

import org.make.api.MakeUnitTest
import org.make.api.technical.{EventBusService, EventBusServiceComponent, IdGenerator, IdGeneratorComponent}
import org.make.api.user._
import org.make.api.userhistory.UserEvent.OrganisationRegisteredEvent
import org.make.core.user.Role.RoleOrganisation
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify}
import org.mockito.{ArgumentMatcher, ArgumentMatchers, Mockito}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
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

  feature("register organisation") {
    scenario("successfully register an organisation") {
      Mockito.reset(eventBusService)
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(false))

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
        profile = None
      )

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
  }
}
