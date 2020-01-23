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
import org.make.core.feature.{ActiveFeature, ActiveFeatureId, FeatureId}
import org.make.core.question.QuestionId
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when

import scala.concurrent.Future

class AdminActiveFeatureApiTest
    extends MakeApiTestBase
    with DefaultAdminActiveFeatureApiComponent
    with ActiveFeatureServiceComponent {

  override val activeFeatureService: ActiveFeatureService = mock[ActiveFeatureService]

  val routes: Route = sealRoute(adminActiveFeatureApi.routes)

  feature("create an activeFeature") {
    val validActiveFeature =
      ActiveFeature(ActiveFeatureId("valid-active-feature"), FeatureId("feature"), Some(QuestionId("question")))

    when(
      activeFeatureService
        .createActiveFeature(ArgumentMatchers.any[FeatureId], ArgumentMatchers.any[Option[QuestionId]])
    ).thenReturn(Future.successful(validActiveFeature))

    scenario("unauthorize unauthenticated") {
      Post("/admin/active-features").withEntity(
        HttpEntity(ContentTypes.`application/json`, """{"featureId": "feature", "questionId": "question"}""")
      ) ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("forbid authenticated citizen") {
      Post("/admin/active-features")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"featureId": "feature", "questionId": "question"}""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("forbid authenticated moderator") {
      Post("/admin/active-features")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"featureId": "feature", "questionId": "question"}""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("allow authenticated admin") {

      Post("/admin/active-features")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"featureId": "feature", "questionId": "question"}""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }
  }

  feature("read a activeFeature") {
    val helloActiveFeature =
      ActiveFeature(ActiveFeatureId("hello-active-feature"), FeatureId("feature"), Some(QuestionId("question")))

    when(activeFeatureService.getActiveFeature(ArgumentMatchers.eq(helloActiveFeature.activeFeatureId)))
      .thenReturn(Future.successful(Some(helloActiveFeature)))
    when(activeFeatureService.getActiveFeature(ArgumentMatchers.eq(ActiveFeatureId("fake-active-feature"))))
      .thenReturn(Future.successful(None))

    scenario("unauthorize unauthenticated") {
      Get("/admin/active-features/hello-active-feature") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("forbid authenticated citizen") {
      Get("/admin/active-features/hello-active-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("forbid authenticated moderator") {
      Get("/admin/active-features/hello-active-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("allow authenticated admin on existing activeFeature") {
      Get("/admin/active-features/hello-active-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val activeFeature: ActiveFeatureResponse = entityAs[ActiveFeatureResponse]
        activeFeature.id should be(helloActiveFeature.activeFeatureId)
        activeFeature.featureId should be(helloActiveFeature.featureId)
        activeFeature.maybeQuestionId should be(helloActiveFeature.maybeQuestionId)
      }
    }

    scenario("not found and allow authenticated admin on a non existing activeFeature") {
      Get("/admin/active-features/fake-active-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }

  feature("delete a activeFeature") {
    val helloActiveFeature =
      ActiveFeature(ActiveFeatureId("hello-active-feature"), FeatureId("feature"), Some(QuestionId("question")))

    when(activeFeatureService.getActiveFeature(ArgumentMatchers.eq(helloActiveFeature.activeFeatureId)))
      .thenReturn(Future.successful(Some(helloActiveFeature)))
    when(activeFeatureService.getActiveFeature(ArgumentMatchers.eq(ActiveFeatureId("fake-active-feature"))))
      .thenReturn(Future.successful(None))
    when(activeFeatureService.deleteActiveFeature(ArgumentMatchers.eq(helloActiveFeature.activeFeatureId)))
      .thenReturn(Future.successful({}))

    scenario("unauthorize unauthenticated") {
      Delete("/admin/active-features/hello-active-feature") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("forbid authenticated citizen") {
      Delete("/admin/active-features/hello-active-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("forbid authenticated moderator") {
      Delete("/admin/active-features/hello-active-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("allow authenticated admin on existing activeFeature") {
      Delete("/admin/active-features/hello-active-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
    }

    scenario("not found and allow authenticated admin on a non existing activeFeature") {
      Get("/admin/active-features/fake-active-feature")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }
}
