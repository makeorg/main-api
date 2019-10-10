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

import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.core.auth.UserRights
import org.make.core.crmTemplate.{CrmTemplates, CrmTemplatesId, TemplateId}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => matches}
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class AdminCrmTemplatesApiTest
    extends MakeApiTestBase
    with DefaultAdminCrmTemplatesApiComponent
    with MockitoSugar
    with CrmTemplatesServiceComponent
    with QuestionServiceComponent {

  override val crmTemplatesService: CrmTemplatesService = mock[CrmTemplatesService]
  override val questionService: QuestionService = mock[QuestionService]

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

  val validCrmTemplates = CrmTemplates(
    crmTemplatesId = CrmTemplatesId("id"),
    questionId = Some(QuestionId("question-id")),
    locale = Some("fr_FR"),
    registration = TemplateId("12340"),
    welcome = TemplateId("12341"),
    proposalAccepted = TemplateId("12342"),
    proposalRefused = TemplateId("12343"),
    forgottenPassword = TemplateId("12344"),
    proposalAcceptedOrganisation = TemplateId("12345"),
    proposalRefusedOrganisation = TemplateId("12346"),
    forgottenPasswordOrganisation = TemplateId("12347")
  )

  when(crmTemplatesService.createCrmTemplates(ArgumentMatchers.any[CreateCrmTemplates])).thenAnswer { invocation =>
    invocation.getArgument[CreateCrmTemplates](0) match {
      case create if create.questionId.contains(QuestionId("question-id")) && create.locale.contains("fr_FR") =>
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
      case "id"   => Future.successful(Some(validCrmTemplates.copy(registration = TemplateId("updated"))))
      case "fake" => Future.successful(None)
      case _      => throw new NullPointerException()
    }
  }

  when(
    crmTemplatesService.count(ArgumentMatchers.eq(Some(QuestionId("question-id"))), ArgumentMatchers.eq(Some("fr_FR")))
  ).thenReturn(Future.successful(0))

  when(
    crmTemplatesService
      .count(ArgumentMatchers.eq(Some(QuestionId("existing-question-id"))), ArgumentMatchers.eq(Some("fr_FR")))
  ).thenReturn(Future.successful(1))

  when(
    crmTemplatesService
      .count(ArgumentMatchers.eq(None), ArgumentMatchers.eq(Some("fr_FR")))
  ).thenReturn(Future.successful(0))

  when(crmTemplatesService.count(ArgumentMatchers.eq(None), ArgumentMatchers.eq(None)))
    .thenReturn(Future.successful(2))

  val question =
    Question(QuestionId("question-id"), "question", Country("FR"), Language("fr"), "question ?", None, None)

  when(questionService.getQuestion(QuestionId("question-id")))
    .thenReturn(Future.successful(Some(question)))

  when(questionService.getQuestion(QuestionId("existing-question-id")))
    .thenReturn(Future.successful(Some(question.copy(questionId = QuestionId("existing-question-id")))))

  val routes: Route = sealRoute(adminCrmTemplateApi.routes)

  feature("create a crmTemplates") {
    val crmTemplateData =
      """{
         |  "questionId": "question-id",
         |  "country": "FR",
         |  "language": "fr",
         |  "registration": "12340",
         |  "welcome": "12341",
         |  "proposalAccepted": "12342",
         |  "proposalRefused": "12343",
         |  "forgottenPassword": "12344",
         |  "proposalAcceptedOrganisation": "12345",
         |  "proposalRefusedOrganisation": "12346",
         |  "forgottenPasswordOrganisation": "12347"
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
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated moderator") {
      Post("/admin/crm/templates")
        .withEntity(HttpEntity(ContentTypes.`application/json`, crmTemplateData))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated admin") {
      Post("/admin/crm/templates")
        .withEntity(HttpEntity(ContentTypes.`application/json`, crmTemplateData))
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
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
           |  "proposalAcceptedOrganisation": "12345",
           |  "proposalRefusedOrganisation": "12346",
           |  "forgottenPasswordOrganisation": "12347"
           |}""".stripMargin

      Post("/admin/crm/templates")
        .withEntity(HttpEntity(ContentTypes.`application/json`, requestNoQuestionId))
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }

      val requestQuestionIdExists = crmTemplateData.replace("question-id", "existing-question-id")
      Post("/admin/crm/templates")
        .withEntity(HttpEntity(ContentTypes.`application/json`, requestQuestionIdExists))
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }

      val requestNoQuestionIdNoLocale =
        """{
           |  "registration": "12340",
           |  "welcome": "12341",
           |  "proposalAccepted": "12342",
           |  "proposalRefused": "12343",
           |  "forgottenPassword": "12344",
           |  "proposalAcceptedOrganisation": "12345",
           |  "proposalRefusedOrganisation": "12346",
           |  "forgottenPasswordOrganisation": "12347"
           |}""".stripMargin

      Post("/admin/crm/templates")
        .withEntity(HttpEntity(ContentTypes.`application/json`, requestNoQuestionIdNoLocale))
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
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
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Get("/admin/crm/templates/id")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Get("/admin/crm/templates/id")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }

      Get("/admin/crm/templates/fake")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("list crmTemplates") {
      Get("/admin/crm/templates?_start=0&_end=10") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Get("/admin/crm/templates?_start=0&_end=10")
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Get("/admin/crm/templates?_start=0&_end=10")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Get("/admin/crm/templates?_start=0&_end=10")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
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
         |  "proposalAcceptedOrganisation": "12345",
         |  "proposalRefusedOrganisation": "12346",
         |  "forgottenPasswordOrganisation": "12347"
         |}""".stripMargin

    scenario("update fake crmTemplates") {
      Put("/admin/crm/templates/fake") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Put("/admin/crm/templates/fake")
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Put("/admin/crm/templates/fake")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Put("/admin/crm/templates/fake")
        .withEntity(HttpEntity(ContentTypes.`application/json`, updateCrmTemplateData))
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("update crmTemplates with an invalid request") {
      Put("/admin/crm/templates/fake")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
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
          |  "proposalAcceptedOrganisation": "12345",
          |  "proposalRefusedOrganisation": "12346",
          |  "forgottenPasswordOrganisation": "12347"
          |}""".stripMargin

      Put("/admin/crm/templates/fake")
        .withEntity(HttpEntity(ContentTypes.`application/json`, updateCrmTemplateData))
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("update crmTemplates") {
      Put("/admin/crm/templates/id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, updateCrmTemplateData))
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val crmTemplates: CrmTemplatesResponse = entityAs[CrmTemplatesResponse]
        crmTemplates.id.value should be("id")
        crmTemplates.registration should be(TemplateId("updated"))
      }
    }

  }
}
