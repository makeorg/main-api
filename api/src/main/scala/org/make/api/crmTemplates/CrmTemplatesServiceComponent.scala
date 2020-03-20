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

import org.make.api.technical._
import org.make.core.crmTemplate.{CrmTemplates, CrmTemplatesId, TemplateId}
import org.make.core.question.QuestionId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.make.core.Validation._

import scala.util.Try

trait CrmTemplatesServiceComponent {
  def crmTemplatesService: CrmTemplatesService
}

trait CrmTemplatesService extends ShortenedNames {
  def getCrmTemplates(crmTemplatesId: CrmTemplatesId): Future[Option[CrmTemplates]]
  def createCrmTemplates(entity: CreateCrmTemplates): Future[CrmTemplates]
  def updateCrmTemplates(entity: UpdateCrmTemplates): Future[Option[CrmTemplates]]
  def find(start: Int,
           end: Option[Int],
           questionId: Option[QuestionId],
           locale: Option[String]): Future[Seq[CrmTemplates]]
  def count(questionId: Option[QuestionId], locale: Option[String]): Future[Int]
  def getDefaultTemplate(locale: Option[String]): Future[Option[CrmTemplates]]
}

trait DefaultCrmTemplatesServiceComponent extends CrmTemplatesServiceComponent {
  this: PersistentCrmTemplatesServiceComponent with IdGeneratorComponent =>

  override lazy val crmTemplatesService: CrmTemplatesService = new DefaultCrmTemplatesService

  class DefaultCrmTemplatesService extends CrmTemplatesService {

    override def getCrmTemplates(crmTemplatesId: CrmTemplatesId): Future[Option[CrmTemplates]] = {
      persistentCrmTemplatesService.getById(crmTemplatesId)
    }

    override def createCrmTemplates(entity: CreateCrmTemplates): Future[CrmTemplates] = {
      val crmTemplates: CrmTemplates = CrmTemplates(
        crmTemplatesId = idGenerator.nextCrmTemplatesId(),
        questionId = entity.questionId,
        locale = entity.locale,
        registration = entity.registration,
        welcome = entity.welcome,
        proposalAccepted = entity.proposalAccepted,
        proposalRefused = entity.proposalRefused,
        forgottenPassword = entity.forgottenPassword,
        resendRegistration = entity.resendRegistration,
        proposalAcceptedOrganisation = entity.proposalAcceptedOrganisation,
        proposalRefusedOrganisation = entity.proposalRefusedOrganisation,
        forgottenPasswordOrganisation = entity.forgottenPasswordOrganisation,
        organisationEmailChangeConfirmation = entity.organisationEmailChangeConfirmation
      )
      persistentCrmTemplatesService.persist(crmTemplates)
    }

    override def updateCrmTemplates(entity: UpdateCrmTemplates): Future[Option[CrmTemplates]] = {
      persistentCrmTemplatesService.getById(entity.crmTemplatesId).flatMap {
        case Some(crmTemplates) =>
          persistentCrmTemplatesService
            .modify(
              crmTemplates.copy(
                registration = entity.registration,
                welcome = entity.welcome,
                proposalAccepted = entity.proposalAccepted,
                proposalRefused = entity.proposalRefused,
                forgottenPassword = entity.forgottenPassword,
                resendRegistration = entity.resendRegistration,
                proposalAcceptedOrganisation = entity.proposalAcceptedOrganisation,
                proposalRefusedOrganisation = entity.proposalRefusedOrganisation,
                forgottenPasswordOrganisation = entity.forgottenPasswordOrganisation,
                organisationEmailChangeConfirmation = entity.organisationEmailChangeConfirmation
              )
            )
            .map(Some.apply)
        case None => Future.successful(None)
      }
    }

    override def find(start: Int,
                      end: Option[Int],
                      questionId: Option[QuestionId],
                      locale: Option[String]): Future[Seq[CrmTemplates]] = {
      val searchByLocale: Option[String] = questionId match {
        case Some(_) => None
        case None    => locale
      }
      persistentCrmTemplatesService.find(start, end, questionId, searchByLocale).flatMap {
        case crmTemplates if crmTemplates.nonEmpty || locale.isEmpty => Future.successful(crmTemplates)
        case _                                                       => persistentCrmTemplatesService.find(start, end, None, locale)
      }
    }

    override def count(questionId: Option[QuestionId], locale: Option[String]): Future[Int] = {
      val searchByLocale: Option[String] = questionId match {
        case Some(_) => None
        case None    => locale
      }
      persistentCrmTemplatesService.count(questionId, searchByLocale)
    }

    override def getDefaultTemplate(locale: Option[String]): Future[Option[CrmTemplates]] = {
      persistentCrmTemplatesService.find(start = 0, end = Some(1), questionId = None, locale = locale).map {
        case templates if templates.isEmpty => None
        case templates                      => Some(templates.head)
      }
    }
  }
}

final case class CreateCrmTemplates(questionId: Option[QuestionId],
                                    locale: Option[String],
                                    registration: TemplateId,
                                    welcome: TemplateId,
                                    proposalAccepted: TemplateId,
                                    proposalRefused: TemplateId,
                                    forgottenPassword: TemplateId,
                                    resendRegistration: TemplateId,
                                    proposalAcceptedOrganisation: TemplateId,
                                    proposalRefusedOrganisation: TemplateId,
                                    forgottenPasswordOrganisation: TemplateId,
                                    organisationEmailChangeConfirmation: TemplateId) {
  validate(
    validateField(
      "registration",
      "invalid_content",
      Try(registration.value.toInt).isSuccess,
      "TemplateId must be of type Int"
    ),
    validateField("welcome", "invalid_content", Try(welcome.value.toInt).isSuccess, "TemplateId must be of type Int"),
    validateField(
      "proposalAccepted",
      "invalid_content",
      Try(proposalAccepted.value.toInt).isSuccess,
      "TemplateId must be of type Int"
    ),
    validateField(
      "proposalRefused",
      "invalid_content",
      Try(proposalRefused.value.toInt).isSuccess,
      "TemplateId must be of type Int"
    ),
    validateField(
      "forgottenPassword",
      "invalid_content",
      Try(forgottenPassword.value.toInt).isSuccess,
      "TemplateId must be of type Int"
    ),
    validateField(
      "proposalAcceptedOrganisation",
      "invalid_content",
      Try(proposalAcceptedOrganisation.value.toInt).isSuccess,
      "TemplateId must be of type Int"
    ),
    validateField(
      "proposalRefusedOrganisation",
      "invalid_content",
      Try(proposalRefusedOrganisation.value.toInt).isSuccess,
      "TemplateId must be of type Int"
    ),
    validateField(
      "forgottenPasswordOrganisation",
      "invalid_content",
      Try(forgottenPasswordOrganisation.value.toInt).isSuccess,
      "TemplateId must be of type Int"
    ),
    validateField(
      "organisationEmailChangeConfirmation",
      "invalid_content",
      Try(organisationEmailChangeConfirmation.value.toInt).isSuccess,
      "TemplateId must be of type Int"
    )
  )
}

final case class UpdateCrmTemplates(crmTemplatesId: CrmTemplatesId,
                                    registration: TemplateId,
                                    welcome: TemplateId,
                                    proposalAccepted: TemplateId,
                                    proposalRefused: TemplateId,
                                    forgottenPassword: TemplateId,
                                    resendRegistration: TemplateId,
                                    proposalAcceptedOrganisation: TemplateId,
                                    proposalRefusedOrganisation: TemplateId,
                                    forgottenPasswordOrganisation: TemplateId,
                                    organisationEmailChangeConfirmation: TemplateId) {
  validate(
    validateField(
      "registration",
      "invalid_content",
      Try(registration.value.toInt).isSuccess,
      "TemplateId must be of type Int"
    ),
    validateField("welcome", "invalid_content", Try(welcome.value.toInt).isSuccess, "TemplateId must be of type Int"),
    validateField(
      "proposalAccepted",
      "invalid_content",
      Try(proposalAccepted.value.toInt).isSuccess,
      "TemplateId must be of type Int"
    ),
    validateField(
      "proposalRefused",
      "invalid_content",
      Try(proposalRefused.value.toInt).isSuccess,
      "TemplateId must be of type Int"
    ),
    validateField(
      "forgottenPassword",
      "invalid_content",
      Try(forgottenPassword.value.toInt).isSuccess,
      "TemplateId must be of type Int"
    ),
    validateField(
      "proposalAcceptedOrganisation",
      "invalid_content",
      Try(proposalAcceptedOrganisation.value.toInt).isSuccess,
      "TemplateId must be of type Int"
    ),
    validateField(
      "proposalRefusedOrganisation",
      "invalid_content",
      Try(proposalRefusedOrganisation.value.toInt).isSuccess,
      "TemplateId must be of type Int"
    ),
    validateField(
      "forgottenPasswordOrganisation",
      "invalid_content",
      Try(forgottenPasswordOrganisation.value.toInt).isSuccess,
      "TemplateId must be of type Int"
    ),
    validateField(
      "organisationEmailChangeConfirmation",
      "invalid_content",
      Try(organisationEmailChangeConfirmation.value.toInt).isSuccess,
      "TemplateId must be of type Int"
    )
  )

}
