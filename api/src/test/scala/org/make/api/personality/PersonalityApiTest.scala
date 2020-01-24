package org.make.api.personality

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.idea.topIdeaComments.{TopIdeaCommentService, TopIdeaCommentServiceComponent}
import org.make.api.idea.{TopIdeaService, TopIdeaServiceComponent}
import org.make.api.operation.{OperationOfQuestionService, OperationOfQuestionServiceComponent}
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.api.user.{UserResponse, UserService, UserServiceComponent}
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.core.RequestContext
import org.make.core.idea._
import org.make.core.operation.{
  FinalCard,
  IntroCard,
  Metas,
  OperationId,
  OperationOfQuestion,
  PushProposalCard,
  QuestionTheme,
  SequenceCardsConfiguration,
  SignUpCard
}
import org.make.core.personality.{Candidate, Personality, PersonalityId}
import org.make.core.profile.Profile
import org.make.core.proposal.{QualificationKey, VoteKey}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.user.{User, UserId, UserType}
import org.mockito.Mockito.when
import org.mockito.{ArgumentMatchers, Mockito}

import scala.concurrent.Future

class PersonalityApiTest
    extends MakeApiTestBase
    with DefaultPersonalityApiComponent
    with UserServiceComponent
    with TopIdeaCommentServiceComponent
    with TopIdeaServiceComponent
    with QuestionPersonalityServiceComponent
    with QuestionServiceComponent
    with OperationOfQuestionServiceComponent {

  override val userService: UserService = mock[UserService]
  override val topIdeaCommentService: TopIdeaCommentService = mock[TopIdeaCommentService]
  override val topIdeaService: TopIdeaService = mock[TopIdeaService]
  override val questionPersonalityService: QuestionPersonalityService = mock[QuestionPersonalityService]
  override val questionService: QuestionService = mock[QuestionService]
  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]

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
                "label",
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

  feature("get personality opinions") {

    scenario("personality not found") {
      when(
        questionPersonalityService.find(
          ArgumentMatchers.eq(0),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(Some(UserId("non-existant"))),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None)
        )
      ).thenReturn(Future.successful(Seq.empty))

      Get("/personalities/non-existant/opinions") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    scenario("multiple questions for personality") {
      when(
        questionPersonalityService.find(
          ArgumentMatchers.eq(0),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(Some(UserId("multiple-personality-id"))),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None)
        )
      ).thenReturn(
        Future.successful(
          Seq(
            Personality(
              PersonalityId("one"),
              UserId("multiple-personality-id"),
              QuestionId("question-id-one"),
              Candidate
            ),
            Personality(
              PersonalityId("two"),
              UserId("multiple-personality-id"),
              QuestionId("question-id-two"),
              Candidate
            ),
          )
        )
      )
      Get("/personalities/multiple-personality-id/opinions") ~> routes ~> check {
        status shouldBe StatusCodes.MultipleChoices
      }
    }

    scenario("questions not found") {
      when(
        questionPersonalityService.find(
          ArgumentMatchers.eq(0),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(Some(UserId("personality-id"))),
          ArgumentMatchers.any[Option[QuestionId]],
          ArgumentMatchers.eq(None)
        )
      ).thenReturn(
        Future.successful(
          Seq(Personality(PersonalityId("id"), UserId("personality-id"), QuestionId("fake-question-id"), Candidate))
        )
      )

      when(questionService.getQuestion(ArgumentMatchers.eq(QuestionId("fake-question-id"))))
        .thenReturn(Future.successful(None))
      Get("/personalities/personality-id/opinions?") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    scenario("empty list of top ideas") {
      when(
        questionPersonalityService.find(
          ArgumentMatchers.eq(0),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(Some(UserId("personality-id"))),
          ArgumentMatchers.any[Option[QuestionId]],
          ArgumentMatchers.eq(None)
        )
      ).thenReturn(
        Future.successful(
          Seq(Personality(PersonalityId("id"), UserId("personality-id"), QuestionId("question-id"), Candidate))
        )
      )
      when(questionService.getQuestion(ArgumentMatchers.eq(QuestionId("question-id"))))
        .thenReturn(
          Future.successful(
            Some(Question(QuestionId("question-id"), "slug", Country("FR"), Language("fr"), "question", None, None))
          )
        )
      when(operationOfQuestionService.findByQuestionId(ArgumentMatchers.eq(QuestionId("question-id"))))
        .thenReturn(
          Future.successful(
            Some(
              OperationOfQuestion(
                operationId = OperationId("operation-id"),
                questionId = QuestionId("question-id"),
                startDate = None,
                endDate = None,
                operationTitle = "title",
                landingSequenceId = SequenceId("sequence-id"),
                canPropose = true,
                sequenceCardsConfiguration = SequenceCardsConfiguration(
                  introCard = IntroCard(enabled = true, title = None, description = None),
                  pushProposalCard = PushProposalCard(enabled = true),
                  signUpCard = SignUpCard(enabled = true, title = None, nextCtaText = None),
                  finalCard = FinalCard(
                    enabled = true,
                    sharingEnabled = false,
                    title = None,
                    shareDescription = None,
                    learnMoreTitle = None,
                    learnMoreTextButton = None,
                    linkUrl = None
                  )
                ),
                aboutUrl = None,
                metas = Metas(title = None, description = None, picture = None),
                theme = QuestionTheme.default,
                description = OperationOfQuestion.defaultDescription,
                consultationImage = Some("https://example.com/image"),
                descriptionImage = Some("https://example.com/descriptionImage"),
                displayResults = false
              )
            )
          )
        )
      when(
        topIdeaService.search(
          ArgumentMatchers.eq(0),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(Some(QuestionId("question-id"))),
          ArgumentMatchers.eq(None)
        )
      ).thenReturn(Future.successful(Seq.empty))
      when(
        topIdeaCommentService
          .search(
            ArgumentMatchers.eq(0),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(Some(Seq.empty)),
            ArgumentMatchers.eq(Some(Seq(UserId("personality-id"))))
          )
      ).thenReturn(Future.successful(Seq.empty))
      Get("/personalities/personality-id/opinions") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val opinions = entityAs[Seq[PersonalityOpinionResponse]]
        opinions shouldBe empty
      }
    }
    scenario("all comments") {
      when(
        questionPersonalityService.find(
          ArgumentMatchers.eq(0),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(Some(UserId("personality-id"))),
          ArgumentMatchers.any[Option[QuestionId]],
          ArgumentMatchers.eq(None)
        )
      ).thenReturn(
        Future.successful(
          Seq(Personality(PersonalityId("id"), UserId("personality-id"), QuestionId("question-id"), Candidate))
        )
      )
      when(questionService.getQuestion(ArgumentMatchers.eq(QuestionId("question-id"))))
        .thenReturn(
          Future.successful(
            Some(Question(QuestionId("question-id"), "slug", Country("FR"), Language("fr"), "question", None, None))
          )
        )
      when(operationOfQuestionService.findByQuestionId(ArgumentMatchers.eq(QuestionId("question-id"))))
        .thenReturn(
          Future.successful(
            Some(
              OperationOfQuestion(
                operationId = OperationId("operation-id"),
                questionId = QuestionId("question-id"),
                startDate = None,
                endDate = None,
                operationTitle = "title",
                landingSequenceId = SequenceId("sequence-id"),
                canPropose = true,
                sequenceCardsConfiguration = SequenceCardsConfiguration(
                  introCard = IntroCard(enabled = true, title = None, description = None),
                  pushProposalCard = PushProposalCard(enabled = true),
                  signUpCard = SignUpCard(enabled = true, title = None, nextCtaText = None),
                  finalCard = FinalCard(
                    enabled = true,
                    sharingEnabled = false,
                    title = None,
                    shareDescription = None,
                    learnMoreTitle = None,
                    learnMoreTextButton = None,
                    linkUrl = None
                  )
                ),
                aboutUrl = None,
                metas = Metas(title = None, description = None, picture = None),
                theme = QuestionTheme.default,
                description = OperationOfQuestion.defaultDescription,
                consultationImage = Some("https://example.com/image"),
                descriptionImage = Some("https://example.com/descriptionImage"),
                displayResults = false
              )
            )
          )
        )
      when(
        topIdeaService.search(
          ArgumentMatchers.eq(0),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(Some(QuestionId("question-id"))),
          ArgumentMatchers.eq(None)
        )
      ).thenReturn(
        Future.successful(
          Seq(
            TopIdea(
              TopIdeaId("top-idea-id"),
              IdeaId("idea-id"),
              QuestionId("question-id"),
              "name",
              "label",
              TopIdeaScores(0f, 0f, 0f),
              0f
            ),
            TopIdea(
              TopIdeaId("top-idea-id-2"),
              IdeaId("idea-id-2"),
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
        topIdeaCommentService
          .search(
            ArgumentMatchers.eq(0),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(Some(Seq(TopIdeaId("top-idea-id"), TopIdeaId("top-idea-id-2")))),
            ArgumentMatchers.eq(Some(Seq(UserId("personality-id"))))
          )
      ).thenReturn(
        Future.successful(
          Seq(
            TopIdeaComment(
              TopIdeaCommentId("top-idea-comment-id"),
              TopIdeaId("top-idea-id"),
              UserId("personality-id"),
              Some("comment one"),
              Some("comment two"),
              None,
              Some(VoteKey.Agree),
              None
            )
          )
        )
      )
      Get("/personalities/personality-id/opinions") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val opinions = entityAs[Seq[PersonalityOpinionResponse]]
        opinions.size shouldBe 2
        opinions.head.comment shouldBe defined
        opinions(1).comment shouldBe empty
      }
    }
  }

}
