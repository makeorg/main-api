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
import org.make.core.user.Role.{RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{eq => matches}
import org.mockito.Mockito._
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class TagApiTest extends MakeApiTestBase with TagApi with TagServiceComponent {

  override val tagService: TagService = mock[TagService]

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

  val validTagText: String = "tag"
  val helloWorldTagText: String = "hello world"
  val helloWorldTagId: String = "hello-world"
  val fakeTag: String = "fake-tag"
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
  ).thenReturn(Future.successful(newTag(validTagText)))
  when(tagService.getTag(ArgumentMatchers.eq(TagId(fakeTag))))
    .thenReturn(Future.successful(None))
  when(tagService.getTag(ArgumentMatchers.eq(TagId(helloWorldTagId))))
    .thenReturn(Future.successful(Some(newTag(helloWorldTagText, Some(helloWorldTagId)))))
  when(tagService.findAll())
    .thenReturn(Future.successful(Seq(newTag("tag1"), newTag("tag2"))))
  when(
    tagService.updateTag(
      ArgumentMatchers.eq(TagId(helloWorldTagId)),
      ArgumentMatchers.eq(s"$validTagText UPDATED"),
      ArgumentMatchers.any[TagDisplay],
      ArgumentMatchers.any[TagTypeId],
      ArgumentMatchers.any[Float],
      ArgumentMatchers.any[Option[OperationId]],
      ArgumentMatchers.any[Option[ThemeId]],
      ArgumentMatchers.any[String],
      ArgumentMatchers.any[String]
    )
  ).thenReturn(
    Future.successful(
      Some(newTag(s"$validTagText UPDATED", Some(helloWorldTagId)).copy(tagTypeId = TagTypeId("1234-1234-1234-1234")))
    )
  )

  val routes: Route = sealRoute(tagRoutes)

  feature("create a tag") {
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

    scenario("unauthenticated") {
      Given("an un authenticated user")
      When("the user wants to create a tag")
      Then("he should get an unauthorized (401) return code")
      Post("/tags").withEntity(HttpEntity(ContentTypes.`application/json`, tagRequest)) ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated citizen") {
      Given("an authenticated user with the citizen role")
      When("the user wants to create a tag")
      Then("he should get an forbidden (403) return code")

      Post("/tags")
        .withEntity(HttpEntity(ContentTypes.`application/json`, tagRequest))
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated moderator") {
      Given("an authenticated user with the moderator role")
      When("the user wants to create a tag")
      Then("the tag should be saved if valid")

      Post("/tags")
        .withEntity(HttpEntity(ContentTypes.`application/json`, tagRequest))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }
  }

  feature("get a tag") {

    scenario("tag not exist") {
      Given(s"a tag id '$fakeTag' that not exist")
      When(s"I get a tag from id '$fakeTag'")
      Then("I should get a not found response")

      Get(s"/tags/$fakeTag") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("valid tag") {
      Given(s"a registered tag with a label '$helloWorldTagText' and an id '$helloWorldTagId'")
      When(s"I get a tag from id '$helloWorldTagId'")
      Then("I should get an ok response")
      And(s"I should get a tag with a label '$helloWorldTagText' and an id '$helloWorldTagId")

      Get("/tags/hello-world").withHeaders(Accept(MediaTypes.`application/json`)) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val tag: Tag = entityAs[Tag]
        tag.label should be(helloWorldTagText)
        tag.tagId.value should be(helloWorldTagId)
      }
    }
  }

  feature("list tags") {
    scenario("list tag") {
      Given("some registered tags")
      When("I get list tag")
      Then("I get a list of all tags")

      Get("/tags") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val tags: Seq[Tag] = entityAs[Seq[Tag]]
        tags.size should be(2)
        tags(1).tagId.value should be("tag2")
        tags.head.tagId.value should be("tag1")
      }
    }
  }

  feature("update a tag") {
    val updateTagRequest =
      s"""{
         |"label": "$validTagText UPDATED",
         |"tagTypeId": "1234-1234-1234-1234",
         |"operationId": "1234-1234-1234-1234",
         |"themeId": "1234-1234-1234-1234",
         |"country": "FR",
         |"language": "fr",
         |"display": "INHERIT",
         |"weight": "0"
       }""".stripMargin

    scenario("update tag with anonymous user") {
      Given("unauthenticated user")
      When(s"I update tag from id '$fakeTag")
      Then("I get an unauthorized response")
      Put(s"/tags/$fakeTag") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("update tag with citizen user") {
      Given("an authenticated user with role citizen")
      When(s"I update tag from id '$fakeTag")
      Then("I get a forbidden response")
      Put(s"/tags/$fakeTag").withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("update non existing tag as moderator") {
      Given("an authenticated user with role moderator")
      When("I update non existing tag")
      Then("I get a not found response")
      Put(s"/tags/$fakeTag")
        .withEntity(HttpEntity(ContentTypes.`application/json`, updateTagRequest))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("update tag with an invalid request") {
      Given("a registered tag")
      When("I update tag with an invalid body")
      Then("I get a bad request response")
      Put(s"/tags/$fakeTag")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("update tag") {
      Given("a registered tag 'hello-world'")
      When("I update tag label to 'hello-world UPDATED'")
      Then("I get an OK response")
      Put(s"/tags/$helloWorldTagId")
        .withEntity(HttpEntity(ContentTypes.`application/json`, updateTagRequest))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val tag: Tag = entityAs[Tag]
        tag.tagId.value should be(helloWorldTagId)
        tag.tagTypeId should be(TagTypeId("1234-1234-1234-1234"))
      }
    }

  }
}
