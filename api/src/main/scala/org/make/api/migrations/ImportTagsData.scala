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

import java.util.concurrent.Executors

import com.typesafe.scalalogging.StrictLogging
import org.make.api.MakeApi
import org.make.core.operation.OperationId
import org.make.core.reference.{Country, Language}
import org.make.core.tag.{TagDisplay, TagTypeId}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.io.Source

trait ImportTagsData extends Migration with StrictLogging {

  var operationId: OperationId = _

  override def initialize(api: MakeApi): Future[Unit] = Future.successful {}

  implicit val executor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  def extractDataLine(line: String): Option[ImportTagsData.TagsDataLine] = {
    line.drop(1).dropRight(1).split("""";"""") match {
      case Array(weight, label, tagType, tagDisplay, country, language) =>
        Some(
          ImportTagsData.TagsDataLine(
            label = label,
            tagTypeId = TagTypeId(tagType),
            tagDisplay = TagDisplay.matchTagDisplayOrDefault(tagDisplay),
            weight = weight.toFloat,
            country = Country(country),
            language = Language(language)
          )
        )
      case _ => None
    }
  }

  def dataResource: String

  override def migrate(api: MakeApi): Future[Unit] = {

    val tags: Seq[ImportTagsData.TagsDataLine] =
      Source.fromResource(dataResource).getLines().toSeq.drop(1).flatMap(extractDataLine)

    api.tagService.findByOperationId(operationId).flatMap {
      case operationTags if operationTags.isEmpty =>
        sequentially(tags) { tag =>
          api.tagService
            .createTag(
              label = tag.label,
              tagTypeId = tag.tagTypeId,
              operationId = Some(operationId),
              themeId = None,
              country = tag.country,
              language = tag.language,
              display = tag.tagDisplay,
              weight = tag.weight
            )
            .flatMap(_ => Future.successful {})
        }
      case _ => Future.successful {}
    }
  }
}

object ImportTagsData {
  case class TagsDataLine(label: String,
                          tagTypeId: TagTypeId,
                          tagDisplay: TagDisplay,
                          weight: Float,
                          country: Country,
                          language: Language)
}
