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
import org.make.api.MakeApiTestBase
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.core.crmTemplate.{CrmTemplates, CrmTemplatesId, TemplateId}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.Future

class AdminCrmTemplatesApiTest
    extends MakeApiTestBase
    with DefaultAdminCrmTemplatesApiComponent
    with MockitoSugar
    with CrmTemplatesServiceComponent
    with QuestionServiceComponent {

  override val crmTemplatesService: CrmTemplatesService = mock[CrmTemplatesService]
  override val questionService: QuestionService = mock[QuestionService]

  val defaultCrmTemplate = CrmTemplates(
    crmTemplatesId = CrmTemplatesId("default-id"),
    questionId = None,
    locale = Some("fr_FR"),
    registration = TemplateId("56780"),
    welcome = TemplateId("56781"),
    proposalAccepted = TemplateId("56782"),
    proposalRefused = TemplateId("56783"),
    forgottenPassword = TemplateId("56784"),
    resendRegistration = TemplateId("56785"),
    proposalAcceptedOrganisation = TemplateId("56786"),
    proposalRefusedOrganisation = TemplateId("56787"),
    forgottenPasswordOrganisation = TemplateId("56788"),
    organisationEmailChangeConfirmation = TemplateId("56788")
  )

  val validCrmTemplates = CrmTemplates(
    crmTemplatesId = CrmTemplatesId("id"),
    questionId = Some(QuestionId("question-id")),
    locale = Some("fr_FR"),
    registration = TemplateId("12340"),
    welcome = TemplateId("12341"),
    proposalAccepted = TemplateId("12342"),
    proposalRefused = TemplateId("12343"),
    forgottenPassword = TemplateId("12344"),
    resendRegistration = TemplateId("12345"),
    proposalAcceptedOrganisation = TemplateId("12346"),
    proposalRefusedOrganisation = TemplateId("12347"),
    forgottenPasswordOrganisation = TemplateId("12348"),
    organisationEmailChangeConfirmation = TemplateId("12348")
  )

  when(crmTemplatesService.createCrmTemplates(ArgumentMatchers.any[CreateCrmTemplates])).thenAnswer { invocation =>
    invocation.getArgument[CreateCrmTemplates](0) match {
      case create if create.questionId.contains(QuestionId("question-id")) =>
        Future.successful(validCrmTemplates)
      case create if create.questionId.isEmpty && create.locale.contains("fr_FR") =>
        Future.successful(validCrmTemplates.copy(questionId = None))
      case _ =>
        throw new NullPointerException()
    }
  }

  when(crmTemplatesService.getCrmTemplates(ArgumentMatchers.eq(CrmTemplatesId("id"))))
    .thenReturn(Future.successful(Some(validCrmTemplates)))

  when(crmTemplatesService.getCrmTemplates(ArgumentMatchers.eq(CrmTemplatesId("fake"))))
    .thenReturn(Future.successful(None))

  when(
    crmTemplatesService
      .find(any[Int], any[Option[Int]], ArgumentMatchers.eq(Some(QuestionId("id"))), ArgumentMatchers.eq(Some("fr_FR")))
  ).thenReturn(Future.successful(Seq(validCrmTemplates)))

  when(
    crmTemplatesService
      .find(any[Int], any[Option[Int]], ArgumentMatchers.eq(None), ArgumentMatchers.eq(None))
  ).thenReturn(Future.successful(Seq(validCrmTemplates, validCrmTemplates.copy(crmTemplatesId = CrmTemplatesId("2")))))

  when(crmTemplatesService.updateCrmTemplates(ArgumentMatchers.any[UpdateCrmTemplates])).thenAnswer { invocation =>
    invocation.getArgument[UpdateCrmTemplates](0).crmTemplatesId.value match {
      case "id"   => Future.successful(Some(validCrmTemplates.copy(registration = TemplateId("56789"))))
      case "fake" => Future.successful(None)
      case _      => throw new NullPointerException()
    }
  }

  when(crmTemplatesService.count(ArgumentMatchers.eq(Some(QuestionId("question-id"))), ArgumentMatchers.eq(None)))
    .thenReturn(Future.successful(0))

  when(
    crmTemplatesService
      .count(ArgumentMatchers.eq(Some(QuestionId("existing-question-id"))), ArgumentMatchers.eq(None))
  ).thenReturn(Future.successful(1))

  when(
    crmTemplatesService
      .count(ArgumentMatchers.eq(None), ArgumentMatchers.eq(Some("fr_FR")))
  ).thenReturn(Future.successful(0))

  when(crmTemplatesService.count(ArgumentMatchers.eq(None), ArgumentMatchers.eq(None)))
    .thenReturn(Future.successful(2))

  val question =
    Question(QuestionId("question-id"), "question", Country("FR"), Language("fr"), "question ?", None)

  when(questionService.getQuestion(QuestionId("question-id")))
    .thenReturn(Future.successful(Some(question)))

  when(questionService.getQuestion(QuestionId("existing-question-id")))
    .thenReturn(Future.successful(Some(question.copy(questionId = QuestionId("existing-question-id")))))

  when(crmTemplatesService.getDefaultTemplate(locale = Some("fr_FR")))
    .thenReturn(Future.successful(Some(defaultCrmTemplate)))

  when(crmTemplatesService.getDefaultTemplate(locale = Some("en_GB")))
    .thenReturn(Future.successful(None))

  val routes: Route = sealRoute(adminCrmTemplateApi.routes)

  feature("create a crmTemplates") {
    val crmTemplateData =
      """{
         |  "questionId": "question-id",
         |  "registration": "12340",
         |  "welcome": "12341",
         |  "proposalAccepted": "12342",
         |  "proposalRefused": "12343",
         |  "forgottenPassword": "12344",
         |  "resendRegistration": "12345",
         |  "proposalAcceptedOrganisation": "12346",
         |  "proposalRefusedOrganisation": "12347",
         |  "forgottenPasswordOrganisation": "12348"
         |}""".stripMargin
    scenario("unauthenticated") {
      Post("/admin/crm/templates")
        .withEntity(HttpEntity(ContentTypes.`application/json`, crmTemplateData)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated citizen") {
      Post("/admin/crm/templates")
        .withEntity(HttpEntity(ContentTypes.`application/json`, crmTemplateData))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated moderator") {
      Post("/admin/crm/templates")
        .withEntity(HttpEntity(ContentTypes.`application/json`, crmTemplateData))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated admin") {
      Post("/admin/crm/templates")
        .withEntity(HttpEntity(ContentTypes.`application/json`, crmTemplateData))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }

      val requestNoQuestionId =
        """{
           |  "country": "FR",
           |  "language": "fr",
           |  "registration": "12340",
           |  "welcome": "12341",
           |  "proposalAccepted": "12342",
           |  "proposalRefused": "12343",
           |  "forgottenPassword": "12344",
           |  "resendRegistration": "12345",
           |  "proposalAcceptedOrganisation": "12346",
           |  "proposalRefusedOrganisation": "12347",
           |  "forgottenPasswordOrganisation": "12348"
           |}""".stripMargin

      Post("/admin/crm/templates")
        .withEntity(HttpEntity(ContentTypes.`application/json`, requestNoQuestionId))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }

      val requestQuestionIdExists = crmTemplateData.replace("question-id", "existing-question-id")
      Post("/admin/crm/templates")
        .withEntity(HttpEntity(ContentTypes.`application/json`, requestQuestionIdExists))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }

      val requestNoQuestionIdNoLocale =
        """{
           |  "registration": "12340",
           |  "welcome": "12341",
           |  "proposalAccepted": "12342",
           |  "proposalRefused": "12343",
           |  "forgottenPassword": "12344",
           |  "resendRegistration": "12345",
           |  "proposalAcceptedOrganisation": "12346",
           |  "proposalRefusedOrganisation": "12347",
           |  "forgottenPasswordOrganisation": "12348"
           |}""".stripMargin

      Post("/admin/crm/templates")
        .withEntity(HttpEntity(ContentTypes.`application/json`, requestNoQuestionIdNoLocale))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }

      val requestDefaultDoesntExistAndSomeTemplatesAreMissing =
        """{
          |  "country": "GB"
          |  "language": "en",
          |  "proposalRefused": "12343",
          |  "forgottenPassword": "12344",
          |  "proposalAcceptedOrganisation": "12345",
          |  "proposalRefusedOrganisation": "12346",
          |  "forgottenPasswordOrganisation": "12347"
          |}""".stripMargin

      Post("/admin/crm/templates")
        .withEntity(HttpEntity(ContentTypes.`application/json`, requestDefaultDoesntExistAndSomeTemplatesAreMissing))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  feature("get crmTemplates") {
    scenario("get crmTemplates") {
      Get("/admin/crm/templates/id") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Get("/admin/crm/templates/id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Get("/admin/crm/templates/id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Get("/admin/crm/templates/id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }

      Get("/admin/crm/templates/fake")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("list crmTemplates") {
      Get("/admin/crm/templates?_start=0&_end=10") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Get("/admin/crm/templates?_start=0&_end=10")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Get("/admin/crm/templates?_start=0&_end=10")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Get("/admin/crm/templates?_start=0&_end=10")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        header("x-total-count").map(_.value) should be(Some("2"))
        val templates: Seq[CrmTemplatesResponse] = entityAs[Seq[CrmTemplatesResponse]]
        templates.size should be(2)
        templates.head.id.value should be("id")
        templates(1).id.value should be("2")
      }
    }
  }

  feature("update a crmTemplates") {
    val updateCrmTemplateData =
      """{
         |  "registration": "999999",
         |  "welcome": "12341",
         |  "proposalAccepted": "12342",
         |  "proposalRefused": "12343",
         |  "forgottenPassword": "12344",
         |  "resendRegistration": "12345",
         |  "proposalAcceptedOrganisation": "12346",
         |  "proposalRefusedOrganisation": "12347",
         |  "forgottenPasswordOrganisation": "12348"
         |}""".stripMargin

    scenario("update fake crmTemplates") {
      Put("/admin/crm/templates/fake") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Put("/admin/crm/templates/fake")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Put("/admin/crm/templates/fake")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Put("/admin/crm/templates/fake")
        .withEntity(HttpEntity(ContentTypes.`application/json`, updateCrmTemplateData))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("update crmTemplates with an invalid request") {
      Put("/admin/crm/templates/fake")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("update crmTemplates with an invalid template id") {
      val updateCrmTemplateData =
        """{
          |  "registration": "textual",
          |  "welcome": "12341",
          |  "proposalAccepted": "12342",
          |  "proposalRefused": "12343",
          |  "forgottenPassword": "12344",
          |  "resendRegistration": "12345",
          |  "proposalAcceptedOrganisation": "12346",
          |  "proposalRefusedOrganisation": "12347",
          |  "forgottenPasswordOrganisation": "12348"
          |}""".stripMargin

      Put("/admin/crm/templates/fake")
        .withEntity(HttpEntity(ContentTypes.`application/json`, updateCrmTemplateData))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("update crmTemplates") {
      Put("/admin/crm/templates/id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, updateCrmTemplateData))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val crmTemplates: CrmTemplatesResponse = entityAs[CrmTemplatesResponse]
        crmTemplates.id.value should be("id")
        crmTemplates.registration should be(TemplateId("56789"))
      }
    }

  }
}
