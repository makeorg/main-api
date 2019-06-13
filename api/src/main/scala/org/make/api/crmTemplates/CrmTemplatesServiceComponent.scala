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
}

trait DefaultCrmTemplatesServiceComponent extends CrmTemplatesServiceComponent {
  this: PersistentCrmTemplatesServiceComponent with IdGeneratorComponent =>

  val crmTemplatesService: CrmTemplatesService = new DefaultCrmTemplatesService

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
        proposalAcceptedOrganisation = entity.proposalAcceptedOrganisation,
        proposalRefusedOrganisation = entity.proposalRefusedOrganisation,
        forgottenPasswordOrganisation = entity.forgottenPasswordOrganisation,
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
                proposalAcceptedOrganisation = entity.proposalAcceptedOrganisation,
                proposalRefusedOrganisation = entity.proposalRefusedOrganisation,
                forgottenPasswordOrganisation = entity.forgottenPasswordOrganisation
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
  }
}

final case class CreateCrmTemplates(questionId: Option[QuestionId],
                                    locale: Option[String],
                                    registration: TemplateId,
                                    welcome: TemplateId,
                                    proposalAccepted: TemplateId,
                                    proposalRefused: TemplateId,
                                    forgottenPassword: TemplateId,
                                    proposalAcceptedOrganisation: TemplateId,
                                    proposalRefusedOrganisation: TemplateId,
                                    forgottenPasswordOrganisation: TemplateId)

final case class UpdateCrmTemplates(crmTemplatesId: CrmTemplatesId,
                                    registration: TemplateId,
                                    welcome: TemplateId,
                                    proposalAccepted: TemplateId,
                                    proposalRefused: TemplateId,
                                    forgottenPassword: TemplateId,
                                    proposalAcceptedOrganisation: TemplateId,
                                    proposalRefusedOrganisation: TemplateId,
                                    forgottenPasswordOrganisation: TemplateId)
