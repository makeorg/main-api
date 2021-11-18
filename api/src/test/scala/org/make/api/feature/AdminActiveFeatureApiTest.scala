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

package org.make.api.feature

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.core.ValidationError
import org.make.core.feature.{ActiveFeature, ActiveFeatureId, FeatureId, FeatureSlug, Feature => Feat}
import org.make.core.question.QuestionId
import org.make.core.technical.Pagination.Start

import scala.concurrent.Future

class AdminActiveFeatureApiTest
    extends MakeApiTestBase
    with DefaultAdminActiveFeatureApiComponent
    with ActiveFeatureServiceComponent
    with FeatureServiceComponent
    with QuestionServiceComponent {

  override val activeFeatureService: ActiveFeatureService = mock[ActiveFeatureService]
  override val questionService: QuestionService = mock[QuestionService]
  override val featureService: FeatureService = mock[FeatureService]

  val routes: Route = sealRoute(adminActiveFeatureApi.routes)

  Feature("create an activeFeature") {
    val f1 = Feat(FeatureId("feature-create"), "name", FeatureSlug("slug"))
    val q1 = TestUtils.question(QuestionId("question-create"))
    val validActiveFeature = ActiveFeature(ActiveFeatureId("valid-active-feature"), f1.featureId, Some(q1.questionId))

    when(featureService.getFeature(eqTo(f1.featureId))).thenReturn(Future.successful(Some(f1)))
    when(featureService.getFeature(eqTo(FeatureId("fake")))).thenReturn(Future.successful(None))
    when(questionService.getQuestion(q1.questionId)).thenReturn(Future.successful(Some(q1)))
    when(questionService.getQuestion(QuestionId("fake"))).thenReturn(Future.successful(None))
    when(
      activeFeatureService
        .find(
          eqTo(Start.zero),
          eqTo(None),
          eqTo(None),
          eqTo(None),
          eqTo(Some(Seq(q1.questionId))),
          eqTo(Some(Seq(f1.featureId)))
        )
    ).thenReturn(Future.successful(Seq.empty))
    when(
      activeFeatureService
        .find(
          eqTo(Start.zero),
          eqTo(None),
          eqTo(None),
          eqTo(None),
          eqTo(Some(Seq(QuestionId("fake")))),
          eqTo(Some(Seq(f1.featureId)))
        )
    ).thenReturn(Future.successful(Seq.empty))

    when(
      activeFeatureService
        .createActiveFeature(any[FeatureId], any[Option[QuestionId]])
    ).thenReturn(Future.successful(validActiveFeature))

    Scenario("unauthorize unauthenticated") {
      Post("/admin/active-features").withEntity(
        HttpEntity(
          ContentTypes.`application/json`,
          """{"featureId": "feature-create", "maybeQuestionId": "question-create"}"""
        )
      ) ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Post("/admin/active-features")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{"featureId": "feature-create", "maybeQuestionId": "question-create"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Post("/admin/active-features")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{"featureId": "feature-create", "maybeQuestionId": "question-create"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/active-features")
          .withEntity(
            HttpEntity(
              ContentTypes.`application/json`,
              """{"featureId": "feature-create", "maybeQuestionId": "question-create"}"""
            )
          )
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.Created)
        }
      }
    }

    Scenario("feature does not exist") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/active-features")
          .withEntity(
            HttpEntity(
              ContentTypes.`application/json`,
              """{"featureId": "fake", "maybeQuestionId": "question-create"}"""
            )
          )
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size shouldBe 1
          errors.map(_.field) should contain("featureId")
        }
      }
    }

    Scenario("question does not exist") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/active-features")
          .withEntity(
            HttpEntity(
              ContentTypes.`application/json`,
              """{"featureId": "feature-create", "maybeQuestionId": "fake"}"""
            )
          )
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size shouldBe 1
          errors.map(_.field) should contain("questionId")
        }
      }
    }

    Scenario("active feature already exists") {
      when(featureService.getFeature(eqTo(FeatureId("feature-pair")))).thenReturn(Future.successful(Some(f1)))
      when(questionService.getQuestion(QuestionId("question-pair"))).thenReturn(Future.successful(Some(q1)))
      when(
        activeFeatureService
          .find(
            eqTo(Start.zero),
            eqTo(None),
            eqTo(None),
            eqTo(None),
            eqTo(Some(Seq(QuestionId("question-pair")))),
            eqTo(Some(Seq(FeatureId("feature-pair"))))
          )
      ).thenReturn(
        Future.successful(
          Seq(ActiveFeature(ActiveFeatureId("active-1"), FeatureId("feature-pair"), Some(QuestionId("question-pair"))))
        )
      )

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/active-features")
          .withEntity(
            HttpEntity(
              ContentTypes.`application/json`,
              """{"featureId": "feature-pair", "maybeQuestionId": "question-pair"}"""
            )
          )
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size shouldBe 1
          errors.map(_.key) should contain("non_empty")
        }
      }
    }
  }

  Feature("read a activeFeature") {
    val helloActiveFeature =
      ActiveFeature(ActiveFeatureId("hello-active-feature"), FeatureId("feature"), Some(QuestionId("question")))

    when(activeFeatureService.getActiveFeature(eqTo(helloActiveFeature.activeFeatureId)))
      .thenReturn(Future.successful(Some(helloActiveFeature)))
    when(activeFeatureService.getActiveFeature(eqTo(ActiveFeatureId("fake-active-feature"))))
      .thenReturn(Future.successful(None))

    Scenario("unauthorize unauthenticated") {
      Get("/admin/active-features/hello-active-feature") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Get("/admin/active-features/hello-active-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Get("/admin/active-features/hello-active-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin on existing activeFeature") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/active-features/hello-active-feature")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val activeFeature: ActiveFeatureResponse = entityAs[ActiveFeatureResponse]
          activeFeature.id should be(helloActiveFeature.activeFeatureId)
          activeFeature.featureId should be(helloActiveFeature.featureId)
          activeFeature.maybeQuestionId should be(helloActiveFeature.maybeQuestionId)
        }
      }
    }

    Scenario("not found and allow authenticated admin on a non existing activeFeature") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/active-features/fake-active-feature")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }
  }

  Feature("delete a activeFeature") {
    val helloActiveFeature =
      ActiveFeature(ActiveFeatureId("hello-active-feature"), FeatureId("feature"), Some(QuestionId("question")))

    when(activeFeatureService.getActiveFeature(eqTo(helloActiveFeature.activeFeatureId)))
      .thenReturn(Future.successful(Some(helloActiveFeature)))
    when(activeFeatureService.getActiveFeature(eqTo(ActiveFeatureId("fake-active-feature"))))
      .thenReturn(Future.successful(None))
    when(activeFeatureService.deleteActiveFeature(eqTo(helloActiveFeature.activeFeatureId)))
      .thenReturn(Future.unit)

    Scenario("unauthorize unauthenticated") {
      Delete("/admin/active-features/hello-active-feature") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Delete("/admin/active-features/hello-active-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Delete("/admin/active-features/hello-active-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin on existing activeFeature") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Delete("/admin/active-features/hello-active-feature")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NoContent)
        }
      }
    }

    Scenario("not found and allow authenticated admin on a non existing activeFeature") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Delete("/admin/active-features/fake-active-feature")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }
  }

  Feature("delete activeFeature from featureId and questionId") {
    val helloActiveFeature =
      ActiveFeature(ActiveFeatureId("hello-active-feature"), FeatureId("feature"), Some(QuestionId("question")))

    when(
      activeFeatureService.find(
        eqTo(Start.zero),
        eqTo(None),
        eqTo(None),
        eqTo(None),
        eqTo(helloActiveFeature.maybeQuestionId.map(qId => Seq(qId))),
        eqTo(Some(Seq(helloActiveFeature.featureId)))
      )
    ).thenReturn(Future.successful(Seq(helloActiveFeature)))
    when(
      activeFeatureService.find(
        eqTo(Start.zero),
        eqTo(None),
        eqTo(None),
        eqTo(None),
        eqTo(Some(Seq(QuestionId("fake")))),
        eqTo(Some(Seq(FeatureId("fake"))))
      )
    ).thenReturn(Future.successful(Seq.empty))
    when(activeFeatureService.deleteActiveFeature(eqTo(helloActiveFeature.activeFeatureId)))
      .thenReturn(Future.unit)

    Scenario("unauthorize unauthenticated") {
      Delete("/admin/active-features")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"featureId": "feature", "maybeQuestionId": "question"}""")
        ) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Delete("/admin/active-features/hello-active-feature")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"featureId": "feature", "maybeQuestionId": "question"}""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Delete("/admin/active-features")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"featureId": "feature", "maybeQuestionId": "question"}""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin on existing activeFeature") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Delete("/admin/active-features")
          .withEntity(
            HttpEntity(ContentTypes.`application/json`, """{"featureId": "feature", "maybeQuestionId": "question"}""")
          )
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NoContent)
        }
      }
    }

    Scenario("delete only one activeFeature") {
      when(
        activeFeatureService.find(
          eqTo(Start.zero),
          eqTo(None),
          eqTo(None),
          eqTo(None),
          eqTo(None),
          eqTo(Some(Seq(helloActiveFeature.featureId)))
        )
      ).thenReturn(
        Future
          .successful(Seq(helloActiveFeature, helloActiveFeature.copy(activeFeatureId = ActiveFeatureId("other-id"))))
      )
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Delete("/admin/active-features")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{"featureId": "feature"}"""))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }

    Scenario("not found and allow authenticated admin on a non existing activeFeature") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Delete("/admin/active-features")
          .withEntity(
            HttpEntity(ContentTypes.`application/json`, """{"featureId": "fake", "maybeQuestionId": "fake"}""")
          )
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }
  }
}
