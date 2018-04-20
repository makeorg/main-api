package org.make.api.tag

import java.util.Date

import akka.http.scaladsl.model.headers.{Accept, Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.core.auth.UserRights
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.make.core.{RequestContext, ValidationError}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{eq => matches}
import org.mockito.Mockito._
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class TagApiTest extends MakeApiTestBase with TagApi with TagServiceComponent {

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
  def newTag(label: String, tagId: Option[String] = None): Tag = Tag(
    tagId = TagId(tagId.getOrElse(label)),
    label = label,
    display = TagDisplay.Inherit,
    weight = 0f,
    tagTypeId = TagTypeId("11111111-1111-1111-1111-11111111111"),
    operationId = None,
    themeId = None,
    country = "FR",
    language = "fr"
  )

  when(tagService.createLegacyTag(ArgumentMatchers.eq(validTagText)))
    .thenReturn(Future.successful(newTag(validTagText)))
  when(tagService.createLegacyTag(ArgumentMatchers.eq(specificValidTagText)))
    .thenReturn(Future.successful(newTag(specificValidTagText, Some(specificValidTagSlug))))
  when(tagService.getTag(ArgumentMatchers.eq(TagId(fakeTag))))
    .thenReturn(Future.successful(None))
  when(tagService.getTag(ArgumentMatchers.eq(TagId(newTagNameSlug))))
    .thenReturn(Future.successful(None))
  when(tagService.getTag(ArgumentMatchers.eq(TagId(helloWorldTagSlug))))
    .thenReturn(Future.successful(Some(newTag(helloWorldTagText, Some(helloWorldTagSlug)))))
  when(tagService.getTag(ArgumentMatchers.eq(TagId(existingValidTagSlug))))
    .thenReturn(Future.successful(Some(newTag(existingValidTagText, Some(existingValidTagSlug)))))
  when(tagService.findAll())
    .thenReturn(Future.successful(Seq(newTag("tag1"), newTag("tag2"))))

  when(
    tagService.updateTag(
      slug = ArgumentMatchers.eq(TagId(existingValidTagSlug)),
      newTagLabel = ArgumentMatchers.eq(newTagNameText),
      requestContext = ArgumentMatchers.any[RequestContext],
      connectedUserId = ArgumentMatchers.any[Option[UserId]]
    )
  ).thenReturn(Future.successful(Some(newTag(newTagNameText))))

  val routes: Route = sealRoute(tagRoutes)

  feature("create a tag") {
    scenario("unauthenticated") {
      Given("an un authenticated user")
      When("the user wants to create a tag")
      Then("he should get an unauthorized (401) return code")
      Post("/tags").withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"label": "$validTagText"}""")) ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated citizen") {
      Given("an authenticated user with the citizen role")
      When("the user wants to create a tag")
      Then("he should get an forbidden (403) return code")

      Post("/tags")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"label": "$validTagText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated moderator") {
      Given("an authenticated user with the moderator role")
      When("the user wants to create a tag")
      Then("the tag should be saved if valid")

      Post("/tags")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"label": "$validTagText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }

    scenario("specific tag") {
      Given("an authenticated user with the moderator role")
      When(s"""the user wants to create a specific tag with value "$specificValidTagText"""")
      Then(s"the created tag's slug should be $specificValidTagSlug")

      Post("/tags")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"label": "$specificValidTagText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
        val tag: Tag = entityAs[Tag]
        tag.tagId.value should be(specificValidTagSlug)
      }
    }
  }

  feature("get a tag") {

    scenario("tag not exist") {
      Given(s"a tag id '$fakeTag' that not exist")
      When(s"i get a tag from id '$fakeTag'")
      Then("i should get a not found response")

      Get(s"/tags/$fakeTag") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("valid tag") {
      Given(s"a registered tag with a label '$helloWorldTagText' and an id '$helloWorldTagSlug'")
      When(s"i get a tag from id '$helloWorldTagSlug'")
      Then("i should get an ok response")
      And(s"i should get a tag with a label '$helloWorldTagText' and an id '$helloWorldTagSlug")

      Get("/tags/hello-world").withHeaders(Accept(MediaTypes.`application/json`)) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val tag: Tag = entityAs[Tag]
        tag.label should be(helloWorldTagText)
        tag.tagId.value should be(helloWorldTagSlug)
      }
    }

    scenario("list tag") {
      Given("some registered tags")
      When("i get list tag")
      Then("i get a list of all tags")

      Get("/tags") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val tags: Seq[Tag] = entityAs[Seq[Tag]]
        tags.size should be(2)
        tags(1).tagId.value should be("tag2")
        tags(0).tagId.value should be("tag1")
      }
    }

    scenario("update tag with anonymous user") {
      Given("unauthenticated user")
      When(s"i update tag from id '$fakeTag")
      Then("i get a unauthorized response")
      Put(s"/tags/$fakeTag") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("update tag with citizen user") {
      Given("an authenticated user with role citizen")
      When(s"i update tag from id '$fakeTag")
      Then("i get a forbidden response")
      Put(s"/tags/$fakeTag").withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("update tag with moderator user") {
      Given("an authenticated user with role citizen")
      When(s"i update tag from id '$fakeTag")
      Then("i get a forbidden response")
      Put(s"/tags/$fakeTag")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("update tag with non existant tag id") {
      Given("an authenticated user with role admin")
      And(s"a tag id '$fakeTag' that not exist")
      When(s"i update tag from id '$fakeTag")
      Then("i get a not found response")
      Put(s"/tags/$fakeTag")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"label": "test"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("update tag with an invalid request") {
      Given(s"a registered tag with a label '$helloWorldTagText' and an id '$helloWorldTagSlug'")
      When(s"i update tag from id '$fakeTag' with an invalid body")
      Then("i get a bad request response")
      Put(s"/tags/$fakeTag")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("update tag to already existing tag") {
      Given(s"a registered tag with a label '$helloWorldTagText' and an id '$helloWorldTagSlug'")
      And(s"a registered tag with a label '$existingValidTagText' and an id '$existingValidTagSlug'")
      When(s"i update tag label with id '$helloWorldTagSlug' to '$existingValidTagText'")
      Then("i get a bad request response")
      And("an error message 'New tag already exist. Duplicates are not allowed'")
      Put(s"/tags/$helloWorldTagSlug")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"label": "$existingValidTagText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "label")
        contentError should be(
          Some(ValidationError("label", Some("New tag already exist. Duplicates are not allowed")))
        )
      }
    }

  }
}
