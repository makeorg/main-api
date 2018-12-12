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

package org.make.api.migrations
import org.make.core.reference.{Country, Language}

object G9ImportTagsDataDe extends ImportTagsData {

  override val operationSlug: String = G9Operation.operationSlug
  override val country: Country = Country("DE")
  override val language: Language = Language("de")

  override val dataResource: String = "fixtures/tags_g9-de.csv"
  override val runInProduction: Boolean = false
}
