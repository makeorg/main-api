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
import org.make.api.MakeApiTestBase
import org.make.api.question.{QuestionService, QuestionServiceComponent, SearchQuestionRequest}
import org.make.core.Order
import org.make.core.feature.{ActiveFeature, ActiveFeatureId, FeatureId, Feature => Feat}
import org.make.core.question.QuestionId
import org.make.core.technical.Pagination

import scala.concurrent.Future

class AdminFeatureApiTest
    extends MakeApiTestBase
    with DefaultAdminFeatureApiComponent
    with FeatureServiceComponent
    with ActiveFeatureServiceComponent
    with QuestionServiceComponent {

  override val featureService: FeatureService = mock[FeatureService]
  override val activeFeatureService: ActiveFeatureService = mock[ActiveFeatureService]
  override val questionService: QuestionService = mock[QuestionService]

  val routes: Route = sealRoute(adminFeatureApi.routes)

  Feature("create a feature") {
    val validFeature = Feat(FeatureId("valid-feature"), "valid Feature", "valid-feature")

    when(featureService.createFeature(eqTo(validFeature.slug), eqTo(validFeature.name)))
      .thenReturn(Future.successful(validFeature))

    Scenario("unauthorize unauthenticated") {
      Post("/admin/features").withEntity(
        HttpEntity(ContentTypes.`application/json`, """{"name": "Valid Feature", "slug": "valid-feature"}""")
      ) ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Post("/admin/features")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"name": "Valid Feature", "slug": "valid-feature"}""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Post("/admin/features")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"name": "Valid Feature", "slug": "valid-feature"}""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated moderator") {

      when(featureService.findBySlug(eqTo("valid-feature")))
        .thenReturn(Future.successful(Seq.empty))

      when(featureService.createFeature(eqTo("valid-feature"), eqTo("Valid Feature")))
        .thenReturn(Future.successful(Feat(FeatureId("featured-id"), "Valid Feature", "valid-feature")))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/features")
          .withEntity(
            HttpEntity(ContentTypes.`application/json`, """{"name": "Valid Feature", "slug": "valid-feature"}""")
          )
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.Created)
        }
      }
    }
  }

  Feature("read a feature") {
    val helloFeature = Feat(FeatureId("hello-feature"), "Hello Feature", "hello-feature")

    when(featureService.getFeature(eqTo(helloFeature.featureId)))
      .thenReturn(Future.successful(Some(helloFeature)))
    when(featureService.getFeature(eqTo(FeatureId("fake-feature"))))
      .thenReturn(Future.successful(None))

    Scenario("unauthorize unauthenticated") {
      Get("/admin/features/hello-feature") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Get("/admin/features/hello-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Get("/admin/features/hello-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin on existing feature") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {

        Get("/admin/features/hello-feature")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val feature: FeatureResponse = entityAs[FeatureResponse]
          feature.id should be(helloFeature.featureId)
          feature.slug should be(helloFeature.slug)
          feature.name should be(helloFeature.name)
          feature.questions.map(_.id).toSet should be(Set(QuestionId("question-id"), QuestionId("question-id-2")))
        }
      }
    }

    Scenario("not found and allow authenticated admin on a non existing feature") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {

        Get("/admin/features/fake-feature")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }
  }

  Feature("update a feature") {
    val helloFeature = Feat(FeatureId("hello-feature"), "hello feature", "hello-feature")
    val newHelloFeature = Feat(FeatureId("hello-feature"), "new name", "new-slug")
    val sameSlugFeature = Feat(FeatureId("same-slug"), "same slug", "same-slug")

    when(featureService.findBySlug(eqTo(newHelloFeature.slug)))
      .thenReturn(Future.successful(Seq.empty))
    when(featureService.findBySlug(eqTo(helloFeature.slug)))
      .thenReturn(Future.successful(Seq(helloFeature)))
    when(featureService.findBySlug(eqTo(sameSlugFeature.slug)))
      .thenReturn(Future.successful(Seq(sameSlugFeature)))
    when(featureService.updateFeature(eqTo(FeatureId("fake-feature")), any[String], any[String]))
      .thenReturn(Future.successful(None))
    when(featureService.updateFeature(eqTo(helloFeature.featureId), eqTo("new-slug"), eqTo("new name")))
      .thenReturn(Future.successful(Some(newHelloFeature)))
    when(featureService.updateFeature(eqTo(sameSlugFeature.featureId), eqTo("same-slug"), eqTo("new name")))
      .thenReturn(Future.successful(Some(sameSlugFeature)))
    when(
      activeFeatureService.find(
        any[Pagination.Start],
        any[Option[Pagination.End]],
        any[Option[String]],
        any[Option[Order]],
        any[Option[Seq[QuestionId]]],
        any[Option[Seq[FeatureId]]]
      )
    ).thenReturn(
      Future.successful(
        Seq(
          ActiveFeature(ActiveFeatureId("active-feature-1"), FeatureId("feature-id"), Some(QuestionId("question-id"))),
          ActiveFeature(
            ActiveFeatureId("active-feature-2"),
            FeatureId("feature-id-2"),
            Some(QuestionId("question-id"))
          ),
          ActiveFeature(
            ActiveFeatureId("active-feature-3"),
            FeatureId("feature-id"),
            Some(QuestionId("question-id-2"))
          ),
          ActiveFeature(ActiveFeatureId("active-feature-4"), FeatureId("feature-id"), None)
        )
      )
    )
    when(
      questionService
        .searchQuestion(eqTo(SearchQuestionRequest(Some(Seq(QuestionId("question-id"), QuestionId("question-id-2"))))))
    ).thenReturn(Future.successful(Seq(question(QuestionId("question-id")), question(QuestionId("question-id-2")))))

    Scenario("unauthorize unauthenticated") {
      Put("/admin/features/hello-feature").withEntity(
        HttpEntity(ContentTypes.`application/json`, """{"slug": "new-slug", "name": "new name"}""")
      ) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Put("/admin/features/hello-feature")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"slug": "new-slug", "name": "new name"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Put("/admin/features/hello-feature")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"slug": "new-slug", "name": "new name"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin on existing feature") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/features/hello-feature")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{"slug": "new-slug", "name": "new name"}"""))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val feature: FeatureResponse = entityAs[FeatureResponse]
          feature.id should be(newHelloFeature.featureId)
          feature.slug should be(newHelloFeature.slug)
          feature.name should be(newHelloFeature.name)
          feature.questions.map(_.id).toSet should be(Set(QuestionId("question-id"), QuestionId("question-id-2")))
        }
      }
    }

    Scenario("not found and allow authenticated admin on a non existing feature") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/features/fake-feature")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{"slug": "new-slug", "name": "new name"}"""))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }

    Scenario("slug already exists") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/features/same-slug")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{"slug": "hello-feature", "name": "new name"}"""))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
        }
      }
    }

    Scenario("changing only the name") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/features/same-slug")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{"slug": "same-slug", "name": "new name"}"""))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
        }
      }
    }
  }

  Feature("delete a feature") {
    val helloFeature = Feat(FeatureId("hello-feature"), "Hello Feature", "hello-feature")

    when(featureService.getFeature(eqTo(helloFeature.featureId)))
      .thenReturn(Future.successful(Some(helloFeature)))
    when(featureService.getFeature(eqTo(FeatureId("fake-feature"))))
      .thenReturn(Future.successful(None))
    when(featureService.deleteFeature(eqTo(helloFeature.featureId)))
      .thenReturn(Future.unit)

    Scenario("unauthorize unauthenticated") {
      Delete("/admin/features/hello-feature") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Delete("/admin/features/hello-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Delete("/admin/features/hello-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated moderator on existing feature") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Delete("/admin/features/hello-feature")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
        }
      }
    }

    Scenario("not found and allow authenticated admin on a non existing feature") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/features/fake-feature")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }
  }
}
