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
import java.time.{LocalDate, ZonedDateTime}
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import cats.data.NonEmptyList
import eu.timepit.refined.auto._
import io.circe.syntax._
import org.make.api.MakeApiTestBase
import org.make.api.question.{QuestionService, QuestionServiceComponent, SearchQuestionRequest}
import org.make.core.auth.UserRights
import org.make.core.operation.OperationKind.GreatCause
import org.make.core.{Order, ValidationError}
import org.make.core.operation._
import org.make.core.operation.indexed.{OperationOfQuestionElasticsearchFieldName, OperationOfQuestionSearchResult}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}

import scala.concurrent.{Await, Future}
import org.make.core.technical.Pagination.{End, Start}
import org.make.core.user.Role
import scalaoauth2.provider.AuthInfo

import scala.concurrent.duration.DurationInt

class DefaultModerationOperationOfQuestionApiComponentTest
    extends MakeApiTestBase
    with DefaultModerationOperationOfQuestionApiComponent
    with OperationOfQuestionServiceComponent
    with QuestionServiceComponent
    with OperationServiceComponent {

  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]
  override val questionService: QuestionService = mock[QuestionService]
  override val operationService: OperationService = mock[OperationService]

  when(operationOfQuestionService.create(any[CreateOperationOfQuestion])).thenAnswer {
    request: CreateOperationOfQuestion =>
      Future.successful(
        operationOfQuestion(
          questionId = QuestionId("some-question"),
          operationId = request.operationId,
          startDate = request.startDate,
          endDate = request.endDate,
          operationTitle = request.operationTitle,
          featured = request.featured
        )
      )
  }

  when(operationOfQuestionService.findByQuestionId(any[QuestionId])).thenAnswer { questionId: QuestionId =>
    Future.successful(Some(operationOfQuestion(questionId = questionId, operationId = OperationId("some-operation"))))
  }

  when(operationOfQuestionService.updateWithQuestion(any[OperationOfQuestion], any[Question])).thenAnswer {
    (ooq: OperationOfQuestion, _: Question) =>
      Future.successful(ooq)
  }

  when(questionService.getQuestion(any[QuestionId])).thenAnswer { questionId: QuestionId =>
    Future.successful(
      Some(
        question(
          id = questionId,
          slug = "some-question",
          countries = NonEmptyList.of(Country("BE"), Country("FR")),
          language = Language("fr")
        )
      )
    )
  }

  when(operationOfQuestionService.delete(any[QuestionId])).thenReturn(Future.unit)

  when(operationOfQuestionService.findByOperationId(any[OperationId])).thenAnswer { operationId: OperationId =>
    Future.successful(
      Seq(
        operationOfQuestion(
          questionId = QuestionId("question-1"),
          operationId = operationId,
          operationTitle = "opération en Français"
        ),
        operationOfQuestion(
          questionId = QuestionId("question-2"),
          operationId = operationId,
          operationTitle = "Operation in English"
        )
      )
    )
  }

  when(questionService.searchQuestion(any[SearchQuestionRequest])).thenAnswer { request: SearchQuestionRequest =>
    Future.successful(
      Seq(
        question(
          id = QuestionId("question-1"),
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          slug = "question-1",
          operationId = request.maybeOperationIds.flatMap(_.headOption)
        ),
        question(
          id = QuestionId("question-2"),
          countries = NonEmptyList.of(Country("IE")),
          language = Language("en"),
          slug = "question-2",
          operationId = request.maybeOperationIds.flatMap(_.headOption)
        )
      )
    )
  }

  when(questionService.getQuestions(any[Seq[QuestionId]])).thenAnswer { ids: Seq[QuestionId] =>
    Future.successful(ids.map { questionId =>
      question(
        id = questionId,
        slug = questionId.value,
        countries = NonEmptyList.of(Country("FR")),
        language = Language("fr")
      )
    })
  }

  when(
    operationOfQuestionService
      .find(any[Start], any[Option[End]], any[Option[String]], any[Option[Order]], any[SearchOperationsOfQuestions])
  ).thenReturn(
    Future.successful(
      Seq(
        operationOfQuestion(questionId = QuestionId("question-1"), operationId = OperationId("operation-1")),
        operationOfQuestion(questionId = QuestionId("question-2"), operationId = OperationId("operation-2"))
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
    when(operationService.findOneSimple(eqTo(OperationId("some-operation"))))
      .thenReturn(Future.successful(Some(simpleOperation(OperationId("some-operation"), operationKind = GreatCause))))
    val existingQuestion = operationOfQuestion(
      questionId = QuestionId("existing-question"),
      operationId = OperationId("operation"),
      operationTitle = "opération en Français"
    )

    Scenario("create as moderator") {
      when(operationOfQuestionService.findByQuestionSlug("make-the-world-great-again"))
        .thenReturn(Future.successful(None))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/moderation/operations-of-questions")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(
            ContentTypes.`application/json`,
            CreateOperationOfQuestionRequest(
              operationId = OperationId("some-operation"),
              startDate = ZonedDateTime.parse("2018-12-01T10:15:30+00:00"),
              endDate = ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
              operationTitle = "my-operation",
              countries = NonEmptyList.of(Country("FR")),
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
          operationOfQuestion.featured shouldBe true
        }
      }
    }

    Scenario("slug already exists") {
      when(operationOfQuestionService.findByQuestionSlug("existing-question"))
        .thenReturn(Future.successful(Some(existingQuestion)))
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/moderation/operations-of-questions")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(
            ContentTypes.`application/json`,
            CreateOperationOfQuestionRequest(
              operationId = OperationId("some-operation"),
              startDate = ZonedDateTime.parse("2018-12-01T10:15:30+00:00"),
              endDate = ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
              operationTitle = "my-operation",
              countries = NonEmptyList.of(Country("FR")),
              language = Language("fr"),
              question = "how to save the world?",
              shortTitle = None,
              questionSlug = "existing-question",
              consultationImage = Some("https://example.com/image"),
              consultationImageAlt = Some("image alternative"),
              descriptionImage = Some("https://example.com/image-desc"),
              descriptionImageAlt = Some("image-desc alternative"),
              actions = None
            ).asJson.toString()
          ) ~> routes ~> check {

          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          val contentError = errors.find(_.field == "slug")
          contentError should be(
            Some(ValidationError("slug", "non_empty", Some("Slug 'existing-question' already exists")))
          )
        }
      }
    }

    Scenario("create as moderator with bad consultationImage format") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/moderation/operations-of-questions")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
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

    Scenario("operation does no exist") {
      when(operationService.findOneSimple(eqTo(OperationId("fake")))).thenReturn(Future.successful(None))
      when(operationOfQuestionService.findByQuestionSlug("whatever-question"))
        .thenReturn(Future.successful(None))
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/moderation/operations-of-questions")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(
            ContentTypes.`application/json`,
            CreateOperationOfQuestionRequest(
              operationId = OperationId("fake"),
              startDate = ZonedDateTime.parse("2018-12-01T10:15:30+00:00"),
              endDate = ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
              operationTitle = "my-operation",
              countries = NonEmptyList.of(Country("FR")),
              language = Language("fr"),
              question = "how to save the world?",
              shortTitle = None,
              questionSlug = "whatever-question",
              consultationImage = Some("https://example.com/image"),
              consultationImageAlt = Some("image alternative"),
              descriptionImage = Some("https://example.com/image-desc"),
              descriptionImageAlt = Some("image-desc alternative"),
              actions = None
            ).asJson.toString()
          ) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size should be(1)
          errors.head.field shouldBe "operationId"
        }
      }
    }
  }

  Feature("update operationOfQuestion") {

    val updateRequest = ModifyOperationOfQuestionRequest(
      startDate = ZonedDateTime.parse("2018-12-01T10:15:30+00:00"),
      endDate = ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
      canPropose = true,
      question = "question ?",
      operationTitle = "new title",
      countries = NonEmptyList.of(Country("BE"), Country("FR")),
      shortTitle = None,
      sequenceCardsConfiguration = SequenceCardsConfiguration.default,
      aboutUrl = None,
      metas = Metas(title = None, description = None, picture = None),
      theme = QuestionTheme.default,
      description = None,
      displayResults = true,
      consultationImage = None,
      consultationImageAlt = None,
      descriptionImage = None,
      descriptionImageAlt = None,
      resultsLink = Some(ResultsLinkRequest(ResultsLinkRequest.ResultsLinkKind.External, "https://example.com/results")),
      actions = Some("some actions"),
      featured = false,
      votesTarget = 100_000,
      timeline = OperationOfQuestionTimelineContract(
        TimelineElementContract(defined = false, None, None, None),
        TimelineElementContract(defined = false, None, None, None),
        TimelineElementContract(defined = false, None, None, None)
      )
    )

    Scenario("update as moderator") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/moderation/operations-of-questions/my-question")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(ContentTypes.`application/json`, updateRequest.asJson.toString()) ~> routes ~> check {

          status should be(StatusCodes.OK)
        }
      }
    }

    Scenario("add a country") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/moderation/operations-of-questions/my-question")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(
            ContentTypes.`application/json`,
            ModifyOperationOfQuestionRequest(
              startDate = ZonedDateTime.parse("2018-12-01T10:15:30+00:00"),
              endDate = ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
              canPropose = true,
              question = "question ?",
              operationTitle = "new title",
              countries = updateRequest.countries :+ Country("DE"),
              shortTitle = None,
              sequenceCardsConfiguration = SequenceCardsConfiguration.default,
              aboutUrl = None,
              metas = Metas(title = None, description = None, picture = None),
              theme = QuestionTheme.default,
              description = None,
              displayResults = true,
              consultationImage = None,
              consultationImageAlt = None,
              descriptionImage = None,
              descriptionImageAlt = None,
              resultsLink =
                Some(ResultsLinkRequest(ResultsLinkRequest.ResultsLinkKind.External, "https://example.com/results")),
              actions = Some("some actions"),
              featured = false,
              votesTarget = 100_000,
              timeline = OperationOfQuestionTimelineContract(
                action = TimelineElementContract(
                  defined = true,
                  date = Some(LocalDate.now()),
                  dateText = Some("in a long time"),
                  description = Some("action description")
                ),
                result = TimelineElementContract(
                  defined = false,
                  date = Some(LocalDate.now()),
                  dateText = Some("in a long time"),
                  description = Some("results description")
                ),
                workshop = TimelineElementContract(
                  defined = true,
                  date = Some(LocalDate.now()),
                  dateText = Some("in a long time"),
                  description = Some("workshop description")
                )
              )
            ).asJson.toString()
          ) ~> routes ~> check {

          status should be(StatusCodes.OK)
        }
      }
    }

    Scenario("try removing a country") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/moderation/operations-of-questions/my-question")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(
            ContentTypes.`application/json`,
            updateRequest.copy(countries = NonEmptyList.of(Country("FR"))).asJson.toString()
          ) ~> routes ~> check {

          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors shouldBe Seq(
            ValidationError("countries", "invalid_value", Some("You can not remove existing countries: BE, FR"))
          )
        }
      }
    }

    Scenario("update with bad color") {

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/moderation/operations-of-questions/my-question")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(ContentTypes.`application/json`, """{
            | "startDate": "2018-12-01T10:15:30.000Z",
            | "endDate": "2068-12-01T10:15:30.000Z",
            | "canPropose": true,
            | "question": "question ?",
            | "operationTitle": "title",
            | "countries": ["BE", "FR"],
            | "sequenceCardsConfiguration": {
            |   "introCard": { "enabled": true },
            |   "pushProposalCard": { "enabled": true },
            |   "signUpCard": { "enabled": true },
            |   "finalCard": {
            |     "enabled": true,
            |     "sharingEnabled": true
            |   }
            | },
            | "metas": { "title": "metas" },
            | "theme": {
            |   "color": "#000000",
            |   "fontColor": "wrongFormattedColor"
            | },
            | "description": "description",
            | "displayResults": false,
            | "consultationImage": "https://example",
            | "featured": true,
            | "votesTarget": 100000,
            | "timeline": {
            |   "action": {
            |     "defined": true,
            |     "date": "2168-12-01",
            |     "dateText": "in a long time",
            |     "description": "action description"
            |   },
            |   "result": {
            |     "defined": false,
            |     "date": "2168-12-01",
            |     "dateText": "in a long time",
            |     "description": "results description"
            |   },
            |   "workshop": {
            |     "defined": true,
            |     "date": "2168-12-01",
            |     "dateText": "in a long time",
            |     "description": "workshop description"
            |   }
            | }
            |}""".stripMargin) ~> routes ~> check {

          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size should be(1)
          errors.head.field shouldBe "fontColor"
        }
      }
    }

    Scenario("update with bad shortTitle") {

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/moderation/operations-of-questions/my-question")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(
            ContentTypes.`application/json`,
            """{
           | "startDate": "2018-12-01T10:15:30.000Z",
           | "endDate": "2068-12-01T10:15:30.000Z",
           | "canPropose": true,
           | "question": "question ?",
           | "operationTitle": "title",
           | "countries": ["BE", "FR"],
           | "shortTitle": "Il s'agit d'un short title de plus de 30 charactères !",
           | "sequenceCardsConfiguration": {
           |   "introCard": { "enabled": true },
           |   "pushProposalCard": { "enabled": true },
           |   "signUpCard": { "enabled": true },
           |   "finalCard": {
           |     "enabled": true,
           |     "sharingEnabled": true
           |   }
           | },
           | "metas": { "title": "metas" },
           | "theme": {
           |   "color": "#000000",
           |   "fontColor": "#000000"
           | },
           | "description": "description",
           | "displayResults": false,
           | "consultationImage": "https://example.com",
           | "featured": true,
           | "votesTarget": 100000,
           | "timeline": {
           |   "action": {
           |     "defined": true,
           |     "date": "2168-12-01",
           |     "dateText": "in a long time",
           |     "description": "action description"
           |   },
           |   "result": {
           |     "defined": false
           |   },
           |   "workshop": {
           |     "defined": false
           |   }
           | }
           |}""".stripMargin
          ) ~> routes ~> check {

          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size should be(1)
          errors.head.field shouldBe "shortTitle"
        }
      }
    }

    Scenario("update image as moderator with incorrect URL") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/moderation/operations-of-questions/my-question")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(ContentTypes.`application/json`, """{
                                                       | "startDate": "2018-12-01T10:15:30.000Z",
                                                       | "endDate": "2068-12-01T10:15:30.000Z",
                                                       | "canPropose": true,
                                                       | "question": "question ?",
                                                       | "operationTitle": "title",
                                                       | "countries": ["BE", "FR"],
                                                       | "sequenceCardsConfiguration": {
                                                       |   "introCard": { "enabled": true },
                                                       |   "pushProposalCard": { "enabled": true },
                                                       |   "signUpCard": { "enabled": true },
                                                       |   "finalCard": {
                                                       |     "enabled": true,
                                                       |     "sharingEnabled": true
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
                                                       | "featured": true,
                                                       | "votesTarget": 100000,
                                                       | "timeline": {
                                                       |   "action": {
                                                       |     "defined": false
                                                       |   },
                                                       |   "result": {
                                                       |     "defined": false
                                                       |   },
                                                       |   "workshop": {
                                                       |     "defined": false
                                                       |   }
                                                       | }
                                                       |}""".stripMargin) ~> routes ~> check {

          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size should be(2)
          errors.head.field shouldBe "consultationImage"
          errors(1).field shouldBe "descriptionImage"
        }
      }
    }

    Scenario("update with invalid timeline") {

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/moderation/operations-of-questions/my-question")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(ContentTypes.`application/json`, """{
            | "startDate": "2018-12-01T10:15:30.000Z",
            | "endDate": "2068-12-01T10:15:30.000Z",
            | "canPropose": true,
            | "question": "question ?",
            | "operationTitle": "title",
            | "countries": ["BE", "FR"],
            | "shortTitle": "short title",
            | "sequenceCardsConfiguration": {
            |   "introCard": { "enabled": true },
            |   "pushProposalCard": { "enabled": true },
            |   "signUpCard": { "enabled": true },
            |   "finalCard": {
            |     "enabled": true,
            |     "sharingEnabled": true
            |   }
            | },
            | "metas": { "title": "metas" },
            | "theme": {
            |   "color": "#000000",
            |   "fontColor": "#000000"
            | },
            | "description": "description",
            | "displayResults": false,
            | "consultationImage": "https://example.com",
            | "featured": true,
            | "votesTarget": 100000,
            | "timeline": {
            |   "action": {
            |     "defined": true,
            |     "date": "2168-12-01",
            |     "dateText": "missing description"
            |   },
            |   "result": {
            |     "defined": false,
            |     "date": "2168-12-01",
            |     "dateText": "in a long time",
            |     "description": "results description"
            |   },
            |   "workshop": {
            |     "defined": true,
            |     "date": "2168-12-01",
            |     "dateText": "in a long time",
            |     "description": "workshop description"
            |   }
            | }
            |}""".stripMargin) ~> routes ~> check {

          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size should be(1)
          errors.head.field shouldBe "timeline.description"
        }
      }
    }
  }

  Feature("delete operationOfQuestion") {
    Scenario("delete as admin") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Delete("/moderation/operations-of-questions/my-question")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {

          status should be(StatusCodes.NoContent)
        }
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

  Feature("list operation of question") {
    Scenario("list as moderator") {
      Get("/moderation/operations-of-questions?openAt=2018-01-01")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {

        status should be(StatusCodes.OK)
      }
    }
  }

  Feature("search operation of question") {
    Scenario("forbidden when not moderator or admin") {
      for (token <- Seq(None, Some(tokenCitizen))) {
        Get("/moderation/operations-of-questions/search")
          .withHeaders(token.map(t => Authorization(OAuth2BearerToken(t))).toList) ~> routes ~> check {
          status should be(token.fold(StatusCodes.Unauthorized)(_ => StatusCodes.Forbidden))
        }
      }
    }
    Scenario("works for moderators") {
      when(
        operationOfQuestionService.search(
          eqTo(
            OperationOfQuestionSearchQuery(
              filters = Some(
                OperationOfQuestionSearchFilters(
                  questionIds = Some(QuestionIdsSearchFilter(Nil)),
                  operationKinds = Some(OperationKindsSearchFilter(OperationKind.values))
                )
              ),
              sort = Some(OperationOfQuestionElasticsearchFieldName.slug)
            )
          )
        )
      ).thenReturn(
        Future.successful(
          OperationOfQuestionSearchResult(
            1,
            Seq(indexedOperationOfQuestion(QuestionId("foo"), OperationId("bar"), slug = "foobar"))
          )
        )
      )
      Get("/moderation/operations-of-questions/search?_sort=slug")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        header("x-total-count").map(_.value) should be(Some("1"))
        val results = entityAs[Seq[ModerationOperationOfQuestionSearchResult]]
        results.size should be(1)
        results.head.id.value should be("foo")
        results.head.slug should be("foobar")

      }
    }
    Scenario("works for admins") {
      when(
        operationOfQuestionService.search(
          eqTo(
            OperationOfQuestionSearchQuery(
              filters = Some(
                OperationOfQuestionSearchFilters(operationKinds = Some(OperationKindsSearchFilter(OperationKind.values))
                )
              ),
              sort = Some(OperationOfQuestionElasticsearchFieldName.slug)
            )
          )
        )
      ).thenReturn(
        Future.successful(
          OperationOfQuestionSearchResult(
            1,
            Seq(indexedOperationOfQuestion(QuestionId("baz"), OperationId("buz"), slug = "bazbuz"))
          )
        )
      )

      // TODO questions ACL assume that superadmins have admin role too, we should abstract these ACL better
      // in the meantime, grant admin role to superadmin for this test
      val superAdminToken = Await.result(oauth2DataHandler.findAccessToken(tokenSuperAdmin), 3.seconds).get
      when(oauth2DataHandler.findAuthInfoByAccessToken(eqTo(superAdminToken))).thenReturn(
        Future.successful(
          Some(
            AuthInfo(
              UserRights(
                userId = defaultSuperAdminUser.userId,
                roles = defaultSuperAdminUser.roles :+ Role.RoleAdmin,
                availableQuestions = defaultSuperAdminUser.availableQuestions,
                emailVerified = defaultSuperAdminUser.emailVerified
              ),
              None,
              None,
              None
            )
          )
        )
      )

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/moderation/operations-of-questions/search?_sort=slug")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          header("x-total-count").map(_.value) should be(Some("1"))
          val results = entityAs[Seq[ModerationOperationOfQuestionSearchResult]]
          results.size should be(1)
          results.head.id.value should be("baz")
          results.head.slug should be("bazbuz")
        }
      }
    }
  }

}
