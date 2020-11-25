/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.ShortenedNames
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ScalikeSupport._
import org.make.core.crmTemplate.{CrmQuestionTemplate, CrmQuestionTemplateId}
import org.make.core.question.QuestionId
import scalikejdbc._

import scala.concurrent.Future

trait PersistentCrmQuestionTemplateServiceComponent {
  def persistentCrmQuestionTemplateService: PersistentCrmQuestionTemplateService
}

trait PersistentCrmQuestionTemplateService {
  def list(questionId: QuestionId): Future[Seq[CrmQuestionTemplate]]
  def get(id: CrmQuestionTemplateId): Future[Option[CrmQuestionTemplate]]
  def persist(template: CrmQuestionTemplate): Future[CrmQuestionTemplate]
  def modify(template: CrmQuestionTemplate): Future[CrmQuestionTemplate]
  def remove(id: CrmQuestionTemplateId): Future[Unit]
}

trait DefaultPersistentCrmQuestionTemplateServiceComponent extends PersistentCrmQuestionTemplateServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override def persistentCrmQuestionTemplateService: PersistentCrmQuestionTemplateService =
    new DefaultPersistentCrmQuestionTemplateService

  class DefaultPersistentCrmQuestionTemplateService extends PersistentCrmQuestionTemplateService with ShortenedNames {

    private val templates = SQLSyntaxSupportFactory[CrmQuestionTemplate]()
    private val t = templates.syntax

    override def list(questionId: QuestionId): Future[Seq[CrmQuestionTemplate]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL { select.from(templates.as(t)).where.eq(t.questionId, questionId) }
          .map(templates.apply(t.resultName))
          .list()
          .apply()
      })
    }

    override def get(id: CrmQuestionTemplateId): Future[Option[CrmQuestionTemplate]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL { select.from(templates.as(t)).where.eq(t.id, id) }
          .map(templates.apply(t.resultName))
          .single()
          .apply()
      })
    }

    override def persist(template: CrmQuestionTemplate): Future[CrmQuestionTemplate] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL { insert.into(templates).namedValues(autoNamedValues(template, templates.column)) }.update().apply()
      }).map(_ => template)
    }

    override def modify(template: CrmQuestionTemplate): Future[CrmQuestionTemplate] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(templates).set(autoNamedValues(template, templates.column, "id")).where.eq(t.id, template.id)
        }.update().apply()
      }).map(_ => template)
    }

    override def remove(id: CrmQuestionTemplateId): Future[Unit] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL { delete.from(templates).where.eq(t.id, id) }.execute().apply()
      })
    }
  }

}
