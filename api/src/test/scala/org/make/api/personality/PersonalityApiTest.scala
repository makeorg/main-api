/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.personality

import java.time.ZonedDateTime

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import cats.data.NonEmptyList
import org.make.api.idea.topIdeaComments.{TopIdeaCommentService, TopIdeaCommentServiceComponent}
import org.make.api.idea.{TopIdeaService, TopIdeaServiceComponent}
import org.make.api.question.{QuestionTopIdeaWithAvatarResponse, SimpleQuestionResponse, SimpleQuestionWordingResponse}
import org.make.api.user.{UserResponse, UserService, UserServiceComponent}
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.core.RequestContext
import org.make.core.idea._
import org.make.core.personality.{Personality, PersonalityId, PersonalityRoleId}
import org.make.core.profile.Profile
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.user.{User, UserId, UserType}

import scala.concurrent.Future
import org.make.core.technical.Pagination.Start

class PersonalityApiTest
    extends MakeApiTestBase
    with DefaultPersonalityApiComponent
    with UserServiceComponent
    with TopIdeaCommentServiceComponent
    with TopIdeaServiceComponent
    with QuestionPersonalityServiceComponent {

  override val userService: UserService = mock[UserService]
  override val topIdeaCommentService: TopIdeaCommentService = mock[TopIdeaCommentService]
  override val topIdeaService: TopIdeaService = mock[TopIdeaService]
  override val questionPersonalityService: QuestionPersonalityService = mock[QuestionPersonalityService]

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

  when(userService.getPersonality(eqTo(returnedPersonality.userId)))
    .thenReturn(Future.successful(Some(returnedPersonality)))

  when(userService.getPersonality(eqTo(UserId("non-existant"))))
    .thenReturn(Future.successful(None))

  when(userService.update(any[User], any[RequestContext])).thenAnswer { user: User =>
    Future.successful(user)
  }

  Feature("get personality") {
    Scenario("get existing personality") {
      Get("/personalities/personality-id") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val personality: UserResponse = entityAs[UserResponse]
        personality.userId should be(UserId("personality-id"))
      }
    }

    Scenario("get non existing organisation") {
      Get("/personalities/non-existant") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  Feature("get personality profile") {
    Scenario("get existing personality") {
      Get("/personalities/personality-id/profile") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val personality = entityAs[PersonalityProfileResponse]
        personality.firstName should contain("my-personality")
        personality.politicalParty should contain("political-party")
      }
    }

    Scenario("get non existing organisation") {
      Get("/personalities/non-existant/profile") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  Feature("modify profile") {
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

    Scenario("unauthentificated modification") {
      Put(s"/personalities/${returnedPersonality.userId.value}/profile")
        .withEntity(HttpEntity(ContentTypes.`application/json`, entity)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("wrong user") {
      Put(s"/personalities/${returnedPersonality.userId.value}/profile")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, entity)) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("correct user") {
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

    Scenario("bad request") {

      val invalidEntity = """
                     |{
                     |  "firstName": "Morteau"
                     |}
                """.stripMargin

      Put(s"/personalities/${returnedPersonality.userId.value}/profile")
        .withHeaders(Authorization(OAuth2BearerToken(tokenPersonalityCitizen)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, invalidEntity)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  Feature("create top idea comment for personality") {
    val personalityId: UserId = defaultCitizenUser.userId

    Scenario("access refused for other user than self") {
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

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post(s"/personalities/some-user-other-than-self/comments")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~>
          routes ~>
          check {
            status should be(StatusCodes.Forbidden)
          }
      }
    }

    Scenario("access granted but not found if not personality") {
      when(userService.getPersonality(eqTo(personalityId)))
        .thenReturn(Future.successful(None))

      Post(s"/personalities/${personalityId.value}/comments")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~>
        routes ~>
        check {
          status should be(StatusCodes.NotFound)
        }
    }

    Scenario("authorized and personality but top idea does not exist") {
      when(userService.getPersonality(eqTo(personalityId)))
        .thenReturn(Future.successful(Some(TestUtils.user(personalityId, userType = UserType.UserTypePersonality))))

      when(topIdeaService.getById(eqTo(TopIdeaId("fake-top-idea-id"))))
        .thenReturn(Future.successful(None))

      val entity =
        """{
          | "topIdeaId": "fake-top-idea-id",
          | "comment1": "some comment",
          | "comment2": "some other comment",
          | "comment3": null,
          | "vote": "agree",
          | "qualification": "doable"
          |}""".stripMargin

      Post(s"/personalities/${personalityId.value}/comments")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen)))
        .withEntity(ContentTypes.`application/json`, entity) ~>
        routes ~>
        check {
          status should be(StatusCodes.BadRequest)
        }
    }

    Scenario("validation error") {
      when(userService.getPersonality(eqTo(personalityId)))
        .thenReturn(Future.successful(Some(TestUtils.user(personalityId, userType = UserType.UserTypePersonality))))

      val entity =
        """{
          | "topIdeaId": "fake-top-idea-id",
          | "comment1": "some comment",
          | "comment2": "some other comment",
          | "comment3": null,
          | "vote": "agree",
          | "qualification": "noWay"
          |}""".stripMargin

      Post(s"/personalities/${personalityId.value}/comments")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen)))
        .withEntity(ContentTypes.`application/json`, entity) ~>
        routes ~>
        check {
          status should be(StatusCodes.BadRequest)
        }
    }

    Scenario("successful create") {
      when(userService.getPersonality(eqTo(personalityId)))
        .thenReturn(Future.successful(Some(TestUtils.user(personalityId, userType = UserType.UserTypePersonality))))

      when(topIdeaService.getById(eqTo(TopIdeaId("top-idea-id"))))
        .thenReturn(
          Future.successful(
            Some(
              TopIdea(
                TopIdeaId("top-idea-id"),
                IdeaId("idea-id"),
                QuestionId("question-id"),
                "name",
                "label",
                TopIdeaScores(0f, 0f, 0f),
                0f
              )
            )
          )
        )

      when(
        topIdeaCommentService.create(
          eqTo(TopIdeaId("top-idea-id")),
          eqTo(personalityId),
          eqTo(Some("some comment")),
          eqTo(Some("some other comment")),
          eqTo(None),
          eqTo(CommentVoteKey.Agree),
          eqTo(Some(CommentQualificationKey.Doable))
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
            CommentVoteKey.Agree,
            Some(CommentQualificationKey.Doable)
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
          | "qualification": "doable"
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

  Feature("get personality opinions") {

    Scenario("personality not found") {
      Get("/personalities/non-existant/opinions") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    Scenario("personality not in question") {
      when(
        questionPersonalityService.find(
          eqTo(Start.zero),
          eqTo(None),
          eqTo(None),
          eqTo(None),
          eqTo(Some(UserId("personality-id"))),
          eqTo(None),
          eqTo(None)
        )
      ).thenReturn(Future.successful(Seq.empty))

      Get("/personalities/personality-id/opinions") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val opinions = entityAs[Seq[PersonalityOpinionResponse]]
        opinions shouldBe empty
      }
    }

    Scenario("empty list of top ideas") {
      when(
        questionPersonalityService.find(
          eqTo(Start.zero),
          eqTo(None),
          eqTo(None),
          eqTo(None),
          eqTo(Some(UserId("personality-id"))),
          eqTo(None),
          eqTo(None)
        )
      ).thenReturn(
        Future.successful(
          Seq(
            Personality(
              PersonalityId("one"),
              UserId("personality-id"),
              QuestionId("question-id-one"),
              PersonalityRoleId("candidate")
            ),
            Personality(
              PersonalityId("two"),
              UserId("personality-id"),
              QuestionId("question-id-two"),
              PersonalityRoleId("candidate")
            )
          )
        )
      )
      when(
        questionPersonalityService.getPersonalitiesOpinionsByQuestions(
          eqTo(
            Seq(
              Personality(
                personalityId = PersonalityId("one"),
                userId = UserId("personality-id"),
                questionId = QuestionId("question-id-one"),
                personalityRoleId = PersonalityRoleId("candidate")
              ),
              Personality(
                personalityId = PersonalityId("two"),
                userId = UserId("personality-id"),
                questionId = QuestionId("question-id-two"),
                personalityRoleId = PersonalityRoleId("candidate")
              )
            )
          )
        )
      ).thenReturn(Future.successful(Seq.empty))

      Get("/personalities/personality-id/opinions") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val opinions = entityAs[Seq[PersonalityOpinionResponse]]
        opinions shouldBe empty
      }
    }

    Scenario("all comments") {
      when(
        questionPersonalityService.find(
          eqTo(Start.zero),
          eqTo(None),
          eqTo(None),
          eqTo(None),
          eqTo(Some(UserId("personality-id"))),
          eqTo(None),
          eqTo(None)
        )
      ).thenReturn(
        Future.successful(
          Seq(
            Personality(
              PersonalityId("one"),
              UserId("personality-id"),
              QuestionId("question-id-one"),
              PersonalityRoleId("candidate")
            ),
            Personality(
              PersonalityId("two"),
              UserId("personality-id"),
              QuestionId("question-id-two"),
              PersonalityRoleId("candidate")
            )
          )
        )
      )
      when(
        questionPersonalityService
          .getPersonalitiesOpinionsByQuestions(
            eqTo(
              Seq(
                Personality(
                  PersonalityId("one"),
                  UserId("personality-id"),
                  QuestionId("question-id-one"),
                  PersonalityRoleId("candidate")
                ),
                Personality(
                  PersonalityId("two"),
                  UserId("personality-id"),
                  QuestionId("question-id-two"),
                  PersonalityRoleId("candidate")
                )
              )
            )
          )
      ).thenReturn(
        Future.successful(
          Seq(
            PersonalityOpinionResponse(
              question = SimpleQuestionResponse(
                questionId = QuestionId("question-id"),
                slug = "slug",
                wording = SimpleQuestionWordingResponse("title", "question"),
                countries = NonEmptyList.of(Country("FR")),
                language = Language("fr"),
                startDate = ZonedDateTime.parse("1968-07-03T00:00:00.000Z"),
                endDate = ZonedDateTime.parse("2068-07-03T00:00:00.000Z")
              ),
              topIdea = QuestionTopIdeaWithAvatarResponse(
                id = TopIdeaId("top-idea-id"),
                ideaId = IdeaId("idea-id"),
                questionId = QuestionId("question-id"),
                name = "name",
                label = "label",
                scores = TopIdeaScores(0f, 0f, 0f),
                proposalsCount = 0,
                avatars = Seq.empty,
                weight = 0f,
                commentsCount = 0
              ),
              comment = None
            ),
            PersonalityOpinionResponse(
              question = SimpleQuestionResponse(
                questionId = QuestionId("question-id-two"),
                slug = "slug",
                wording = SimpleQuestionWordingResponse("title", "question"),
                countries = NonEmptyList.of(Country("FR")),
                language = Language("fr"),
                startDate = ZonedDateTime.parse("1968-07-03T00:00:00.000Z"),
                endDate = ZonedDateTime.parse("2068-07-03T00:00:00.000Z")
              ),
              topIdea = QuestionTopIdeaWithAvatarResponse(
                id = TopIdeaId("top-idea-id-two"),
                ideaId = IdeaId("idea-id-two"),
                questionId = QuestionId("question-id-two"),
                name = "name",
                label = "label",
                scores = TopIdeaScores(0f, 0f, 0f),
                proposalsCount = 0,
                avatars = Seq.empty,
                weight = 0f,
                commentsCount = 0
              ),
              comment = None
            )
          )
        )
      )

      Get("/personalities/personality-id/opinions") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val opinions = entityAs[Seq[PersonalityOpinionResponse]]
        opinions.size shouldBe 2
        opinions.head.question.questionId shouldBe QuestionId("question-id")
        opinions(1).comment shouldBe empty
      }
    }
  }

}
