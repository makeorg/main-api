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

package org.make.api.crmTemplates

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import io.circe.syntax._
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.core.ValidationError
import org.make.core.crmTemplate.{CrmQuestionTemplate, CrmQuestionTemplateId, CrmTemplateKind, TemplateId}
import org.make.core.question.QuestionId

import scala.concurrent.Future

class AdminCrmQuestionTemplatesApiTest
    extends MakeApiTestBase
    with DefaultAdminCrmQuestionTemplatesApiComponent
    with CrmTemplatesServiceComponent
    with QuestionServiceComponent {

  override val crmTemplatesService: CrmTemplatesService = mock[CrmTemplatesService]
  override val questionService: QuestionService = mock[QuestionService]

  val routes: Route = sealRoute(adminCrmQuestionTemplatesApi.routes)

  def template(questionId: QuestionId, kind: CrmTemplateKind): CrmQuestionTemplate =
    CrmQuestionTemplate(CrmQuestionTemplateId(s"id-${questionId.value}"), kind, questionId, TemplateId(s"1234"))

  Feature("list templates by question") {
    val questionId: QuestionId = QuestionId("list")

    when(crmTemplatesService.list(questionId))
      .thenReturn(
        Future.successful(
          Seq(
            template(questionId, CrmTemplateKind.Registration),
            template(questionId, CrmTemplateKind.B2BEmailChanged),
            template(questionId, CrmTemplateKind.B2BRegistration)
          )
        )
      )

    Scenario("list all") {
      Get(s"/admin/crm-templates/questions?questionId=${questionId.value}") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Get(s"/admin/crm-templates/questions?questionId=${questionId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Get(s"/admin/crm-templates/questions?questionId=${questionId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get(s"/admin/crm-templates/questions?questionId=${questionId.value}")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          header("x-total-count").map(_.value) should be(Some("3"))

          val templates: Seq[CrmQuestionTemplate] = entityAs[Seq[CrmQuestionTemplate]]
          templates.size should be(3)
          templates.map(_.kind).toSet shouldBe Set(
            CrmTemplateKind.Registration,
            CrmTemplateKind.B2BEmailChanged,
            CrmTemplateKind.B2BRegistration
          )
        }
      }
    }

    Scenario("missing mandatory questionId param") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {

        Get("/admin/crm-templates/questions")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }
  }

  Feature("get one question templates") {
    val id: CrmQuestionTemplateId = CrmQuestionTemplateId("id-getone")
    val fake: CrmQuestionTemplateId = CrmQuestionTemplateId("fake")
    val questionId: QuestionId = QuestionId("getone")
    when(crmTemplatesService.get(eqTo(id)))
      .thenReturn(Future.successful(Some(template(questionId, CrmTemplateKind.Registration))))
    when(crmTemplatesService.get(eqTo(fake)))
      .thenReturn(Future.successful(None))

    Scenario("get crmTemplates") {
      Get("/admin/crm-templates/questions/id-getone") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Get("/admin/crm-templates/questions/id-getone")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Get("/admin/crm-templates/questions/id-getone")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {

        Get("/admin/crm-templates/questions/id-getone")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
        }

        Get("/admin/crm-templates/questions/fake")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }
  }

  Feature("create question templates") {
    val questionId: QuestionId = QuestionId("create")
    val alreadyExistsId: QuestionId = QuestionId("already-exists")
    val kind = CrmTemplateKind.Registration
    val fakeQuestionId = QuestionId("fake")

    val id = CrmQuestionTemplateId("id-create")
    when(idGenerator.nextCrmQuestionTemplateId()).thenReturn(id)

    when(questionService.getCachedQuestion(eqTo(fakeQuestionId))).thenReturn(Future.successful(None))
    when(crmTemplatesService.list(eqTo(fakeQuestionId))).thenReturn(Future.successful(Seq()))

    when(questionService.getCachedQuestion(eqTo(questionId)))
      .thenReturn(Future.successful(Some(TestUtils.question(questionId))))
    when(crmTemplatesService.list(eqTo(questionId))).thenReturn(Future.successful(Seq()))

    when(questionService.getCachedQuestion(eqTo(alreadyExistsId)))
      .thenReturn(Future.successful(Some(TestUtils.question(alreadyExistsId))))
    when(crmTemplatesService.list(eqTo(alreadyExistsId)))
      .thenReturn(Future.successful(Seq(template(alreadyExistsId, kind))))

    val valid = CreateCrmQuestionTemplate(kind, questionId, TemplateId("4321"))
    val alreadyExists = CreateCrmQuestionTemplate(kind, alreadyExistsId, TemplateId("4321"))
    val fakeQuestion = valid.copy(questionId = fakeQuestionId)

    when(crmTemplatesService.create(eqTo(valid.toCrmQuestionTemplate(id))))
      .thenReturn(Future.successful(valid.toCrmQuestionTemplate(id)))

    Scenario("create") {
      Post("/admin/crm-templates/questions")
        .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Post("/admin/crm-templates/questions")
        .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Post("/admin/crm-templates/questions")
        .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/crm-templates/questions")
          .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {

          status should be(StatusCodes.Created)
        }
      }
    }

    Scenario("invalid") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/crm-templates/questions")
          .withEntity(HttpEntity(ContentTypes.`application/json`, fakeQuestion.asJson.toString))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)

          val errors = entityAs[Seq[ValidationError]]
          errors.size should be(1)
          errors.head.field should be("questionId")
        }
      }
    }

    Scenario("already exists") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/crm-templates/questions")
          .withEntity(HttpEntity(ContentTypes.`application/json`, alreadyExists.asJson.toString))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)

          val errors = entityAs[Seq[ValidationError]]
          errors.size should be(1)
          errors.head.field should be("templateKind")
        }
      }
    }
  }

  Feature("update a crmTemplates") {
    val questionId: QuestionId = QuestionId("update")
    val kind = CrmTemplateKind.Registration
    val doNotOverrideKind = CrmTemplateKind.Welcome
    val fakeQuestionId = QuestionId("fake")
    val templateUpdate = template(questionId, kind)
    val templateNotToOverride = templateUpdate.copy(id = CrmQuestionTemplateId("other-id"), kind = doNotOverrideKind)

    when(crmTemplatesService.get(eqTo(CrmQuestionTemplateId("id-fake"))))
      .thenReturn(Future.successful(None))
    when(questionService.getCachedQuestion(eqTo(fakeQuestionId))).thenReturn(Future.successful(None))

    when(crmTemplatesService.get(eqTo(CrmQuestionTemplateId(s"id-update"))))
      .thenReturn(Future.successful(Some(templateUpdate)))
    when(questionService.getCachedQuestion(eqTo(questionId)))
      .thenReturn(Future.successful(Some(TestUtils.question(questionId))))
    when(crmTemplatesService.list(eqTo(questionId)))
      .thenReturn(Future.successful(Seq(templateUpdate, templateNotToOverride)))

    val valid = templateUpdate.copy(template = TemplateId("4567"))
    val fakeQuestion = valid.copy(questionId = fakeQuestionId)

    when(crmTemplatesService.update(eqTo(valid)))
      .thenReturn(Future.successful(valid))

    Scenario("update") {
      Put("/admin/crm-templates/questions/id-update")
        .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Put("/admin/crm-templates/questions/id-update")
        .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Put("/admin/crm-templates/questions/id-update")
        .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/crm-templates/questions/id-update")
          .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
        }
      }
    }

    Scenario("not found") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/crm-templates/questions/fake")
          .withEntity(HttpEntity(ContentTypes.`application/json`, fakeQuestion.asJson.toString))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }

    Scenario("kind already exists") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/crm-templates/questions/id-update")
          .withEntity(HttpEntity(ContentTypes.`application/json`, valid.copy(kind = doNotOverrideKind).asJson.toString))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)

          val errors = entityAs[Seq[ValidationError]]
          errors.size should be(1)
          errors.head.field should be("kind")
        }
      }
    }

    Scenario("fake question") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/crm-templates/questions/id-update")
          .withEntity(
            HttpEntity(ContentTypes.`application/json`, valid.copy(questionId = fakeQuestionId).asJson.toString)
          )
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)

          val errors = entityAs[Seq[ValidationError]]
          errors.size should be(1)
          errors.head.field should be("questionId")
        }
      }
    }
  }

  Feature("delete one question templates") {
    val id: CrmQuestionTemplateId = CrmQuestionTemplateId("id-delete")

    when(crmTemplatesService.delete(eqTo(id)))
      .thenReturn(Future.unit)

    Scenario("delete crmTemplates") {
      Delete("/admin/crm-templates/questions/id-delete") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Delete("/admin/crm-templates/questions/id-delete")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Delete("/admin/crm-templates/questions/id-delete")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Delete("/admin/crm-templates/questions/id-delete")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
        }
      }
    }
  }
}
