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
import org.make.api.MakeApiTestBase
import org.make.core.ValidationError
import org.make.core.crmTemplate.{CrmLanguageTemplate, CrmLanguageTemplateId, CrmTemplateKind, TemplateId}
import org.make.core.reference.Language

import scala.concurrent.Future

class AdminCrmLanguageTemplatesApiTest
    extends MakeApiTestBase
    with DefaultAdminCrmLanguageTemplatesApiComponent
    with CrmTemplatesServiceComponent {

  override val crmTemplatesService: CrmTemplatesService = mock[CrmTemplatesService]
  val routes: Route = sealRoute(adminCrmLanguageTemplatesApi.routes)

  def crmLanguageTemplate(language: Language, kind: CrmTemplateKind): CrmLanguageTemplate =
    CrmLanguageTemplate(CrmLanguageTemplateId(s"id-${language.value}"), kind, language, TemplateId(s"1234"))
  def values(language: Language): CrmTemplateKind => CrmLanguageTemplate =
    CrmTemplateKind.values.map(kind => kind -> crmLanguageTemplate(language, kind)).toMap.apply

  val fr: Language = Language("fr")
  val en: Language = Language("en")
  val it: Language = Language("it")
  val de: Language = Language("de")
  val nl: Language = Language("nl")
  val bg: Language = Language("bg")
  val el: Language = Language("el")
  val cs: Language = Language("cs")

  when(crmTemplatesService.get(eqTo(Language("non"))))
    .thenReturn(Future.successful(None))

  Feature("list language templates") {
    when(crmTemplatesService.listByLanguage())
      .thenReturn(Future.successful(Map(fr -> values(fr), en -> values(en), it -> values(it))))

    Scenario("list all") {
      Get("/admin/crm-templates/languages") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Get("/admin/crm-templates/languages")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Get("/admin/crm-templates/languages")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Get("/admin/crm-templates/languages")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        header("x-total-count").map(_.value) should be(Some("3"))

        val templates: Seq[CrmLanguageTemplates] = entityAs[Seq[CrmLanguageTemplates]]
        templates.size should be(3)
        templates.map(_.id).toSet shouldBe Set(fr, en, it)
      }
    }
  }

  Feature("get one language templates") {
    when(crmTemplatesService.get(eqTo(de)))
      .thenReturn(Future.successful(Some(values(de))))

    Scenario("get crmTemplates") {
      Get("/admin/crm-templates/languages/de") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Get("/admin/crm-templates/languages/de")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Get("/admin/crm-templates/languages/de")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Get("/admin/crm-templates/languages/de")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }

      Get("/admin/crm-templates/languages/non")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }

  Feature("create language templates") {
    when(crmTemplatesService.get(eqTo(nl)))
      .thenReturn(Future.successful(None))
    when(crmTemplatesService.get(eqTo(bg)))
      .thenReturn(Future.successful(Some(values(bg))))

    val valid = CrmLanguageTemplates(
      id = Language("nl"),
      registration = TemplateId("12340"),
      resendRegistration = TemplateId("12341"),
      welcome = TemplateId("12342"),
      proposalAccepted = TemplateId("12343"),
      proposalRefused = TemplateId("12344"),
      forgottenPassword = TemplateId("12345"),
      b2bProposalAccepted = TemplateId("12346"),
      b2bProposalRefused = TemplateId("12347"),
      b2bForgottenPassword = TemplateId("12348"),
      b2bRegistration = TemplateId("12349"),
      b2bEmailChangedConfirmation = TemplateId("12350")
    )
    val languageAlreadyExists = valid.copy(id = Language("bg"))
    val fakeLanguage = valid.copy(id = Language("non"))
    val missingTemplates =
      """{
         |  "id": "nl",
         |  "registration": "12340"
         |}""".stripMargin

    when(crmTemplatesService.create(eqTo(nl), argThat[CrmTemplateKind => TemplateId] { arg =>
      CrmTemplateKind.values.forall(kind => arg(kind) == valid.values(kind))
    }))
      .thenReturn(Future.successful(values(nl).apply))

    Scenario("create") {
      Post("/admin/crm-templates/languages")
        .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Post("/admin/crm-templates/languages")
        .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Post("/admin/crm-templates/languages")
        .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Post("/admin/crm-templates/languages")
        .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }

    Scenario("invalid") {
      Post("/admin/crm-templates/languages")
        .withEntity(HttpEntity(ContentTypes.`application/json`, fakeLanguage.asJson.toString))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size should be(1)
        errors.head.field should be("id")
      }

      Post("/admin/crm-templates/languages")
        .withEntity(HttpEntity(ContentTypes.`application/json`, missingTemplates))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)

        val errors = entityAs[Seq[ValidationError]]
        errors.size should be(10)
        errors.map(_.field).contains("welcome") shouldBe true
      }
    }

    Scenario("already exists") {
      Post("/admin/crm-templates/languages")
        .withEntity(HttpEntity(ContentTypes.`application/json`, languageAlreadyExists.asJson.toString))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)

        val errors = entityAs[Seq[ValidationError]]
        errors.size should be(1)
        errors.head.field should be("id")
      }
    }
  }

  Feature("update a crmTemplates") {
    when(crmTemplatesService.get(eqTo(el)))
      .thenReturn(Future.successful(Some(values(el))))

    val valid = CrmLanguageTemplates(
      id = el,
      registration = TemplateId("10000"),
      resendRegistration = TemplateId("10001"),
      welcome = TemplateId("10002"),
      proposalAccepted = TemplateId("10003"),
      proposalRefused = TemplateId("10004"),
      forgottenPassword = TemplateId("10005"),
      b2bProposalAccepted = TemplateId("10006"),
      b2bProposalRefused = TemplateId("10007"),
      b2bForgottenPassword = TemplateId("10008"),
      b2bRegistration = TemplateId("10009"),
      b2bEmailChangedConfirmation = TemplateId("10010")
    )
    val missingTemplates =
      """{
        |  "id": "el",
        |  "registration": "12340"
        |}""".stripMargin

    when(crmTemplatesService.update(eqTo(el), argThat[CrmTemplateKind => TemplateId] { arg =>
      CrmTemplateKind.values.forall(kind => arg(kind) == valid.values(kind))
    }))
      .thenReturn(Future.successful(values(el)))

    Scenario("update") {
      Post("/admin/crm-templates/languages/el")
        .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Post("/admin/crm-templates/languages/el")
        .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Post("/admin/crm-templates/languages/el")
        .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Post("/admin/crm-templates/languages/el")
        .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("invalid") {
      Post("/admin/crm-templates/languages/el")
        .withEntity(HttpEntity(ContentTypes.`application/json`, missingTemplates))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)

        val errors = entityAs[Seq[ValidationError]]
        errors.size should be(10)
        errors.map(_.field).contains("welcome") shouldBe true
      }

      Post("/admin/crm-templates/languages/non")
        .withEntity(HttpEntity(ContentTypes.`application/json`, valid.asJson.toString))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }
}
