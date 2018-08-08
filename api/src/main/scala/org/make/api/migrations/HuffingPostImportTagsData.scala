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

import org.make.api.MakeApi

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.io.Source

object HuffingPostImportTagsData extends Migration {

  implicit val executor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  val operationsTagsSource: Map[String, String] = Map(
    "politique-huffpost" -> "fixtures/huffingpost/tags_politique.csv",
    "economie-huffpost" -> "fixtures/huffingpost/tags_economie.csv",
    "international-huffpost" -> "fixtures/huffingpost/tags_international.csv",
    "culture-huffpost" -> "fixtures/huffingpost/tags_culture.csv",
    "ecologie-huffpost" -> "fixtures/huffingpost/tags_ecologie.csv",
    "societe-huffpost" -> "fixtures/huffingpost/tags_societe.csv"
  )

  override def initialize(api: MakeApi): Future[Unit] = Future.successful{}
  override def migrate(api: MakeApi): Future[Unit] = {
    Future {
      operationsTagsSource.foreach {
        case (operationSlug, tagsDataSource) =>
          retryableFuture(
            api.operationService.findOneBySlug(operationSlug)
          ) .flatMap {
            case Some(operation) =>
              val tags: Seq[ImportTagsData.TagsDataLine] =
                Source.fromResource(tagsDataSource).getLines().toSeq.drop(1).flatMap(ImportTagsData.extractDataLine)

              api.tagService.findByOperationId(operation.operationId).flatMap {
                case operationTags if operationTags.isEmpty =>
                  sequentially(tags) { tag =>
                    api.tagService
                      .createTag(
                        label = tag.label,
                        tagTypeId = tag.tagTypeId,
                        operationId = Some(operation.operationId),
                        themeId = None,
                        country = tag.country,
                        language = tag.language,
                        display = tag.tagDisplay,
                        weight = tag.weight
                      )
                      .flatMap(_ => Future.successful {})
                  }
                case _ => Future.successful {
                  println(s"tag is emptty for operation $operationSlug")
                }
              }
              Future.successful{}
            case None =>
              Future.failed(new RuntimeException(s"Error When getting operation ${operationSlug} when importing tags"))
          }
        case _ =>
          Future.failed(new RuntimeException(s"Error When importingHuffingPost tags"))
      }
    }
  }

  override val runInProduction: Boolean = true
}
