package org.make.api.tag

import java.util.Date

import akka.http.scaladsl.model.headers.{Accept, Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.core.RequestContext
import org.make.core.auth.UserRights
import org.make.core.tag.{Tag, TagId}
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
  val existingValidTagSlug: String = "existing-tag"
  val existingValidTagText: String = "existing tag"
  val specificValidTagText: String = "taxes/fiscalité"
  val specificValidTagSlug: String = "taxes-fiscalite"
  val helloWorldTagText: String = "hello world"
  val helloWorldTagSlug: String = "hello-world"
  val fakeTag: String = "fake-tag"
  val newTagNameText: String = "new tag name"
  val newTagNameSlug: String = "new-tag-name"

  when(tagService.createTag(ArgumentMatchers.eq(validTagText)))
    .thenReturn(Future.successful(Tag(validTagText)))
  when(tagService.createTag(ArgumentMatchers.eq(specificValidTagText)))
    .thenReturn(Future.successful(Tag(specificValidTagText)))
  when(tagService.getTag(ArgumentMatchers.eq(TagId(fakeTag))))
    .thenReturn(Future.successful(None))
  when(tagService.getTag(ArgumentMatchers.eq(TagId(newTagNameSlug))))
    .thenReturn(Future.successful(None))
  when(tagService.getTag(ArgumentMatchers.eq(TagId(helloWorldTagSlug))))
    .thenReturn(Future.successful(Some(Tag(helloWorldTagText))))
  when(tagService.getTag(ArgumentMatchers.eq(TagId(existingValidTagSlug))))
    .thenReturn(Future.successful(Some(Tag(existingValidTagText))))
  when(tagService.findAllEnabled())
    .thenReturn(Future.successful(Seq(Tag("tag1"), Tag("tag2"))))

  when(
    tagService.updateTag(
      slug = ArgumentMatchers.eq(TagId(existingValidTagSlug)),
      newTagLabel = ArgumentMatchers.eq(newTagNameText),
      requestContext = ArgumentMatchers.any[RequestContext],
      connectedUserId = ArgumentMatchers.any[Option[UserId]]
    )
  ).thenReturn(Future.successful(Some(Tag(newTagNameText))))

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

      Post("/moderation/tags")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"label": "$validTagText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }

    scenario("specific tag") {
      Given("an authenticated user with the moderator role")
      When(s"""the user wants to create a specific tag with value "$specificValidTagText"""")
      Then(s"the created tag's slug should be $specificValidTagSlug")

      Post("/moderation/tags")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"label": "$specificValidTagText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
        val tag: TagResponse = entityAs[TagResponse]
        tag.id.value should be(specificValidTagSlug)
      }
    }
  }

  feature("get a tag") {

    scenario("tag not exist") {
      Given("an anonymous user")
      When(s"she wants to get a tag from id '$fakeTag'")
      Then("she should get a not authorized response")

      Get(s"/moderation/tags/$fakeTag") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Given("a citizen user")
      When(s"she wants to get a tag from id '$fakeTag'")
      Then("she should get a forbidden response")

      Get(s"/moderation/tags/$fakeTag")
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Given(s"a moderator user and a tag id '$fakeTag' that not exist")
      When(s"she wants to get a tag from id '$fakeTag'")
      Then("she should get a not found response")

      Get(s"/moderation/tags/$fakeTag")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("valid tag") {
      Given(s"a registered tag with a label '$helloWorldTagText' and an id '$helloWorldTagSlug'")
      When(s"i get a tag from id '$helloWorldTagSlug'")
      Then("i should get an ok response")
      And(s"i should get a tag with a label '$helloWorldTagText' and an id '$helloWorldTagSlug")

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
      When("i get list tag")
      Then("i get a list of all tags")

      Get("/moderation/tags?_start=0&_end=10&_sort=label&_order=ASC")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        header("x-total-count").map(_.value) should be(Some("2"))
        val tags: Seq[TagResponse] = entityAs[Seq[TagResponse]]
        tags.size should be(2)
        tags(1).id.value should be("tag2")
        tags(0).id.value should be("tag1")
      }
    }
  }
}
