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

package org.make.api.idea

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.DateHelper
import org.make.core.idea.indexed.IdeaSearchResult
import org.make.core.idea.{Idea, IdeaId, IdeaSearchQuery, IdeaStatus}
import org.make.core.operation.OperationId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}

import scala.concurrent.Future

class ModerationIdeaApiTest
    extends MakeApiTestBase
    with DefaultModerationIdeaApiComponent
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with QuestionServiceComponent
    with IdeaServiceComponent
    with MakeSettingsComponent {

  override val ideaService: IdeaService = mock[IdeaService]
  override val questionService: QuestionService = mock[QuestionService]

  val fooIdeaText: String = "fooIdea"
  val fooIdeaId: IdeaId = IdeaId("fooIdeaId")
  val fooIdea: Idea =
    Idea(
      ideaId = fooIdeaId,
      name = fooIdeaText,
      operationId = Some(OperationId("vff")),
      questionId = Some(QuestionId("vff-fr-question")),
      createdAt = Some(DateHelper.now()),
      updatedAt = Some(DateHelper.now())
    )
  val barIdeaText: String = "barIdea"
  val barIdeaId: IdeaId = IdeaId("barIdeaId")
  val barIdea: Idea =
    Idea(ideaId = barIdeaId, name = barIdeaText, createdAt = Some(DateHelper.now()), updatedAt = Some(DateHelper.now()))
  val otherIdeaText: String = "otherIdea"
  val otherIdeaId: IdeaId = IdeaId("otherIdeaId")
  val otherIdea: Idea =
    Idea(
      ideaId = otherIdeaId,
      name = otherIdeaText,
      operationId = Some(OperationId("vff")),
      createdAt = Some(DateHelper.now()),
      updatedAt = Some(DateHelper.now())
    )

  when(questionService.getQuestion(any[QuestionId])).thenReturn(
    Future.successful(
      Some(
        Question(
          questionId = QuestionId("vff-fr-question"),
          slug = "vff-fr-question",
          country = Country("FR"),
          language = Language("fr"),
          question = "??",
          shortTitle = None,
          operationId = Some(OperationId("vff"))
        )
      )
    )
  )

  when(ideaService.fetchAll(any[IdeaSearchQuery]))
    .thenReturn(Future.successful(IdeaSearchResult.empty))

  when(ideaService.insert(eqTo(fooIdeaText), any[Question]))
    .thenReturn(Future.successful(fooIdea))

  when(ideaService.update(eqTo(fooIdeaId), any[String], any[IdeaStatus])).thenReturn(Future.successful(1))

  when(ideaService.fetchOneByName(any[QuestionId], eqTo(fooIdeaText)))
    .thenReturn(Future.successful(None))
  when(ideaService.fetchOneByName(any[QuestionId], eqTo(barIdeaText)))
    .thenReturn(Future.successful(None))
  when(ideaService.fetchOneByName(any[QuestionId], eqTo(otherIdeaText)))
    .thenReturn(Future.successful(Some(otherIdea)))
  when(ideaService.fetchOne(eqTo(fooIdeaId)))
    .thenReturn(Future.successful(Some(fooIdea)))

  val routes: Route = sealRoute(moderationIdeaApi.routes)

  Feature("create an idea") {
    Scenario("unauthenticated") {
      Given("an un authenticated user")
      When("the user wants to create an idea")
      Then("he should get an unauthorized (401) return code")
      Post("/moderation/ideas")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$fooIdeaText"}""")) ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("authenticated citizen") {
      Given("an authenticated user with the citizen role")
      When("the user wants to create an idea")
      Then("he should get an forbidden (403) return code")

      Post("/moderation/ideas")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$fooIdeaText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("authenticated moderator") {
      Given("an authenticated user with the moderator role")
      When("the user wants to create an idea")
      Then("It should be forbidden")

      Post("/moderation/ideas")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$fooIdeaText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("authenticated admin without questionId") {
      Given("an authenticated user with the admin role")
      When("the user wants to create an idea without an operationId nor a themeId")
      Then("Then he should get a bad request (400) return code")

      Post("/moderation/ideas")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$fooIdeaText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("authenticated admin with questionId") {
      Given("an authenticated user with the admin role")
      When("the user wants to create an idea with an questionId")
      Then("the idea should be saved if valid")

      Post("/moderation/ideas")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, s"""{"name": "$fooIdeaText", "questionId": "vff-fr-question"}""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.Created)
        val idea: IdeaResponse = entityAs[IdeaResponse]
        idea.id.value should be(fooIdeaId.value)
      }
    }

    Scenario("bad data in body") {
      Given("an authenticated user with the admin role")
      When("the user wants to create an idea")
      Then("Then he should get a bad request (400) return code")

      Post("/moderation/ideas")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"bibi": "$fooIdeaText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  Feature("update an idea") {
    Scenario("unauthenticated") {
      Given("an un authenticated user")
      When("the user wants to update an idea")
      Then("he should get an unauthorized (401) return code")
      Put(s"/moderation/ideas/${fooIdeaId.value}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$barIdeaText"}""")) ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("authenticated citizen") {
      Given("an authenticated user with the citizen role")
      When("the user wants to update an idea")
      Then("he should get an forbidden (403) return code")

      Put(s"/moderation/ideas/${fooIdeaId.value}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$barIdeaText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("authenticated moderator") {
      Given("an authenticated user with the moderator role")
      When("the user wants to update an idea")
      Then("the idea should be saved if valid")

      Put(s"/moderation/ideas/${fooIdeaId.value}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$barIdeaText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("authenticated admin - update idea with the name already exist") {
      Given("an authenticated user with the admin role")
      When("the user wants to update an idea with the name that already exist")
      Then("he should receive a bad request (400)")

      Put(s"/moderation/ideas/${fooIdeaId.value}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$otherIdeaText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("authenticated admin") {
      Given("an authenticated user with the admin role")
      When("the user wants to update an idea")
      Then("the idea should be saved if valid")

      Put(s"/moderation/ideas/${fooIdeaId.value}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"name": "$barIdeaText", "status": "Activated"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val ideaId: IdeaIdResponse = entityAs[IdeaIdResponse]
        ideaId.ideaId should be(fooIdeaId)
      }
    }
  }

  Feature("get an idea") {
    Scenario("unauthenticated") {
      Given("an un authenticated user")
      When("the user wants to get an idea")
      Then("he should get an unauthorized (401) return code")
      Get("/moderation/ideas/foo-idea") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("authenticated citizen") {
      Given("an authenticated user with the citizen role")
      When("the user wants to create an idea")
      Then("he should get an forbidden (403) return code")

      Get("/moderation/ideas/foo-idea")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

  }

  Feature("get a list of ideas") {
    Scenario("unauthenticated") {
      Given("an unauthenticated user")
      When("the user wants to get a list of ideas")
      Then("he should get an unauthorized (401) return code")
      Get("/moderation/ideas") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("authenticated citizen") {
      Given("an authenticated user with the citizen role")
      When("the user wants to create an idea")
      Then("he should get an forbidden (403) return code")

      Get("/moderation/ideas")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("authenticated admin") {
      Given("an authenticated user with the admin role")
      When("the user wants to get list of ideas")
      Then("the result should be a IdeaSearchResult")

      Get("/moderation/ideas")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val ideas: Seq[IdeaResponse] = entityAs[Seq[IdeaResponse]]
        ideas should be(Seq.empty)
      }
    }
  }
}
