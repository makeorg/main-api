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
import org.make.api.migrations.TagHelper.TagsDataLine
import org.make.core.question.Question
import org.make.core.reference.{Country, Language}
import org.make.core.tag.{TagDisplay, TagTypeId}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait ImportTagsData extends Migration with StrictLogging with TagHelper {

  def operationSlug: String
  def country: Country
  def language: Language

  var question: Question = _

  override def initialize(api: MakeApi): Future[Unit] = {
    api.operationService
      .findOneBySlug(operationSlug)
      .flatMap {
        case None =>
          throw new IllegalStateException(s"Unable to find an operation with slug $operationSlug")
        case Some(operation) =>
          api.questionService.findQuestion(Some(operation.operationId), country, language)
      }
      .map {
        case None =>
          throw new IllegalStateException(s"Unable to find the question for the operation with slug $operationSlug")
        case Some(q) =>
          this.question = q
          Future.successful {}
      }
  }

  implicit override val executor: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  def dataResource: String

  override def extractDataLine(line: String): Option[TagsDataLine] = {
    line.drop(1).dropRight(1).split("""";"""") match {
      case Array(weight, label, tagType, tagDisplay, country, language) =>
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

  override def migrate(api: MakeApi): Future[Unit] = {
    importTags(api, dataResource, question)
  }
}

object ImportTagsData {}
