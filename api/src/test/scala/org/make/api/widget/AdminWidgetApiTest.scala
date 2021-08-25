/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.api.widget

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import cats.data.NonEmptyList
import org.make.api.MakeApiTestBase
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.core.{DateHelper, ValidationError}
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.widget.{SourceId, Widget, WidgetId}

import scala.collection.immutable.Seq
import scala.concurrent.Future

class AdminWidgetApiTest
    extends MakeApiTestBase
    with DefaultAdminWidgetApiComponent
    with QuestionServiceComponent
    with SourceServiceComponent
    with UserServiceComponent
    with WidgetServiceComponent {

  override val questionService: QuestionService = mock[QuestionService]
  override val sourceService: SourceService = mock[SourceService]
  override val userService: UserService = mock[UserService]
  override val widgetService: WidgetService = mock[WidgetService]

  val routes: Route = sealRoute(adminWidgetApi.routes)

  private val country = Country("FR")
  private val questionId = QuestionId("question")
  private val sourceId = SourceId("source")

  private def widget(id: WidgetId) =
    Widget(
      id,
      sourceId,
      questionId,
      country,
      defaultAdminUser.userId,
      Widget.Version.V1,
      "<marquee><blink>En construction</blink></marquee>",
      DateHelper.now()
    )

  when(userService.getUser(defaultAdminUser.userId)).thenReturn(Future.successful(Some(defaultAdminUser)))
  when(userService.getUser(defaultSuperAdminUser.userId)).thenReturn(Future.successful(Some(defaultSuperAdminUser)))
  when(userService.getUsersByUserIds(Seq(defaultAdminUser.userId))).thenReturn(Future.successful(Seq(defaultAdminUser)))
  when(userService.getUsersByUserIds(Seq(defaultSuperAdminUser.userId)))
    .thenReturn(Future.successful(Seq(defaultSuperAdminUser)))

  Feature("list widgets") {

    val w = widget(WidgetId("id"))

    when(widgetService.list(any, any, any, any, any)).thenReturn(Future.successful(Seq(w)))
    when(widgetService.count(any)).thenReturn(Future.successful(20))

    Scenario("unauthorize unauthenticated") {
      Get("/admin/widgets?sourceId=source") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Get("/admin/widgets?sourceId=source").withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Get("/admin/widgets?sourceId=source").withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/widgets?sourceId=source")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val sources = entityAs[Seq[AdminWidgetResponse]]
          sources should be(
            Seq(
              AdminWidgetResponse(
                WidgetId("id"),
                QuestionId("question"),
                Country("FR"),
                "<marquee><blink>En construction</blink></marquee>",
                Widget.Version.V1,
                defaultAdminUser.displayName,
                w.createdAt
              )
            )
          )
          header("x-total-count").map(_.value) should be(Some("20"))
        }
      }
    }
  }

  Feature("get widget by id") {

    val existing = widget(WidgetId("existing"))

    when(widgetService.get(existing.id)).thenReturn(Future.successful(Some(existing)))
    when(widgetService.get(WidgetId("nonexisting"))).thenReturn(Future.successful(None))

    Scenario("unauthorize unauthenticated") {
      Get("/admin/widgets/existing") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Get("/admin/widgets/existing").withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Get("/admin/widgets/existing").withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin on existing widget") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/widgets/existing")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val widget = entityAs[AdminWidgetResponse]
          widget should be(
            AdminWidgetResponse(
              WidgetId("existing"),
              QuestionId("question"),
              Country("FR"),
              "<marquee><blink>En construction</blink></marquee>",
              Widget.Version.V1,
              defaultAdminUser.displayName,
              existing.createdAt
            )
          )
        }
      }
    }

    Scenario("not found and allow authenticated admin on a non existing widget") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/widgets/nonexisting")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }

  }

  Feature("create a source") {
    val created = widget(WidgetId("id"))

    when(questionService.getQuestion(any)).thenReturn(Future.successful(None))
    when(questionService.getQuestion(questionId))
      .thenReturn(Future.successful(Some(question(id = questionId, countries = NonEmptyList.of(Country("FR"))))))

    when(sourceService.get(any)).thenReturn(Future.successful(None))
    when(sourceService.get(sourceId)).thenReturn(Future.successful(Some(source(sourceId, defaultAdminUser.userId))))
    when(widgetService.create(any, any, any, any)).thenReturn(Future.successful(created))

    Scenario("unauthorize unauthenticated") {
      Post("/admin/widgets") ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Post("/admin/widgets")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Post("/admin/widgets")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/widgets")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                    |  "sourceId" : "source",
                                                                    |  "questionId" : "question",
                                                                    |  "country" : "FR"
                                                                    |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.Created)
        }
      }
    }

    Scenario("validate source") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/widgets")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                    |  "sourceId" : "fake",
                                                                    |  "questionId" : "question",
                                                                    |  "country" : "FR"
                                                                    |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size should be(1)
          errors.head.field should be("sourceId")
        }
      }
    }

    Scenario("validate question") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/widgets")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                    |  "sourceId" : "source",
                                                                    |  "questionId" : "fake",
                                                                    |  "country" : "FR"
                                                                    |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size should be(1)
          errors.head.field should be("questionId")
        }
      }
    }

    Scenario("validate country") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/widgets")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                    |  "sourceId" : "source",
                                                                    |  "questionId" : "question",
                                                                    |  "country" : "DE"
                                                                    |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size should be(1)
          errors.head.field should be("country")
        }
      }
    }
  }

}
