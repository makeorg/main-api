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

import org.make.core.crmTemplate.CrmLanguageTemplate
import org.make.core.reference.Language

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
