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
import org.make.core.crmTemplate.CrmLanguageTemplate
import org.make.core.reference.Language
import scalikejdbc._

import scala.concurrent.Future

trait PersistentCrmLanguageTemplateServiceComponent {
  def persistentCrmLanguageTemplateService: PersistentCrmLanguageTemplateService
}

trait PersistentCrmLanguageTemplateService {
  def persist(templates: Seq[CrmLanguageTemplate]): Future[Seq[CrmLanguageTemplate]]
  def modify(templates: Seq[CrmLanguageTemplate]): Future[Seq[CrmLanguageTemplate]]
  def list(language: Language): Future[Seq[CrmLanguageTemplate]]
  def all(): Future[Seq[CrmLanguageTemplate]]
}

trait DefaultPersistentCrmLanguageTemplateServiceComponent extends PersistentCrmLanguageTemplateServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentCrmLanguageTemplateService: PersistentCrmLanguageTemplateService =
    new DefaultPersistentCrmLanguageTemplateService

  class DefaultPersistentCrmLanguageTemplateService extends PersistentCrmLanguageTemplateService with ShortenedNames {

    private val templates = SQLSyntaxSupportFactory[CrmLanguageTemplate]()
    private val t = templates.syntax

    override def persist(items: Seq[CrmLanguageTemplate]): Future[Seq[CrmLanguageTemplate]] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        items.foreach { template =>
          withSQL { insert.into(templates).namedValues(autoNamedValues(template, templates.column)) }.update().apply()
        }
      }).map(_ => items)
    }

    override def modify(items: Seq[CrmLanguageTemplate]): Future[Seq[CrmLanguageTemplate]] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        items.foreach { template =>
          withSQL {
            update(templates).set(autoNamedValues(template, templates.column, "id")).where.eq(t.id, template.id)
          }.update().apply()
        }
      }).map(_ => items)
    }

    override def list(language: Language): Future[Seq[CrmLanguageTemplate]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL { select.from(templates.as(t)).where.eq(t.language, language) }
          .map(templates.apply(t.resultName))
          .list()
          .apply()
      })
    }

    override def all(): Future[Seq[CrmLanguageTemplate]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL { select.from(templates.as(t)) }.map(templates.apply(t.resultName)).list().apply()
      })
    }
  }

}
