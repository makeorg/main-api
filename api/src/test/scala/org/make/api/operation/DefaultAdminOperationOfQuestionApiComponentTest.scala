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

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import eu.timepit.refined.auto._
import io.circe.syntax._
import org.make.api.MakeApiTestBase
import org.make.api.keyword.{KeywordService, KeywordServiceComponent}
import org.make.core.ValidationError
import org.make.core.keyword.Keyword
import org.make.core.operation._
import org.make.core.question.QuestionId

import scala.concurrent.Future

class DefaultAdminOperationOfQuestionApiComponentTest
    extends MakeApiTestBase
    with DefaultAdminOperationOfQuestionApiComponent
    with OperationOfQuestionServiceComponent
    with KeywordServiceComponent {

  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]
  override val keywordService: KeywordService = mock[KeywordService]

  when(operationOfQuestionService.findByQuestionId(any[QuestionId])).thenAnswer { questionId: QuestionId =>
    Future.successful(Some(operationOfQuestion(questionId = questionId, operationId = OperationId("some-operation"))))
  }

  val routes: Route = sealRoute(adminOperationOfQuestionApi.routes)

  Feature("update highlights") {
    when(operationOfQuestionService.update(any[OperationOfQuestion])).thenAnswer { ooq: OperationOfQuestion =>
      Future.successful(ooq)
    }

    Scenario("unauthenticated user") {
      Put("/admin/questions/some-question/highlights") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("invalid token") {
      Put("/admin/questions/some-question/highlights")
        .withHeaders(Authorization(OAuth2BearerToken("invalid-token"))) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("citizen user") {
      Put("/admin/questions/some-question/highlights")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("moderator user") {
      Put("/admin/questions/some-question/highlights")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("update highlights as admin") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/questions/some-question/highlights")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(
            ContentTypes.`application/json`,
            UpdateHighlights(proposalsCount = 4200, participantsCount = 8400, votesCount = 45000).asJson.toString()
          ) ~> routes ~> check {

          status should be(StatusCodes.NoContent)
        }
      }
    }

    Scenario("update with negative int") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/questions/some-question/highlights")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(ContentTypes.`application/json`, """{
           | "proposalsCount": -84,
           | "participantsCount": 1000,
           | "votesCount": 1000000
           |}""".stripMargin) ~> routes ~> check {

          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size should be(1)
          errors.head.field shouldBe "proposalsCount"
        }
      }
    }
  }

  Feature("keywords") {
    when(keywordService.addAndReplaceTop(any[QuestionId], any[Seq[Keyword]])).thenReturn(Future.unit)

    Scenario("unauthenticated user") {
      Put("/admin/questions/some-question/keywords") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("invalid token") {
      Put("/admin/questions/some-question/keywords")
        .withHeaders(Authorization(OAuth2BearerToken("invalid-token"))) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("citizen user") {
      Put("/admin/questions/some-question/keywords")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("moderator user") {
      Put("/admin/questions/some-question/keywords")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("update keywords as admin") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/questions/some-question/keywords")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(
            ContentTypes.`application/json`,
            UpdateKeywords(Seq(KeywordRequest("key", "label", 0.42f, 14))).asJson.toString()
          ) ~> routes ~> check {
          status should be(StatusCodes.NoContent)
        }
      }
    }

    Scenario("duplicate keywords") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/questions/some-question/keywords")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(
            ContentTypes.`application/json`,
            UpdateKeywords(
              Seq(
                KeywordRequest("key", "label1", 0.42f, 14),
                KeywordRequest("key", "label2", 0.2f, 4),
                KeywordRequest("other-key", "label3", 0.2f, 4)
              )
            ).asJson.toString()
          ) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size should be(1)
          errors.head.message shouldBe Some("keywords contain duplicate keys: key")
        }
      }
    }
  }
}
