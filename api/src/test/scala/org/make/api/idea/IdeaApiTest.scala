package org.make.api.idea

import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestUtils
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical.auth.{MakeDataHandler, MakeDataHandlerComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.core.auth.UserRights
import org.make.core.reference.{Idea, IdeaId}
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{eq => matches}
import org.mockito.Mockito._

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scalaoauth2.provider.{AccessToken, AuthInfo}

class IdeaApiTest
    extends MakeApiTestUtils
    with IdeaApi
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with IdeaServiceComponent
    with MakeSettingsComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val ideaService: IdeaService = mock[IdeaService]
  override val makeSettings: MakeSettings = mock[MakeSettings]

  private val sessionCookieConfiguration = mock[makeSettings.SessionCookie.type]
  private val oauthConfiguration = mock[makeSettings.Oauth.type]

  when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  when(makeSettings.Oauth).thenReturn(oauthConfiguration)
  when(sessionCookieConfiguration.name).thenReturn("cookie-session")
  when(sessionCookieConfiguration.isSecure).thenReturn(false)
  when(sessionCookieConfiguration.lifetime).thenReturn(Duration("20 minutes"))
  when(idGenerator.nextId()).thenReturn("next-id")

  val validCitizenAccessToken = "my-valid-citizen-access-token"
  val validModeratorAccessToken = "my-valid-moderator-access-token"
  val validAdminAccessToken = "my-valid-admin-access-token"

  val tokenCreationDate = new Date()
  private val citizenAccessToken =
    AccessToken(validCitizenAccessToken, None, Some("user"), Some(1234567890L), tokenCreationDate)
  private val moderatorAccessToken =
    AccessToken(validModeratorAccessToken, None, Some("user"), Some(1234567890L), tokenCreationDate)
  private val adminAccessToken =
    AccessToken(validAdminAccessToken, None, Some("user"), Some(1234567890L), tokenCreationDate)

  when(oauth2DataHandler.findAccessToken(validCitizenAccessToken))
    .thenReturn(Future.successful(Some(citizenAccessToken)))
  when(oauth2DataHandler.findAccessToken(validModeratorAccessToken))
    .thenReturn(Future.successful(Some(moderatorAccessToken)))
  when(oauth2DataHandler.findAccessToken(validAdminAccessToken))
    .thenReturn(Future.successful(Some(adminAccessToken)))

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(citizenAccessToken)))
    .thenReturn(
      Future.successful(
        Some(AuthInfo(UserRights(UserId("my-citizen-user-id"), Seq(RoleCitizen)), None, Some("citizen"), None))
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(
      Future.successful(
        Some(AuthInfo(UserRights(UserId("my-moderator-user-id"), Seq(RoleModerator)), None, Some("moderator"), None))
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future
        .successful(Some(AuthInfo(UserRights(UserId("my-admin-user-id"), Seq(RoleAdmin)), None, Some("admin"), None)))
    )

  val fooIdeaText: String = "fooIdea"
  val fooIdeaId: IdeaId = IdeaId("fooIdeaId")
  val fooIdea: Idea = Idea(fooIdeaId, fooIdeaText)
  val barIdeaText: String = "barIdea"
  val barIdeaId: IdeaId = IdeaId("barIdeaId")
  val barIdea: Idea = Idea(barIdeaId, barIdeaText)

  when(
    ideaService.insert(
      ArgumentMatchers.eq(fooIdeaText),
      ArgumentMatchers.any[Option[String]],
      ArgumentMatchers.any[Option[String]],
      ArgumentMatchers.any[Option[String]],
      ArgumentMatchers.any[Option[String]]
    )
  ).thenReturn(Future.successful(fooIdea))

  when(ideaService.update(ArgumentMatchers.eq(fooIdeaId), ArgumentMatchers.any[String]))
    .thenReturn(Future.successful(1))

  when(ideaService.fetchOneByName(ArgumentMatchers.any[String])).thenReturn(Future.successful(None))

  val routes: Route = sealRoute(ideaRoutes)

  feature("create an idea") {
    scenario("unauthenticated") {
      Given("an un authenticated user")
      When("the user wants to create an idea")
      Then("he should get an unauthorized (401) return code")
      Post("/ideas").withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$fooIdeaText"}""")) ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated citizen") {
      Given("an authenticated user with the citizen role")
      When("the user wants to create an idea")
      Then("he should get an forbidden (403) return code")

      Post("/ideas")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$fooIdeaText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated moderator") {
      Given("an authenticated user with the moderator role")
      When("the user wants to create an idea")
      Then("the idea should be saved if valid")

      Post("/ideas")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$fooIdeaText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated admin") {
      Given("an authenticated user with the admin role")
      When("the user wants to create an idea")
      Then("the idea should be saved if valid")

      Post("/ideas")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$fooIdeaText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
        val idea: Idea = entityAs[Idea]
        idea.ideaId.value should be(fooIdeaId.value)
      }
    }

    scenario("bad data in body") {
      Given("an authenticated user with the admin role")
      When("the user wants to create an idea")
      Then("the idea should be saved if valid")

      Post("/ideas")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"bibi": "$fooIdeaText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  feature("update an idea") {
    scenario("unauthenticated") {
      Given("an un authenticated user")
      When("the user wants to update an idea")
      Then("he should get an unauthorized (401) return code")
      Put(s"/ideas/${fooIdeaId.value}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$barIdeaText"}""")) ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated citizen") {
      Given("an authenticated user with the citizen role")
      When("the user wants to update an idea")
      Then("he should get an forbidden (403) return code")

      Put(s"/ideas/${fooIdeaId.value}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$barIdeaText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated moderator") {
      Given("an authenticated user with the moderator role")
      When("the user wants to update an idea")
      Then("the idea should be saved if valid")

      Put(s"/ideas/${fooIdeaId.value}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$barIdeaText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated admin") {
      Given("an authenticated user with the admin role")
      When("the user wants to update an idea")
      Then("the idea should be saved if valid")

      Put(s"/ideas/${fooIdeaId.value}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$barIdeaText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val ideaId: IdeaId = entityAs[IdeaId]
        ideaId should be(fooIdeaId)
      }
    }
  }
}
