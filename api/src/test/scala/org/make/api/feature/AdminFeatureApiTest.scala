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

import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.core.auth.UserRights
import org.make.core.feature.{Feature, FeatureId}
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.mockito.{ArgumentMatchers, Mockito}
import org.mockito.ArgumentMatchers.{eq => matches}
import org.mockito.Mockito.when
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class AdminFeatureApiTest extends MakeApiTestBase with DefaultAdminFeatureApiComponent with FeatureServiceComponent {

  override val featureService: FeatureService = mock[FeatureService]

  val validCitizenAccessToken = "my-valid-citizen-access-token"
  val validModeratorAccessToken = "my-valid-moderator-access-token"
  val validAdminAccessToken = "my-valid-admin-access-token"

  val tokenCreationDate = new Date()
  private val citizenAccessToken =
    AccessToken(validCitizenAccessToken, None, Some("user"), Some(1234567890L), tokenCreationDate)
  private val moderatorAccessToken =
    AccessToken(validModeratorAccessToken, None, Some("user"), Some(1234567890L), tokenCreationDate)
  private val adminAccessToken =
    AccessToken(validAdminAccessToken, None, Some("user"), Some(1234567890L), tokenCreationDate)

  when(oauth2DataHandler.findAccessToken(validCitizenAccessToken))
    .thenReturn(Future.successful(Some(citizenAccessToken)))
  when(oauth2DataHandler.findAccessToken(validModeratorAccessToken))
    .thenReturn(Future.successful(Some(moderatorAccessToken)))
  when(oauth2DataHandler.findAccessToken(validAdminAccessToken))
    .thenReturn(Future.successful(Some(adminAccessToken)))

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(citizenAccessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(
              userId = UserId("my-citizen-user-id"),
              roles = Seq(RoleCitizen),
              availableQuestions = Seq.empty,
              emailVerified = true
            ),
            None,
            Some("citizen"),
            None
          )
        )
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(
              userId = UserId("my-moderator-user-id"),
              roles = Seq(RoleModerator),
              availableQuestions = Seq.empty,
              emailVerified = true
            ),
            None,
            Some("moderator"),
            None
          )
        )
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future
        .successful(
          Some(
            AuthInfo(
              UserRights(
                userId = UserId("my-admin-user-id"),
                roles = Seq(RoleAdmin),
                availableQuestions = Seq.empty,
                emailVerified = true
              ),
              None,
              Some("admin"),
              None
            )
          )
        )
    )

  val routes: Route = sealRoute(adminFeatureApi.routes)

  feature("create a feature") {
    val validFeature = Feature(FeatureId("valid-feature"), "valid Feature", "valid-feature")

    when(featureService.createFeature(ArgumentMatchers.eq(validFeature.slug), ArgumentMatchers.eq(validFeature.name)))
      .thenReturn(Future.successful(validFeature))

    scenario("unauthorize unauthenticated") {
      Post("/admin/features").withEntity(
        HttpEntity(ContentTypes.`application/json`, """{"name": "Valid Feature", "slug": "valid-feature"}""")
      ) ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("forbid authenticated citizen") {
      Post("/admin/features")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"name": "Valid Feature", "slug": "valid-feature"}""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("forbid authenticated moderator") {
      Post("/admin/features")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"name": "Valid Feature", "slug": "valid-feature"}""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("allow authenticated moderator") {

      Mockito
        .when(featureService.findBySlug(ArgumentMatchers.eq("valid-feature")))
        .thenReturn(Future.successful(Seq.empty))

      Mockito
        .when(featureService.createFeature(ArgumentMatchers.eq("valid-feature"), ArgumentMatchers.eq("Valid Feature")))
        .thenReturn(Future.successful(Feature(FeatureId("featured-id"), "Valid Feature", "valid-feature")))

      Post("/admin/features")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"name": "Valid Feature", "slug": "valid-feature"}""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }
  }

  feature("read a feature") {
    val helloFeature = Feature(FeatureId("hello-feature"), "Hello Feature", "hello-feature")

    when(featureService.getFeature(ArgumentMatchers.eq(helloFeature.featureId)))
      .thenReturn(Future.successful(Some(helloFeature)))
    when(featureService.getFeature(ArgumentMatchers.eq(FeatureId("fake-feature"))))
      .thenReturn(Future.successful(None))

    scenario("unauthorize unauthenticated") {
      Get("/admin/features/hello-feature") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("forbid authenticated citizen") {
      Get("/admin/features/hello-feature")
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("forbid authenticated moderator") {
      Get("/admin/features/hello-feature")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("allow authenticated moderator on existing feature") {
      Get("/admin/features/hello-feature")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val feature: FeatureResponse = entityAs[FeatureResponse]
        feature.id should be(helloFeature.featureId)
        feature.slug should be(helloFeature.slug)
        feature.name should be(helloFeature.name)
      }
    }

    scenario("not found and allow authenticated admin on a non existing feature") {
      Get("/admin/features/fake-feature")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }

  feature("update a feature") {
    val helloFeature = Feature(FeatureId("hello-feature"), "hello feature", "hello-feature")
    val newHelloFeature = Feature(FeatureId("hello-feature"), "new name", "new-slug")
    val sameSlugFeature = Feature(FeatureId("same-slug"), "same slug", "same-slug")

    when(featureService.findBySlug(ArgumentMatchers.eq(newHelloFeature.slug)))
      .thenReturn(Future.successful(Seq.empty))
    when(featureService.findBySlug(ArgumentMatchers.eq(helloFeature.slug)))
      .thenReturn(Future.successful(Seq(helloFeature)))
    when(featureService.findBySlug(ArgumentMatchers.eq(sameSlugFeature.slug)))
      .thenReturn(Future.successful(Seq(sameSlugFeature)))
    when(
      featureService.updateFeature(
        ArgumentMatchers.eq(FeatureId("fake-feature")),
        ArgumentMatchers.any[String],
        ArgumentMatchers.any[String]
      )
    ).thenReturn(Future.successful(None))
    when(
      featureService.updateFeature(
        ArgumentMatchers.eq(helloFeature.featureId),
        ArgumentMatchers.eq("new-slug"),
        ArgumentMatchers.eq("new name")
      )
    ).thenReturn(Future.successful(Some(newHelloFeature)))
    when(
      featureService.updateFeature(
        ArgumentMatchers.eq(sameSlugFeature.featureId),
        ArgumentMatchers.eq("same-slug"),
        ArgumentMatchers.eq("new name")
      )
    ).thenReturn(Future.successful(Some(sameSlugFeature)))

    scenario("unauthorize unauthenticated") {
      Put("/admin/features/hello-feature").withEntity(
        HttpEntity(ContentTypes.`application/json`, """{"slug": "new-slug", "name": "new name"}""")
      ) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("forbid authenticated citizen") {
      Put("/admin/features/hello-feature")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"slug": "new-slug", "name": "new name"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("forbid authenticated moderator") {
      Put("/admin/features/hello-feature")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"slug": "new-slug", "name": "new name"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("allow authenticated admin on existing feature") {
      Put("/admin/features/hello-feature")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"slug": "new-slug", "name": "new name"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val feature: FeatureResponse = entityAs[FeatureResponse]
        feature.id should be(newHelloFeature.featureId)
        feature.slug should be(newHelloFeature.slug)
        feature.name should be(newHelloFeature.name)
      }
    }

    scenario("not found and allow authenticated admin on a non existing feature") {
      Put("/admin/features/fake-feature")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"slug": "new-slug", "name": "new name"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("slug already exists") {
      Put("/admin/features/same-slug")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"slug": "hello-feature", "name": "new name"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("changing only the name") {
      Put("/admin/features/same-slug")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"slug": "same-slug", "name": "new name"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  feature("delete a feature") {
    val helloFeature = Feature(FeatureId("hello-feature"), "Hello Feature", "hello-feature")

    when(featureService.getFeature(ArgumentMatchers.eq(helloFeature.featureId)))
      .thenReturn(Future.successful(Some(helloFeature)))
    when(featureService.getFeature(ArgumentMatchers.eq(FeatureId("fake-feature"))))
      .thenReturn(Future.successful(None))
    when(featureService.deleteFeature(ArgumentMatchers.eq(helloFeature.featureId)))
      .thenReturn(Future.successful({}))

    scenario("unauthorize unauthenticated") {
      Delete("/admin/features/hello-feature") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("forbid authenticated citizen") {
      Delete("/admin/features/hello-feature")
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("forbid authenticated moderator") {
      Delete("/admin/features/hello-feature")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("allow authenticated moderator on existing feature") {
      Delete("/admin/features/hello-feature")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("not found and allow authenticated admin on a non existing feature") {
      Get("/admin/features/fake-feature")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }
}
