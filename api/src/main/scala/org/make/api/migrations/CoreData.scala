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

import org.make.api.migrations.InsertFixtureData.FixtureDataLine
import org.make.core.RequestContext
import org.make.core.reference.{LabelId, ThemeId}
import org.make.core.tag.TagId

object CoreData extends InsertFixtureData {

  override val dataResource: String = "fixtures/proposals.csv"
  var localRequestContext: RequestContext = RequestContext.empty
  override def requestContext: RequestContext = localRequestContext

  override def extractDataLine(line: String): Option[FixtureDataLine] = {
    line.drop(1).dropRight(1).split("""";"""") match {
      case Array(email, content, theme, tags, labels, country, language) =>
        Some(
          FixtureDataLine(
            email = email,
            content = content,
            theme = Some(ThemeId(theme)),
            operation = None,
            tags = tags.split('|').map(TagId.apply).toSeq,
            labels = labels.split('|').map(LabelId.apply).toSeq,
            country = country,
            language = language
          )
        )
      case Array(email, content, theme, tags, country, language) =>
        Some(
          FixtureDataLine(
            email = email,
            content = content,
            theme = Some(ThemeId(theme)),
            operation = None,
            tags = tags.split('|').map(TagId.apply).toSeq,
            labels = Seq.empty,
            country = country,
            language = language
          )
        )
      case _ => None
    }
  }

  override val runInProduction: Boolean = false
}
