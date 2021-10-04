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

import org.make.core.crmTemplate.{
  CrmLanguageTemplate,
  CrmQuestionTemplate,
  CrmQuestionTemplateId,
  CrmTemplateKind,
  TemplateId
}
import org.make.core.question.QuestionId

import scala.concurrent.Future
import org.make.core.reference.{Country, Language}

import scala.collection.SortedMap

trait CrmTemplatesServiceComponent {
  def crmTemplatesService: CrmTemplatesService
}

trait CrmTemplatesService {
  def find(kind: CrmTemplateKind, questionId: Option[QuestionId], country: Country): Future[Option[TemplateId]]

  def listByLanguage(): Future[SortedMap[Language, CrmTemplateKind => CrmLanguageTemplate]]
  def get(language: Language): Future[Option[CrmTemplateKind       => CrmLanguageTemplate]]
  def create(language: Language, values: CrmTemplateKind => TemplateId): Future[CrmTemplateKind => CrmLanguageTemplate]
  def update(language: Language, values: CrmTemplateKind => TemplateId): Future[CrmTemplateKind => CrmLanguageTemplate]

  def list(questionId: QuestionId): Future[Seq[CrmQuestionTemplate]]
  def get(id: CrmQuestionTemplateId): Future[Option[CrmQuestionTemplate]]
  def create(template: CrmQuestionTemplate): Future[CrmQuestionTemplate]
  def update(template: CrmQuestionTemplate): Future[CrmQuestionTemplate]
  def delete(id: CrmQuestionTemplateId): Future[Unit]
}
