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
import org.make.api.MakeApi
import org.make.api.migrations.TagHelper.TagsDataLine
import org.make.core.question.Question
import org.make.core.reference.{Country, Language}
import org.make.core.tag.{TagDisplay, TagTypeId}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

trait TagHelper {

  implicit def executor: ExecutionContext

  def importTags(api: MakeApi, dataFile: String, question: Question): Future[Unit] = {
    val tags: Seq[TagHelper.TagsDataLine] =
      Source.fromResource(dataFile).getLines().toSeq.drop(1).flatMap(extractDataLine)

    api.tagService.findByQuestionId(question.questionId).flatMap { operationTags =>
      sequentially(tags) { tag =>
        if (!operationTags.map(_.label).contains(tag.label)) {
          api.tagService
            .createTag(
              label = tag.label,
              tagTypeId = tag.tagTypeId,
              question = question,
              display = tag.tagDisplay,
              weight = tag.weight
            )
            .flatMap(_ => Future.successful {})
        } else {
          Future.successful {}
        }
      }

    }
  }

  def extractDataLine(line: String): Option[TagsDataLine] = {
    line.drop(1).dropRight(1).split("""";"""") match {
      case Array(label, weight, tagType, tagDisplay, country, language, _) =>
        Some(
          TagsDataLine(
            label = label,
            tagTypeId = TagTypeId(tagType),
            tagDisplay = TagDisplay(tagDisplay),
            weight = weight.toFloat,
            country = Country(country),
            language = Language(language)
          )
        )
      case _ => None
    }
  }
}

object TagHelper {
  final case class TagsDataLine(
    label: String,
    tagTypeId: TagTypeId,
    tagDisplay: TagDisplay,
    weight: Float,
    country: Country,
    language: Language
  )

}
