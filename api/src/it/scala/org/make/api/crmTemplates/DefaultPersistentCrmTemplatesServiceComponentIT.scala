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

import org.make.api.DatabaseTest
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.core.crmTemplate.{CrmTemplates, CrmTemplatesId, TemplateId}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class DefaultPersistentCrmTemplatesServiceComponentIT
    extends DatabaseTest
    with DefaultPersistentCrmTemplatesServiceComponent
    with DefaultPersistentQuestionServiceComponent {
  override protected val cockroachExposedPort: Int = 40013

  def waitForCompletion(f: Future[_]): Unit = whenReady(f, Timeout(5.seconds))(_ => ())

  def createQuestion(id: QuestionId): Question = Question(
    questionId = id,
    slug = id.value,
    country = Country("FR"),
    language = Language("fr"),
    question = id.value,
    shortTitle = None,
    operationId = None
  )

  feature("CRUD crm templates") {
    scenario("insert templates") {
      val questionId = QuestionId("create-templates")

      val crmTemplates = CrmTemplates(
        crmTemplatesId = CrmTemplatesId("template-id"),
        questionId = Some(questionId),
        locale = None,
        registration = TemplateId("registration-id"),
        welcome = TemplateId("welcome-id"),
        proposalAccepted = TemplateId("accepted-id"),
        proposalRefused = TemplateId("refused-id"),
        forgottenPassword = TemplateId("forgot_password-id"),
        resendRegistration = TemplateId("resend-registration-id"),
        proposalAcceptedOrganisation = TemplateId("accepted_organisation-id"),
        proposalRefusedOrganisation = TemplateId("refused_organisation-id"),
        forgottenPasswordOrganisation = TemplateId("forgot_password_organisation-id"),
        organisationEmailChangeConfirmation = TemplateId("organisation_change_email_confirmation-id"),
        registrationB2B = TemplateId("registration_b2b-id")
      )

      waitForCompletion(persistentQuestionService.persist(createQuestion(questionId)))

      whenReady(persistentCrmTemplatesService.persist(crmTemplates), Timeout(2.seconds)) { answer =>
        answer should be(crmTemplates)
      }

      waitForCompletion(
        persistentCrmTemplatesService
          .persist(
            crmTemplates
              .copy(crmTemplatesId = CrmTemplatesId("question-unknown"), questionId = Some(QuestionId("unknown")))
          )
          .failed
      )

      waitForCompletion(
        persistentCrmTemplatesService
          .persist(crmTemplates.copy(crmTemplatesId = CrmTemplatesId("question-none"), questionId = None))
      )
    }

    scenario("update and get") {
      val questionId = QuestionId("update-templates")

      val crmTemplates = CrmTemplates(
        crmTemplatesId = CrmTemplatesId("template-id-to-update"),
        questionId = Some(questionId),
        locale = None,
        registration = TemplateId("registration-id"),
        welcome = TemplateId("welcome-id"),
        proposalAccepted = TemplateId("accepted-id"),
        proposalRefused = TemplateId("refused-id"),
        forgottenPassword = TemplateId("forgot_password-id"),
        resendRegistration = TemplateId("resend-registration-id"),
        proposalAcceptedOrganisation = TemplateId("accepted_organisation-id"),
        proposalRefusedOrganisation = TemplateId("refused_organisation-id"),
        forgottenPasswordOrganisation = TemplateId("forgot_password_organisation-id"),
        organisationEmailChangeConfirmation = TemplateId("organisation_change_email_confirmation-id"),
        registrationB2B = TemplateId("registration_b2b-id")
      )

      val insertDependencies = for {
        _ <- persistentQuestionService.persist(createQuestion(questionId))
        _ <- persistentCrmTemplatesService.persist(crmTemplates)
      } yield ()

      waitForCompletion(insertDependencies)

      whenReady(persistentCrmTemplatesService.getById(crmTemplates.crmTemplatesId), Timeout(2.seconds)) { result =>
        result should contain(crmTemplates)
      }

      waitForCompletion(
        persistentCrmTemplatesService.modify(
          crmTemplates
            .copy(registration = TemplateId("new-registration"), proposalAccepted = TemplateId("new-accepted"))
        )
      )

      whenReady(persistentCrmTemplatesService.getById(crmTemplates.crmTemplatesId), Timeout(2.seconds)) { result =>
        result should contain(
          crmTemplates
            .copy(registration = TemplateId("new-registration"), proposalAccepted = TemplateId("new-accepted"))
        )
      }
    }

    scenario("finding and count templates") {
      val questionId1 = QuestionId("finding-templates-1")
      val questionId2 = QuestionId("finding-templates-2")
      val questionId3 = QuestionId("finding-templates-3")

      val crmTemplates1 = CrmTemplates(
        crmTemplatesId = CrmTemplatesId("template-id-1"),
        questionId = Some(questionId1),
        locale = None,
        registration = TemplateId("registration-id"),
        welcome = TemplateId("welcome-id"),
        proposalAccepted = TemplateId("accepted-id"),
        proposalRefused = TemplateId("refused-id"),
        forgottenPassword = TemplateId("forgot_password-id"),
        resendRegistration = TemplateId("resend-registration-id"),
        proposalAcceptedOrganisation = TemplateId("accepted_organisation-id"),
        proposalRefusedOrganisation = TemplateId("refused_organisation-id"),
        forgottenPasswordOrganisation = TemplateId("forgot_password_organisation-id"),
        organisationEmailChangeConfirmation = TemplateId("organisation_change_email_confirmation-id"),
        registrationB2B = TemplateId("registration_b2b-id")
      )
      val crmTemplates2 =
        crmTemplates1.copy(crmTemplatesId = CrmTemplatesId("template-id-2"), questionId = Some(questionId2))
      val crmTemplates3 =
        crmTemplates1.copy(crmTemplatesId = CrmTemplatesId("template-id-3"), questionId = Some(questionId3))
      val crmTemplates4 =
        crmTemplates1.copy(crmTemplatesId = CrmTemplatesId("template-id-4"), questionId = None, locale = Some("fr_FR"))
      val crmTemplates5 =
        crmTemplates1.copy(
          crmTemplatesId = CrmTemplatesId("template-id-5"),
          questionId = None,
          locale = Some("other_locale")
        )

      val insertDependencies = for {
        _ <- persistentQuestionService.persist(createQuestion(questionId1))
        _ <- persistentQuestionService.persist(createQuestion(questionId2))
        _ <- persistentQuestionService.persist(createQuestion(questionId3))
        _ <- persistentCrmTemplatesService.persist(crmTemplates1)
        _ <- persistentCrmTemplatesService.persist(crmTemplates2)
        _ <- persistentCrmTemplatesService.persist(crmTemplates3)
        _ <- persistentCrmTemplatesService.persist(crmTemplates4)
        _ <- persistentCrmTemplatesService.persist(crmTemplates5)
      } yield ()

      waitForCompletion(insertDependencies)

      whenReady(persistentCrmTemplatesService.find(0, None, Some(questionId1), None), Timeout(2.seconds)) { result =>
        result.map(_.crmTemplatesId.value).sorted should be(Seq("template-id-1"))
      }

      whenReady(persistentCrmTemplatesService.find(0, None, Some(questionId2), None), Timeout(2.seconds)) { result =>
        result.map(_.crmTemplatesId.value).sorted should be(Seq("template-id-2"))
      }

      whenReady(persistentCrmTemplatesService.find(0, None, None, Some("fr_FR")), Timeout(2.seconds)) { result =>
        result.map(_.crmTemplatesId.value) should contain("template-id-4")
      }

      whenReady(persistentCrmTemplatesService.count(Some(QuestionId("unknown")), None)) { _ should be(0) }

      whenReady(persistentCrmTemplatesService.count(Some(questionId3), None)) { _ should be(1) }

      whenReady(persistentCrmTemplatesService.count(None, Some("other_locale"))) { _ should be(1) }
    }
  }

}
