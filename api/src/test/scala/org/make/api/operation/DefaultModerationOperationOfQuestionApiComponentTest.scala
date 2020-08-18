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

package org.make.api.operation
import java.time.ZonedDateTime

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import eu.timepit.refined.auto._
import io.circe.syntax._
import org.make.api.MakeApiTestBase
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.question.{QuestionService, QuestionServiceComponent, SearchQuestionRequest}
import org.make.api.technical.IdGeneratorComponent
import org.make.core.{Order, ValidationError}
import org.make.core.operation._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId

import scala.concurrent.Future

class DefaultModerationOperationOfQuestionApiComponentTest
    extends MakeApiTestBase
    with DefaultModerationOperationOfQuestionApiComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with OperationOfQuestionServiceComponent
    with QuestionServiceComponent {

  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]
  override val questionService: QuestionService = mock[QuestionService]

  when(operationOfQuestionService.create(any[CreateOperationOfQuestion])).thenAnswer {
    request: CreateOperationOfQuestion =>
      Future.successful(
        OperationOfQuestion(
          questionId = QuestionId("some-question"),
          operationId = request.operationId,
          startDate = request.startDate,
          endDate = request.endDate,
          operationTitle = request.operationTitle,
          landingSequenceId = SequenceId("some-sequence"),
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
          consultationImage = None,
          consultationImageAlt = None,
          descriptionImage = None,
          descriptionImageAlt = None,
          resultsLink = None,
          proposalsCount = 42,
          participantsCount = 84,
          actions = None,
          featured = true
        )
      )
  }

  when(operationOfQuestionService.findByQuestionId(any[QuestionId])).thenAnswer { questionId: QuestionId =>
    Future.successful(
      Some(
        OperationOfQuestion(
          questionId = questionId,
          operationId = OperationId("some-operation"),
          startDate = None,
          endDate = None,
          operationTitle = "some title",
          landingSequenceId = SequenceId("some-sequence"),
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
          consultationImage = None,
          consultationImageAlt = None,
          descriptionImage = None,
          descriptionImageAlt = None,
          resultsLink = None,
          proposalsCount = 42,
          participantsCount = 84,
          actions = None,
          featured = true
        )
      )
    )
  }

  when(operationOfQuestionService.updateWithQuestion(any[OperationOfQuestion], any[Question])).thenAnswer {
    (ooq: OperationOfQuestion, _: Question) =>
      Future.successful(ooq)
  }

  when(questionService.getQuestion(any[QuestionId])).thenAnswer { questionId: QuestionId =>
    Future.successful(
      Some(
        Question(
          questionId = questionId,
          slug = "some-question",
          country = Country("FR"),
          language = Language("fr"),
          question = "what's that?",
          shortTitle = None,
          operationId = None
        )
      )
    )
  }

  when(operationOfQuestionService.delete(any[QuestionId])).thenReturn(Future.successful {})

  when(operationOfQuestionService.findByOperationId(any[OperationId])).thenAnswer { operationId: OperationId =>
    Future.successful(
      Seq(
        OperationOfQuestion(
          questionId = QuestionId("question-1"),
          operationId = operationId,
          startDate = None,
          endDate = None,
          operationTitle = "opération en Français",
          landingSequenceId = SequenceId("landing-1"),
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
          consultationImage = None,
          consultationImageAlt = None,
          descriptionImage = None,
          descriptionImageAlt = None,
          resultsLink = None,
          proposalsCount = 42,
          participantsCount = 84,
          actions = None,
          featured = true
        ),
        OperationOfQuestion(
          questionId = QuestionId("question-2"),
          operationId = operationId,
          startDate = None,
          endDate = None,
          operationTitle = "Operation in English",
          landingSequenceId = SequenceId("landing-2"),
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
          consultationImage = None,
          consultationImageAlt = None,
          descriptionImage = None,
          descriptionImageAlt = None,
          resultsLink = None,
          proposalsCount = 42,
          participantsCount = 84,
          actions = None,
          featured = true
        )
      )
    )
  }

  when(questionService.searchQuestion(any[SearchQuestionRequest])).thenAnswer { request: SearchQuestionRequest =>
    Future.successful(
      Seq(
        Question(
          questionId = QuestionId("question-1"),
          country = Country("FR"),
          language = Language("fr"),
          slug = "question-1",
          question = "Est-ce que ?",
          shortTitle = None,
          operationId = request.maybeOperationIds.flatMap(_.headOption)
        ),
        Question(
          questionId = QuestionId("question-2"),
          country = Country("IE"),
          language = Language("en"),
          slug = "question-2",
          question = "Is it?",
          shortTitle = None,
          operationId = request.maybeOperationIds.flatMap(_.headOption)
        )
      )
    )
  }

  when(questionService.getQuestions(any[Seq[QuestionId]])).thenAnswer { ids: Seq[QuestionId] =>
    Future.successful(ids.map { questionId =>
      Question(
        questionId = questionId,
        slug = questionId.value,
        country = Country("FR"),
        language = Language("fr"),
        question = questionId.value,
        shortTitle = None,
        operationId = None
      )
    })
  }

  when(
    operationOfQuestionService
      .find(any[Int], any[Option[Int]], any[Option[String]], any[Option[Order]], any[SearchOperationsOfQuestions])
  ).thenReturn(
    Future.successful(
      Seq(
        OperationOfQuestion(
          questionId = QuestionId("question-1"),
          operationId = OperationId("operation-1"),
          startDate = None,
          endDate = None,
          operationTitle = "some title",
          landingSequenceId = SequenceId("sequence-1"),
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
          consultationImage = None,
          consultationImageAlt = None,
          descriptionImage = None,
          descriptionImageAlt = None,
          resultsLink = None,
          proposalsCount = 42,
          participantsCount = 84,
          actions = None,
          featured = true
        ),
        OperationOfQuestion(
          questionId = QuestionId("question-2"),
          operationId = OperationId("operation-2"),
          startDate = None,
          endDate = None,
          operationTitle = "some title",
          landingSequenceId = SequenceId("sequence-2"),
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
          consultationImage = None,
          consultationImageAlt = None,
          descriptionImage = None,
          descriptionImageAlt = None,
          resultsLink = None,
          proposalsCount = 42,
          participantsCount = 84,
          actions = None,
          featured = true
        )
      )
    )
  )

  when(operationOfQuestionService.count(any[SearchOperationsOfQuestions])).thenReturn(Future.successful(2))

  val routes: Route = sealRoute(moderationOperationOfQuestionApi.routes)

  Feature("access control") {
    Scenario("unauthenticated user") {
      Get("/moderation/operations-of-questions") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Post("/moderation/operations-of-questions") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Get("/moderation/operations-of-questions/some-question") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Put("/moderation/operations-of-questions/some-question") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Delete("/moderation/operations-of-questions/some-question") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("invalid token") {

      Get("/moderation/operations-of-questions")
        .withHeaders(Authorization(OAuth2BearerToken("invalid-token"))) ~> routes ~> check {

        status should be(StatusCodes.Unauthorized)
      }

      Post("/moderation/operations-of-questions")
        .withHeaders(Authorization(OAuth2BearerToken("invalid-token"))) ~> routes ~> check {

        status should be(StatusCodes.Unauthorized)
      }

      Get("/moderation/operations-of-questions/some-question")
        .withHeaders(Authorization(OAuth2BearerToken("invalid-token"))) ~> routes ~> check {

        status should be(StatusCodes.Unauthorized)
      }

      Put("/moderation/operations-of-questions/some-question")
        .withHeaders(Authorization(OAuth2BearerToken("invalid-token"))) ~> routes ~> check {

        status should be(StatusCodes.Unauthorized)
      }

      Delete("/moderation/operations-of-questions/some-question")
        .withHeaders(Authorization(OAuth2BearerToken("invalid-token"))) ~> routes ~> check {

        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("citizen user") {

      Get("/moderation/operations-of-questions")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {

        status should be(StatusCodes.Forbidden)
      }

      Post("/moderation/operations-of-questions")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {

        status should be(StatusCodes.Forbidden)
      }

      Get("/moderation/operations-of-questions/some-question")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {

        status should be(StatusCodes.Forbidden)
      }

      Put("/moderation/operations-of-questions/some-question")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {

        status should be(StatusCodes.Forbidden)
      }

      Delete("/moderation/operations-of-questions/some-question")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {

        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("admin-only endpoints") {
      Post("/moderation/operations-of-questions")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {

        status should be(StatusCodes.Forbidden)
      }

      Put("/moderation/operations-of-questions/some-question")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {

        status should be(StatusCodes.Forbidden)
      }

      Delete("/moderation/operations-of-questions/some-question")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {

        status should be(StatusCodes.Forbidden)
      }
    }

  }

  Feature("create operationOfQuestion") {
    Scenario("create as moderator") {
      Post("/moderation/operations-of-questions")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(
          ContentTypes.`application/json`,
          CreateOperationOfQuestionRequest(
            operationId = OperationId("some-operation"),
            startDate = Some(ZonedDateTime.parse("2018-12-01T10:15:30+00:00")),
            endDate = None,
            operationTitle = "my-operation",
            country = Country("FR"),
            language = Language("fr"),
            question = "how to save the world?",
            shortTitle = None,
            questionSlug = "make-the-world-great-again",
            consultationImage = Some("https://example.com/image"),
            consultationImageAlt = Some("image alternative"),
            descriptionImage = Some("https://example.com/image-desc"),
            descriptionImageAlt = Some("image-desc alternative"),
            actions = None
          ).asJson.toString()
        ) ~> routes ~> check {

        status should be(StatusCodes.Created)
        val operationOfQuestion = entityAs[OperationOfQuestionResponse]
        operationOfQuestion.canPropose shouldBe true
      }
    }

    Scenario("create as moderator with bad consultationImage format") {
      Post("/moderation/operations-of-questions")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(ContentTypes.`application/json`, """{
            |"operationId": "some-operation",
            |"startDate": "2018-12-01T10:15:30+00:00",
            |"question": "question ?",
            |"questionSlug": "question-slug",
            |"operationTitle": "my-operation",
            |"country": "FR",
            |"language": "fr",
            |"consultationImage": "wrongurlformat"
            |}""".stripMargin) ~> routes ~> check {

        status should be(StatusCodes.BadRequest)
      }
    }
  }

  Feature("update operationOfQuestion") {
    Scenario("update as moderator") {
      Put("/moderation/operations-of-questions/my-question")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(
          ContentTypes.`application/json`,
          ModifyOperationOfQuestionRequest(
            startDate = Some(ZonedDateTime.parse("2018-12-01T10:15:30+00:00")),
            endDate = None,
            canPropose = true,
            question = "question ?",
            operationTitle = "new title",
            shortTitle = None,
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
            displayResults = true,
            consultationImage = None,
            consultationImageAlt = None,
            descriptionImage = None,
            descriptionImageAlt = None,
            resultsLink =
              Some(ResultsLinkRequest(ResultsLinkRequest.ResultsLinkKind.External, "https://example.com/results")),
            actions = Some("some actions"),
            featured = false
          ).asJson.toString()
        ) ~> routes ~> check {

        status should be(StatusCodes.OK)
      }
    }

    Scenario("update with bad color") {

      Put("/moderation/operations-of-questions/my-question")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(ContentTypes.`application/json`, """{
            | "startDate": "2018-12-01T10:15:30.000Z",
            | "canPropose": true,
            | "question": "question ?",
            | "operationTitle": "title",
            | "sequenceCardsConfiguration": {
            |   "introCard": { "enabled": true },
            |   "pushProposalCard": { "enabled": true },
            |   "signUpCard": { "enabled": true },
            |   "finalCard": {
            |     "enabled": true,
            |     "sharingEnabled": false
            |   }
            | },
            | "metas": { "title": "metas" },
            | "theme": {
            |   "gradientStart": "wrongFormattedColor",
            |   "gradientEnd": "#000000",
            |   "color": "#000000",
            |   "fontColor": "#000000"
            | },
            | "description": "description",
            | "displayResults": false,
            | "consultationImage": "https://example",
            | "featured": true
          }""".stripMargin) ~> routes ~> check {

        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size should be(1)
        errors.head.field shouldBe "gradientStart"
      }
    }

    Scenario("update with bad shortTitle") {

      Put("/moderation/operations-of-questions/my-question")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(
          ContentTypes.`application/json`,
          """{
           | "startDate": "2018-12-01T10:15:30.000Z",
           | "canPropose": true,
           | "question": "question ?",
           | "operationTitle": "title",
           | "shortTitle": "Il s'agit d'un short title de plus de 30 charactères !",
           | "sequenceCardsConfiguration": {
           |   "introCard": { "enabled": true },
           |   "pushProposalCard": { "enabled": true },
           |   "signUpCard": { "enabled": true },
           |   "finalCard": {
           |     "enabled": true,
           |     "sharingEnabled": false
           |   }
           | },
           | "metas": { "title": "metas" },
           | "theme": {
           |   "gradientStart": "#000000",
           |   "gradientEnd": "#000000",
           |   "color": "#000000",
           |   "fontColor": "#000000"
           | },
           | "description": "description",
           | "displayResults": false,
           | "consultationImage": "https://example.com",
           | "featured": true
           |}""".stripMargin
        ) ~> routes ~> check {

        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size should be(1)
        errors.head.field shouldBe "shortTitle"
      }
    }

    Scenario("update image as moderator with incorrect URL") {
      Put("/moderation/operations-of-questions/my-question")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(ContentTypes.`application/json`, """{
                                                       | "startDate": "2018-12-01T10:15:30.000Z",
                                                       | "canPropose": true,
                                                       | "question": "question ?",
                                                       | "operationTitle": "title",
                                                       | "sequenceCardsConfiguration": {
                                                       |   "introCard": { "enabled": true },
                                                       |   "pushProposalCard": { "enabled": true },
                                                       |   "signUpCard": { "enabled": true },
                                                       |   "finalCard": {
                                                       |     "enabled": true,
                                                       |     "sharingEnabled": false
                                                       |   }
                                                       | },
                                                       | "metas": { "title": "metas" },
                                                       | "theme": {
                                                       |   "gradientStart": "#000000",
                                                       |   "gradientEnd": "#000000",
                                                       |   "color": "#000000",
                                                       |   "fontColor": "#000000"
                                                       | },
                                                       | "description": "description",
                                                       | "displayResults": false,
                                                       | "consultationImage": "wrong URL",
                                                       | "descriptionImage": "wrong URL",
                                                       | "featured": true
                                                       | }""".stripMargin) ~> routes ~> check {

        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size should be(2)
        errors.head.field shouldBe "consultationImage"
        errors(1).field shouldBe "descriptionImage"
      }
    }
  }

  Feature("delete operationOfQuestion") {
    Scenario("delete as admin") {
      Delete("/moderation/operations-of-questions/my-question")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {

        status should be(StatusCodes.NoContent)
      }
    }
  }

  Feature("get by operation of question") {
    Scenario("get as moderator") {
      Get("/moderation/operations-of-questions/some-question")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {

        status should be(StatusCodes.OK)
      }
    }
  }

  Feature("search operation of question") {
    Scenario("search as moderator") {
      Get("/moderation/operations-of-questions?openAt=2018-01-01")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {

        status should be(StatusCodes.OK)
      }
    }
  }

}
