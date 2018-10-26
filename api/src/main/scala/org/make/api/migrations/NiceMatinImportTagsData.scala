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
import org.make.api.migrations.TagHelper.TagsDataLine
import org.make.core.reference.{Country, Language}
import org.make.core.tag.{TagDisplay, TagTypeId}

object NiceMatinImportTagsData extends ImportTagsData {

  override val operationSlug: String = NiceMatinOperation.operationSlug
  override val country: Country = Country("FR")
  override val language: Language = Language("fr")

  override val dataResource: String = "fixtures/huffingpost/tags_ecologie.csv"

  override def extractDataLine(line: String): Option[TagHelper.TagsDataLine] = {
    line.drop(1).dropRight(1).split("""";"""") match {
      case Array(label, weight, tagType, tagDisplay, _, _, _) =>
        Some(
          TagsDataLine(
            label = label,
            tagTypeId = TagTypeId(tagType),
            tagDisplay = TagDisplay.matchTagDisplayOrDefault(tagDisplay),
            weight = weight.toFloat,
            country = country,
            language = language
          )
        )
      case _ => None
    }
  }

  override val runInProduction: Boolean = true
}