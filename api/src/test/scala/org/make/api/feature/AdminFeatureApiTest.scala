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
import org.make.core.technical.Pagination.Start

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

  when(featureService.getFeature(eqTo(FeatureId("fake-feature"))))
    .thenReturn(Future.successful(None))

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
    val helloActiveFeatures = Seq(
      ActiveFeature(ActiveFeatureId("hello-active-feature"), helloFeature.featureId, Some(QuestionId("hello-q-1"))),
      ActiveFeature(ActiveFeatureId("hello-active-feature-2"), helloFeature.featureId, Some(QuestionId("hello-q-2"))),
      ActiveFeature(ActiveFeatureId("hello-active-feature-3"), helloFeature.featureId, None)
    )
    val questions = Seq(question(QuestionId("hello-q-1")), question(QuestionId("hello-q-2")))

    when(featureService.getFeature(eqTo(helloFeature.featureId)))
      .thenReturn(Future.successful(Some(helloFeature)))

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
      when(
        activeFeatureService.find(
          start = eqTo(Start.zero),
          end = eqTo(None),
          sort = eqTo(None),
          order = eqTo(None),
          maybeQuestionId = eqTo(None),
          featureIds = eqTo(Some(Seq(helloFeature.featureId)))
        )
      ).thenReturn(Future.successful(helloActiveFeatures))
      when(questionService.searchQuestion(eqTo(SearchQuestionRequest(Some(questions.map(_.questionId))))))
        .thenReturn(Future.successful(questions))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {

        Get("/admin/features/hello-feature")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val feature: FeatureResponse = entityAs[FeatureResponse]
          feature.id should be(helloFeature.featureId)
          feature.slug should be(helloFeature.slug)
          feature.name should be(helloFeature.name)
          feature.questions.map(_.id).toSet should be(Set(QuestionId("hello-q-1"), QuestionId("hello-q-2")))
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

  Feature("read features") {
    val f1 = Feat(FeatureId("feature-1"), "Feature one", "f-1")
    val f2 = Feat(FeatureId("feature-2"), "Feature one", "f-2")
    val q1 = question(QuestionId("read-q-id"))
    val a1 = ActiveFeature(ActiveFeatureId("active-feature-1"), f1.featureId, None)
    val a2 = ActiveFeature(ActiveFeatureId("active-feature-2"), f1.featureId, Some(q1.questionId))

    when(featureService.count(eqTo(Some(f1.slug))))
      .thenReturn(Future.successful(1))
    when(
      featureService.find(
        start = eqTo(Pagination.Start.zero),
        end = eqTo(None),
        sort = eqTo(None),
        order = eqTo(None),
        slug = eqTo(Some(f1.slug))
      )
    ).thenReturn(Future.successful(Seq(f1)))

    when(featureService.count(eqTo(None)))
      .thenReturn(Future.successful(2))
    when(featureService.find(eqTo(Start.zero), eqTo(None), eqTo(None), eqTo(None), eqTo(None)))
      .thenReturn(Future.successful(Seq(f1, f2)))

    when(
      activeFeatureService
        .find(eqTo(Start.zero), eqTo(None), eqTo(None), eqTo(None), eqTo(None), argThat[Option[Seq[FeatureId]]] {
          _.forall(_.contains(f1.featureId))
        })
    ).thenReturn(Future.successful(Seq(a1, a2)))
    when(questionService.searchQuestion(eqTo(SearchQuestionRequest(Some(Seq(q1.questionId))))))
      .thenReturn(Future.successful(Seq(q1)))
    when(questionService.searchQuestion(eqTo(SearchQuestionRequest(Some(Seq.empty)))))
      .thenReturn(Future.successful(Seq.empty))

    Scenario("unauthorize unauthenticated") {
      Get("/admin/features?slug=f-1") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Get("/admin/features?slug=f-1")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Get("/admin/features?slug=f-1")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin on existing feature") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {

        Get("/admin/features?slug=f-1")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val features: Seq[FeatureResponse] = entityAs[Seq[FeatureResponse]]
          features.size shouldBe 1
          features.head.id shouldBe f1.featureId
          features.head.questions.map(_.id).toSet shouldBe Set(q1.questionId)
        }

        Get("/admin/features")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val features: Seq[FeatureResponse] = entityAs[Seq[FeatureResponse]]
          features.size shouldBe 2
          features.map(_.id).toSet shouldBe Set(f1.featureId, f2.featureId)
          features.groupMapReduce(_.id)(_.questions.map(_.id))(_ ++ _) shouldBe Map(
            f1.featureId -> Seq(q1.questionId),
            f2.featureId -> Seq.empty
          )
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
    val updateFeature = Feat(FeatureId("update-feature"), "update feature", "update-feature")
    val newUpdateFeature = Feat(FeatureId("update-feature"), "new name", "new-slug")
    val sameSlugFeature = Feat(FeatureId("same-slug"), "same slug", "same-slug")

    when(featureService.findBySlug(eqTo(newUpdateFeature.slug)))
      .thenReturn(Future.successful(Seq.empty))
    when(featureService.findBySlug(eqTo(updateFeature.slug)))
      .thenReturn(Future.successful(Seq(updateFeature)))
    when(featureService.findBySlug(eqTo(sameSlugFeature.slug)))
      .thenReturn(Future.successful(Seq(sameSlugFeature)))
    when(featureService.updateFeature(eqTo(FeatureId("fake-feature")), any[String], any[String]))
      .thenReturn(Future.successful(None))
    when(featureService.updateFeature(eqTo(updateFeature.featureId), eqTo("new-slug"), eqTo("new name")))
      .thenReturn(Future.successful(Some(newUpdateFeature)))
    when(featureService.updateFeature(eqTo(sameSlugFeature.featureId), eqTo("same-slug"), eqTo("new name")))
      .thenReturn(Future.successful(Some(sameSlugFeature)))
    when(
      activeFeatureService.find(
        any[Pagination.Start],
        any[Option[Pagination.End]],
        any[Option[String]],
        any[Option[Order]],
        eqTo(None),
        eqTo(Some(Seq(sameSlugFeature.featureId)))
      )
    ).thenReturn(Future.successful(Seq.empty))
    when(
      activeFeatureService.find(
        any[Pagination.Start],
        any[Option[Pagination.End]],
        any[Option[String]],
        any[Option[Order]],
        eqTo(None),
        argThat[Option[Seq[FeatureId]]] {
          _.forall(_.contains(updateFeature.featureId))
        }
      )
    ).thenReturn(
      Future.successful(
        Seq(
          ActiveFeature(ActiveFeatureId("active-feature-1"), updateFeature.featureId, Some(QuestionId("update-q-id"))),
          ActiveFeature(
            ActiveFeatureId("active-feature-2"),
            FeatureId("other-feature"),
            Some(QuestionId("update-q-id"))
          ),
          ActiveFeature(
            ActiveFeatureId("active-feature-3"),
            updateFeature.featureId,
            Some(QuestionId("update-q-id-2"))
          ),
          ActiveFeature(ActiveFeatureId("active-feature-4"), updateFeature.featureId, None)
        )
      )
    )
    when(
      questionService
        .searchQuestion(eqTo(SearchQuestionRequest(Some(Seq(QuestionId("update-q-id"), QuestionId("update-q-id-2"))))))
    ).thenReturn(Future.successful(Seq(question(QuestionId("update-q-id")), question(QuestionId("update-q-id-2")))))

    Scenario("unauthorize unauthenticated") {
      Put("/admin/features/update-feature").withEntity(
        HttpEntity(ContentTypes.`application/json`, """{"slug": "new-slug", "name": "new name"}""")
      ) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Put("/admin/features/update-feature")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"slug": "new-slug", "name": "new name"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Put("/admin/features/update-feature")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"slug": "new-slug", "name": "new name"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin on existing feature") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/features/update-feature")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{"slug": "new-slug", "name": "new name"}"""))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val feature: FeatureResponse = entityAs[FeatureResponse]
          feature.id should be(newUpdateFeature.featureId)
          feature.slug should be(newUpdateFeature.slug)
          feature.name should be(newUpdateFeature.name)
          feature.questions.map(_.id).toSet should be(Set(QuestionId("update-q-id"), QuestionId("update-q-id-2")))
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
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{"slug": "update-feature", "name": "new name"}"""))
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
    val deleteFeature = Feat(FeatureId("delete-feature"), "Delete Feature", "delete-feature")

    when(featureService.getFeature(eqTo(deleteFeature.featureId)))
      .thenReturn(Future.successful(Some(deleteFeature)))
    when(featureService.deleteFeature(eqTo(deleteFeature.featureId)))
      .thenReturn(Future.unit)

    Scenario("unauthorize unauthenticated") {
      Delete("/admin/features/delete-feature") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Delete("/admin/features/delete-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Delete("/admin/features/delete-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated moderator on existing feature") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Delete("/admin/features/delete-feature")
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
