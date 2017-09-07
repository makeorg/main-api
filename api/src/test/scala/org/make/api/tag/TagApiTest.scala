package org.make.api.tag

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import io.circe.generic.auto._
import org.make.api.MakeApiTestUtils
import org.make.api.technical.auth.{MakeDataHandler, MakeDataHandlerComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.core.reference.Tag
import org.make.core.user.Role.{RoleCitizen, RoleModerator}
import org.make.core.user.{User, UserId}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{eq => matches}
import org.mockito.Mockito._

import scala.concurrent.Future
import scalaoauth2.provider.{AccessToken, AuthInfo}

class TagApiTest
    extends MakeApiTestUtils
    with TagApi
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with TagServiceComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val tagService: TagService = mock[TagService]

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

  val validTagText: String = "tag"
  val specificValidTagText: String = "taxes/fiscalité"
  val specificValidTagSlug: String = "taxes-fiscalite"

  when(tagService.createTag(ArgumentMatchers.eq(validTagText)))
    .thenReturn(Future.successful(Tag(validTagText)))
  when(tagService.createTag(ArgumentMatchers.eq(specificValidTagText)))
    .thenReturn(Future.successful(Tag(specificValidTagText)))

  val routes: Route = sealRoute(tagRoutes)

  feature("create a tag") {
    scenario("unauthenticated") {
      Given("an un authenticated user")
      When("the user wants to create a tag")
      Then("he should get an unauthorized (401) return code")
      Post("/tag").withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"label": "$validTagText"}""")) ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated citizen") {
      Given("an authenticated user with the citizen role")
      When("the user wants to create a tag")
      Then("he should get an forbidden (403) return code")

      Post("/tag")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"label": "$validTagText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated moderator") {
      Given("an authenticated user with the moderator role")
      When("the user wants to create a tag")
      Then("the tag should be saved if valid")

      Post("/tag")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"label": "$validTagText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }

    scenario("specific tag") {
      Given("an authenticated user with the moderator role")
      When(s"""the user wants to create a specific tag with value "$specificValidTagText"""")
      Then(s"the created tag's slug should be $specificValidTagSlug")

      Post("/tag")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"label": "$specificValidTagText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
        val tag: Tag = entityAs[Tag]
        tag.tagId.value should be(specificValidTagSlug)
      }
    }
  }
}
