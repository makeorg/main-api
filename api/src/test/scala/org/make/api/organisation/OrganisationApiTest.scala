package org.make.api.organisation

import java.time.ZonedDateTime

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestUtils
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandler, MakeDataHandlerComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.api.user.{OrganisationService, OrganisationServiceComponent, UserResponse}
import org.make.core.DateHelper
import org.make.core.user.Role.RoleActor
import org.make.core.user.{User, UserId}
import org.mockito.Mockito

import scala.concurrent.Future
import scala.concurrent.duration.Duration

class OrganisationApiTest
    extends MakeApiTestUtils
    with OrganisationApi
    with IdGeneratorComponent
    with MakeSettingsComponent
    with MakeAuthentication
    with MakeDataHandlerComponent
    with OrganisationServiceComponent {

  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val organisationService: OrganisationService = mock[OrganisationService]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]

  private val sessionCookieConfiguration = mock[makeSettings.SessionCookie.type]
  private val oauthConfiguration = mock[makeSettings.Oauth.type]

  Mockito.when(sessionCookieConfiguration.name).thenReturn("cookie-session")
  Mockito.when(sessionCookieConfiguration.isSecure).thenReturn(false)
  Mockito.when(sessionCookieConfiguration.lifetime).thenReturn(Duration("20 minutes"))
  Mockito.when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  Mockito.when(makeSettings.Oauth).thenReturn(oauthConfiguration)
  Mockito.when(idGenerator.nextId()).thenReturn("next-id")

  val routes: Route = sealRoute(organisationRoutes)

  val now: ZonedDateTime = DateHelper.now()

  Mockito
    .when(organisationService.getOrganisation(UserId("make-org")))
    .thenReturn(
      Future.successful(
        Some(
          User(
            userId = UserId("make-org"),
            email = "make@make.org",
            firstName = None,
            lastName = None,
            lastIp = None,
            hashedPassword = None,
            enabled = true,
            emailVerified = true,
            isOrganisation = true,
            lastConnection = now,
            verificationToken = None,
            verificationTokenExpiresAt = None,
            resetToken = None,
            resetTokenExpiresAt = None,
            roles = Seq(RoleActor),
            country = "FR",
            language = "fr",
            profile = None,
            createdAt = None,
            updatedAt = None,
            lastMailingError = None
          )
        )
      )
    )
  Mockito
    .when(organisationService.getOrganisation(UserId("not-an-organisation")))
    .thenReturn(Future.successful(None))

  feature("get organisation") {
    scenario("get existing organisation") {
      Get("/organisations/make-org") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val organisation: UserResponse = entityAs[UserResponse]
        organisation.userId should be(UserId("make-org"))
      }
    }

    scenario("get not existing organisation") {
      Get("/organisations/not-an-organisation") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }

}
