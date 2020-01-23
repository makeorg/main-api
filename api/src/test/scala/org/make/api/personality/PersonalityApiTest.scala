package org.make.api.personality

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.idea.topIdeaComments.{TopIdeaCommentService, TopIdeaCommentServiceComponent}
import org.make.api.idea.{TopIdeaService, TopIdeaServiceComponent}
import org.make.api.user.{UserResponse, UserService, UserServiceComponent}
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.core.RequestContext
import org.make.core.idea._
import org.make.core.profile.Profile
import org.make.core.proposal.{QualificationKey, VoteKey}
import org.make.core.question.QuestionId
import org.make.core.user.{User, UserId, UserType}
import org.mockito.Mockito.when
import org.mockito.{ArgumentMatchers, Mockito}

import scala.concurrent.Future

class PersonalityApiTest
    extends MakeApiTestBase
    with DefaultPersonalityApiComponent
    with UserServiceComponent
    with TopIdeaCommentServiceComponent
    with TopIdeaServiceComponent {

  override val userService: UserService = mock[UserService]
  override val topIdeaCommentService: TopIdeaCommentService = mock[TopIdeaCommentService]
  override val topIdeaService: TopIdeaService = mock[TopIdeaService]

  val routes: Route = personalityApi.routes

  val tokenPersonalityCitizen = "personality"
  val returnedPersonality: User = TestUtils.user(
    id = UserId("personality-id"),
    email = "personality@make.org",
    firstName = Some("my-personality"),
    profile = Profile.parseProfile(politicalParty = Some("political-party")),
    enabled = true,
    emailVerified = true,
    userType = UserType.UserTypePersonality
  )

  override val customUserByToken = Map(tokenPersonalityCitizen -> returnedPersonality)

  Mockito
    .when(userService.getPersonality(ArgumentMatchers.eq(returnedPersonality.userId)))
    .thenReturn(Future.successful(Some(returnedPersonality)))

  Mockito
    .when(userService.getPersonality(ArgumentMatchers.eq(UserId("non-existant"))))
    .thenReturn(Future.successful(None))

  Mockito
    .when(userService.update(ArgumentMatchers.any[User], ArgumentMatchers.any[RequestContext]))
    .thenAnswer(invocation => Future.successful(invocation.getArgument[User](0)))

  feature("get personality") {
    scenario("get existing personality") {
      Get("/personalities/personality-id") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val personality: UserResponse = entityAs[UserResponse]
        personality.userId should be(UserId("personality-id"))
      }
    }

    scenario("get non existing organisation") {
      Get("/personalities/non-existant") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  feature("get personality profile") {
    scenario("get existing personality") {
      Get("/personalities/personality-id/profile") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val personality = entityAs[PersonalityProfileResponse]
        personality.firstName should contain("my-personality")
        personality.politicalParty should contain("political-party")
      }
    }

    scenario("get non existing organisation") {
      Get("/personalities/non-existant/profile") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  feature("modify profile") {
    val entity = """
                |{
                |  "firstName": "Morteau",
                |  "lastName": "Chipo",
                |  "avatarUrl": "https://la-saucisse-masquee.org/avatar",
                |  "description": "Des saucisses avec des masques",
                |  "optInNewsletter": true,
                |  "website": "https://les-saucisses-masquees.org",
                |  "politicalParty": "Les saucisses masquées"
                |}
                """.stripMargin

    scenario("unauthentificated modification") {
      Put(s"/personalities/${returnedPersonality.userId.value}/profile")
        .withEntity(HttpEntity(ContentTypes.`application/json`, entity)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("wrong user") {
      Put(s"/personalities/${returnedPersonality.userId.value}/profile")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, entity)) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("correct user") {
      Put(s"/personalities/${returnedPersonality.userId.value}/profile")
        .withHeaders(Authorization(OAuth2BearerToken(tokenPersonalityCitizen)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, entity)) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val response = entityAs[PersonalityProfileResponse]
        response.firstName should contain("Morteau")
        response.lastName should contain("Chipo")
        response.avatarUrl should contain("https://la-saucisse-masquee.org/avatar")
        response.description should contain("Des saucisses avec des masques")
        response.optInNewsletter should contain(true)
        response.website should contain("https://les-saucisses-masquees.org")
        response.politicalParty should contain("Les saucisses masquées")
      }
    }
  }

  feature("create top idea comment for personality") {
    val personalityId: UserId = defaultCitizenUser.userId

    scenario("access refused for other user than self") {
      Post(s"/personalities/some-user-other-than-self/comments") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Post(s"/personalities/some-user-other-than-self/comments")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Post(s"/personalities/some-user-other-than-self/comments")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Post(s"/personalities/some-user-other-than-self/comments")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }
    }

    scenario("access granted but not found if not personality") {
      when(userService.getPersonality(ArgumentMatchers.eq(personalityId)))
        .thenReturn(Future.successful(None))

      Post(s"/personalities/${personalityId.value}/comments")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~>
        routes ~>
        check {
          status should be(StatusCodes.NotFound)
        }
    }

    scenario("authorized and personality but top idea does not exist") {
      when(userService.getPersonality(ArgumentMatchers.eq(personalityId)))
        .thenReturn(Future.successful(Some(TestUtils.user(personalityId, userType = UserType.UserTypePersonality))))

      when(topIdeaService.getById(ArgumentMatchers.eq(TopIdeaId("fake-top-idea-id"))))
        .thenReturn(Future.successful(None))

      val entity =
        """{
          | "topIdeaId": "fake-top-idea-id",
          | "comment1": "some comment",
          | "comment2": "some other comment",
          | "comment3": null,
          | "vote": "agree",
          | "qualification": "likeIt"
          |}""".stripMargin

      Post(s"/personalities/${personalityId.value}/comments")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen)))
        .withEntity(ContentTypes.`application/json`, entity) ~>
        routes ~>
        check {
          status should be(StatusCodes.BadRequest)
        }
    }

    scenario("successful create") {
      when(userService.getPersonality(ArgumentMatchers.eq(personalityId)))
        .thenReturn(Future.successful(Some(TestUtils.user(personalityId, userType = UserType.UserTypePersonality))))

      when(topIdeaService.getById(ArgumentMatchers.eq(TopIdeaId("top-idea-id"))))
        .thenReturn(
          Future.successful(
            Some(
              TopIdea(
                TopIdeaId("top-idea-id"),
                IdeaId("idea-id"),
                QuestionId("question-id"),
                "name",
                TopIdeaScores(0f, 0f, 0f),
                0f
              )
            )
          )
        )

      when(
        topIdeaCommentService.create(
          ArgumentMatchers.eq(TopIdeaId("top-idea-id")),
          ArgumentMatchers.eq(personalityId),
          ArgumentMatchers.eq(Some("some comment")),
          ArgumentMatchers.eq(Some("some other comment")),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(Some(VoteKey.Agree)),
          ArgumentMatchers.eq(Some(QualificationKey.LikeIt))
        )
      ).thenReturn(
        Future.successful(
          TopIdeaComment(
            topIdeaCommentId = TopIdeaCommentId("top-idea-comment-id"),
            TopIdeaId("top-idea-id"),
            personalityId,
            Some("some comment"),
            Some("some other comment"),
            None,
            Some(VoteKey.Agree),
            Some(QualificationKey.LikeIt)
          )
        )
      )

      val entity =
        """{
          | "topIdeaId": "top-idea-id",
          | "comment1": "some comment",
          | "comment2": "some other comment",
          | "comment3": null,
          | "vote": "agree",
          | "qualification": "likeIt"
          |}""".stripMargin

      Post(s"/personalities/${personalityId.value}/comments")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen)))
        .withEntity(ContentTypes.`application/json`, entity) ~>
        routes ~>
        check {
          status should be(StatusCodes.Created)
        }
    }
  }

}
