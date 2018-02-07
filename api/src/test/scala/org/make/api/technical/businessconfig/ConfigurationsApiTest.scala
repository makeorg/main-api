package org.make.api.technical.businessconfig

import java.util.Date

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestUtils
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical.auth.{MakeDataHandler, MakeDataHandlerComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.api.theme.{ThemeService, ThemeServiceComponent}
import org.make.core.SlugHelper
import org.make.core.auth.UserRights
import org.make.core.reference._
import org.make.core.user.Role.{RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers.{eq => matches}
import org.mockito.Mockito._

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scalaoauth2.provider.{AccessToken, AuthInfo}

class ConfigurationsApiTest
    extends MakeApiTestUtils
    with ConfigurationsApi
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
  when(sessionCookieConfiguration.lifetime).thenReturn(Duration("20 minutes"))
  when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  when(makeSettings.Oauth).thenReturn(oauthConfiguration)
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

  private val citizenUserRights = UserRights(UserId("my-citizen-user-id"), Seq(RoleCitizen))
  private val moderatorUserRights = UserRights(UserId("my-moderator-user-id"), Seq(RoleModerator))

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(citizenAccessToken)))
    .thenReturn(Future.successful(Some(AuthInfo(citizenUserRights, None, Some("citizen"), None))))

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(Future.successful(Some(AuthInfo(moderatorUserRights, None, Some("moderator"), None))))

  val winterIsComingTags: Seq[Tag] = Seq(Tag("Stark"), Tag("Targaryen"), Tag("Lannister"))
  val winterIsHereTags: Seq[Tag] = Seq(Tag("White walker"))
  val themesList = Seq(
    Theme(
      themeId = ThemeId("winterIsComingId"),
      translations =
        Seq(ThemeTranslation(slug = SlugHelper("winter-is-coming"), title = "Winter is coming", language = "dk")),
      actionsCount = 7,
      proposalsCount = 42,
      votesCount = 0,
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
      votesCount = 0,
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
      Get("/configurations/backoffice") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated citizen") {
      Given("an authenticated user with the citizen role")
      When("the user wants to get the backoffice's business config")
      Then("he should get an forbidden (403) return code")

      Get("/configurations/backoffice")
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated moderator") {
      Given("an authenticated user with the moderator role")
      When("the user wants to get the backoffice's business config")
      Then("the backoffice's business config is returned")

      Get("/configurations/backoffice")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val businessConfig: BackofficeConfiguration = entityAs[BackofficeConfiguration]
        businessConfig.themes.forall(themesList.contains) should be(true)
        businessConfig.reasonsForRefusal.size should be(8)
      }
    }
  }

  feature("front business config") {
    scenario("unauthenticated") {
      Given("an un authenticated user")
      When("the user wants to get the front business config")
      Then("the front business config is returned")
      Get("/configurations/front") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val businessConfig: FrontConfiguration = entityAs[FrontConfiguration]
        businessConfig.themes.forall(themesList.contains) should be(true)
      }
    }

  }
}
