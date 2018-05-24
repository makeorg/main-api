package org.make.api.tag

import java.util.Date

import akka.http.scaladsl.model.headers.{Accept, Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.core.auth.UserRights
import org.make.core.operation.OperationId
import org.make.core.reference.ThemeId
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{eq => matches}
import org.mockito.Mockito._
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class ModerationTagApiTest extends MakeApiTestBase with ModerationTagApi with TagServiceComponent {

  override val tagService: TagService = mock[TagService]

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

  val validTagText: String = "tag"
  val helloWorldTagText: String = "hello world"
  val helloWorldTagSlug: String = "hello-world"
  val fakeTagText: String = "fake-tag"

  def newTag(label: String, tagId: TagId = idGenerator.nextTagId()): Tag = Tag(
    tagId = tagId,
    label = label,
    display = TagDisplay.Inherit,
    weight = 0f,
    tagTypeId = TagTypeId("11111111-1111-1111-1111-11111111111"),
    operationId = None,
    themeId = None,
    country = "FR",
    language = "fr"
  )

  val validTag: Tag = newTag(validTagText, TagId("valid-tag"))
  val helloWorldTag: Tag = newTag(helloWorldTagText, TagId(helloWorldTagSlug))
  val fakeTag: Tag = newTag(fakeTagText)
  val tag1: Tag = newTag("tag1", TagId("tag1"))
  val tag2: Tag = newTag("tag2", TagId("tag2"))

  when(
    tagService.createTag(
      ArgumentMatchers.eq(validTagText),
      ArgumentMatchers.any[TagTypeId],
      ArgumentMatchers.any[Option[OperationId]],
      ArgumentMatchers.any[Option[ThemeId]],
      ArgumentMatchers.any[String],
      ArgumentMatchers.any[String],
      ArgumentMatchers.any[TagDisplay],
      ArgumentMatchers.any[Float]
    )
  ).thenReturn(Future.successful(validTag))
  when(tagService.getTag(ArgumentMatchers.eq(TagId(fakeTagText))))
    .thenReturn(Future.successful(None))
  when(tagService.getTag(ArgumentMatchers.eq(TagId("valid-tag"))))
    .thenReturn(Future.successful(Some(validTag)))
  when(tagService.getTag(ArgumentMatchers.eq(TagId(fakeTagText))))
    .thenReturn(Future.successful(None))
  when(tagService.getTag(ArgumentMatchers.eq(TagId(helloWorldTagSlug))))
    .thenReturn(Future.successful(Some(helloWorldTag)))
  when(tagService.findAll())
    .thenReturn(Future.successful(Seq(tag1, tag2)))

  val routes: Route = sealRoute(moderationTagRoutes)

  feature("create a tag") {
    scenario("unauthenticated") {
      Given("an un authenticated user")
      When("the user wants to create a tag")
      Then("he should get an unauthorized (401) return code")
      Post("/moderation/tags").withEntity(
        HttpEntity(ContentTypes.`application/json`, s"""{"label": "$validTagText"}""")
      ) ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated citizen") {
      Given("an authenticated user with the citizen role")
      When("the user wants to create a tag")
      Then("he should get an forbidden (403) return code")

      Post("/moderation/tags")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"label": "$validTagText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated moderator") {
      Given("an authenticated user with the moderator role")
      When("the user wants to create a tag")
      Then("the tag should be saved if valid")
      val tagRequest =
        s"""{
           |"label": "$validTagText",
           |"tagTypeId": "1234-1234-1234-1234",
           |"operationId": "1234-1234-1234-1234",
           |"themeId": "1234-1234-1234-1234",
           |"country": "FR",
           |"language": "fr",
           |"display": "INHERIT"
       }""".stripMargin

      Post("/moderation/tags")
        .withEntity(HttpEntity(ContentTypes.`application/json`, tagRequest))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }
  }

  feature("get a tag") {

    scenario("tag not exist") {
      Given("an anonymous user")
      When(s"she wants to get a tag from id '$fakeTagText'")
      Then("she should get a not authorized response")

      Get(s"/moderation/tags/$fakeTagText") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Given("a citizen user")
      When(s"she wants to get a tag from id '$fakeTagText'")
      Then("she should get a forbidden response")

      Get(s"/moderation/tags/$fakeTagText")
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Given(s"a moderator user and a tag id '$fakeTagText' that not exist")
      When(s"she wants to get a tag from id '$fakeTagText'")
      Then("she should get a not found response")

      Get(s"/moderation/tags/$fakeTagText")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("valid tag") {
      Given(s"a registered tag with a label '$helloWorldTagText' and an id '$helloWorldTagSlug'")
      When(s"I get a tag from id '$helloWorldTagSlug'")
      Then("I should get an ok response")
      And(s"I should get a tag with a label '$helloWorldTagText' and an id '$helloWorldTagSlug")

      Get("/moderation/tags/hello-world")
        .withHeaders(Accept(MediaTypes.`application/json`))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val tag: TagResponse = entityAs[TagResponse]
        tag.label should be(helloWorldTagText)
        tag.id.value should be(helloWorldTagSlug)
      }
    }

    scenario("list tag") {
      Given("an anonymous user")
      Given("when she wants to get the list tag")
      Then("she should get a not authorized response")

      Get("/moderation/tags?_start=0&_end=10&_sort=label&_order=ASC") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Given("a citizen user")
      Given("when she wants to get the list tag")
      Then("she should get a forbidden response")

      Get("/moderation/tags?_start=0&_end=10&_sort=label&_order=ASC")
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Given("some registered tags")
      When("I get list tag")
      Then("I get a list of all tags")

      Get("/moderation/tags?_start=0&_end=10&_sort=label&_order=ASC")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        header("x-total-count").map(_.value) should be(Some("2"))
        val tags: Seq[Tag] = entityAs[Seq[Tag]]
        tags.size should be(2)
        tags(1).tagId.value should be("tag2")
        tags.head.tagId.value should be("tag1")
      }
    }
  }
}
