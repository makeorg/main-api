package org.make.api.technical.businessconfig

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.Date

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import io.circe.generic.auto._
import org.make.api.MakeApiTestUtils
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical.auth.{MakeDataHandler, MakeDataHandlerComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.api.theme.{ThemeService, ThemeServiceComponent}
import org.make.core.SlugHelper
import org.make.core.reference._
import org.make.core.user.Role.{RoleCitizen, RoleModerator}
import org.make.core.user.{User, UserId}
import org.mockito.ArgumentMatchers.{eq => matches}
import org.mockito.Mockito._

import scala.concurrent.Future
import scalaoauth2.provider.{AccessToken, AuthInfo}

class BusinessConfigApiTest
    extends MakeApiTestUtils
    with BusinessConfigApi
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with ThemeServiceComponent
    with MakeSettingsComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val themeService: ThemeService = mock[ThemeService]
  override val makeSettings: MakeSettings = mock[MakeSettings]
  private val sessionCookieConfiguration = mock[makeSettings.SessionCookie.type]
  private val oauthConfiguration = mock[makeSettings.Oauth.type]

  when(sessionCookieConfiguration.name).thenReturn("cookie-session")
  when(sessionCookieConfiguration.isSecure).thenReturn(false)
  when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  when(makeSettings.Oauth).thenReturn(oauthConfiguration)
  when(makeSettings.frontUrl).thenReturn("http://make.org")
  when(idGenerator.nextId()).thenReturn("next-id")

  val validCitizenAccessToken = "my-valid-citizen-access-token"
  val validModeratorAccessToken = "my-valid-moderator-access-token"

  val tokenCreationDate = new Date()
  private val citizenAccessToken =
    AccessToken(validCitizenAccessToken, None, Some("user"), Some(1234567890L), tokenCreationDate)
  private val moderatorAccessToken =
    AccessToken(validModeratorAccessToken, None, Some("user"), Some(1234567890L), tokenCreationDate)

  when(oauth2DataHandler.findAccessToken(validCitizenAccessToken))
    .thenReturn(Future.successful(Some(citizenAccessToken)))
  when(oauth2DataHandler.findAccessToken(validModeratorAccessToken))
    .thenReturn(Future.successful(Some(moderatorAccessToken)))

  private val citizenUser = User(
    userId = UserId("my-citizen-user-id"),
    email = "john.snow@night-watch.com",
    firstName = Some("John"),
    lastName = Some("Snow"),
    lastIp = None,
    hashedPassword = None,
    enabled = true,
    verified = true,
    lastConnection = ZonedDateTime.now(ZoneOffset.UTC),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleCitizen),
    profile = None,
    createdAt = None,
    updatedAt = None
  )
  private val moderatorUser = User(
    userId = UserId("my-moderator-user-id"),
    email = "daenerys.targaryen@dragon-queen.com",
    firstName = Some("Daenerys"),
    lastName = Some("Targaryen"),
    lastIp = None,
    hashedPassword = None,
    enabled = true,
    verified = true,
    lastConnection = ZonedDateTime.now(ZoneOffset.UTC),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleModerator),
    profile = None,
    createdAt = None,
    updatedAt = None
  )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(citizenAccessToken)))
    .thenReturn(Future.successful(Some(AuthInfo(citizenUser, None, Some("citizen"), None))))

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(Future.successful(Some(AuthInfo(moderatorUser, None, Some("moderator"), None))))

  val winterIsComingTags: Seq[Tag] = Seq(Tag("Stark"), Tag("Targaryen"), Tag("Lannister"))
  val winterIsHereTags: Seq[Tag] = Seq(Tag("White walker"))
  val themesList = Seq(
    Theme(
      themeId = ThemeId("winterIsComingId"),
      translations =
        Seq(ThemeTranslation(slug = SlugHelper("winter-is-coming"), title = "Winter is coming", language = "dk")),
      actionsCount = 7,
      proposalsCount = 42,
      country = "WE",
      color = "#00FFFF",
      gradient = Some(GradientColor("#0FF", "#0F0")),
      tags = winterIsComingTags
    ),
    Theme(
      themeId = ThemeId("winterIsHere"),
      translations =
        Seq(ThemeTranslation(slug = SlugHelper("winter-is-here"), title = "Winter is here", language = "dk")),
      actionsCount = 0,
      proposalsCount = 1000,
      country = "WE",
      color = "#FFFFdd",
      gradient = Some(GradientColor("#FFC", "#FFF")),
      tags = winterIsHereTags
    )
  )

  when(themeService.findAll())
    .thenReturn(Future.successful(themesList))

  val routes: Route = sealRoute(businessConfigRoutes)

  feature("Backoffice's business config") {
    scenario("unauthenticated") {
      Given("an un authenticated user")
      When("the user wants to get the backoffice's business config")
      Then("he should get an unauthorized (401) return code")
      Get("/business_config_back") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated citizen") {
      Given("an authenticated user with the citizen role")
      When("the user wants to get the backoffice's business config")
      Then("he should get an forbidden (403) return code")

      Get("/business_config_back")
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated moderator") {
      Given("an authenticated user with the moderator role")
      When("the user wants to get the backoffice's business config")
      Then("the backoffice's business config is returned")

      Get("/business_config_back")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val businessConfig: BusinessConfigBack = entityAs[BusinessConfigBack]
        businessConfig.themes.forall(themesList.contains) should be(true)
      }
    }
  }

  feature("Production's business config") {
    scenario("unauthenticated") {
      Given("an un authenticated user")
      When("the user wants to get the backoffice's business config")
      Then("the production's business config is returned")
      Get("/business_config_prod") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val businessConfig: BusinessConfigFront = entityAs[BusinessConfigFront]
        businessConfig.themes.forall(themesList.contains) should be(true)
      }
    }

  }
}
