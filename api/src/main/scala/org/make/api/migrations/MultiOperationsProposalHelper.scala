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
import org.make.api.migrations.ProposalHelper.FixtureDataLine
import org.make.core.SlugHelper
import org.make.core.proposal.{SearchFilters, SearchQuery, SlugSearchFilter}
import org.make.core.reference.{Country, Language}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait MultiOperationsProposalHelper extends Migration with ProposalHelper {

  implicit override val executor: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  override def extractDataLine(line: String): Option[ProposalHelper.FixtureDataLine] = {
    line.drop(1).dropRight(1).split("""";"""") match {
      case Array(email, content, tags) =>
        Some(
          FixtureDataLine(
            email = email,
            content = content,
            operation = None,
            tags = tags.split('|').toSeq,
            labels = Seq.empty,
            country = Country("FR"),
            language = Language("fr"),
            acceptProposal = true
          )
        )
      case _ => None
    }
  }

  def operationsProposalsSource: Map[String, String]

  override def initialize(api: MakeApi): Future[Unit] = {
    Future.successful {}
  }

  override def migrate(api: MakeApi): Future[Unit] = {
    sequentially(operationsProposalsSource.toSeq) {
      case (operationSlug, dataFile) =>
        api.operationService.findOneBySlug(operationSlug).flatMap {
          case None => Future.failed(new IllegalStateException(s"Operation $operationSlug doesn't exist"))
          case Some(operation) =>
            api.questionService.findQuestion(Some(operation.operationId), Country("FR"), Language("fr")).flatMap {
              case None =>
                Future.failed(new IllegalStateException(s"no question for operation $operationSlug doesn't exist"))
              case Some(question) =>
                api.tagService.findByQuestionId(question.questionId).flatMap { tags =>
                  sequentially(readProposalFile(dataFile)) { line =>
                    api.elasticsearchProposalAPI
                      .countProposals(
                        SearchQuery(filters =
                          Some(SearchFilters(slug = Some(SlugSearchFilter(SlugHelper(line.content)))))
                        )
                      )
                      .flatMap {
                        case count if count > 0 => Future.successful {}
                        case _ =>
                          insertProposal(api, line.content, line.email, question).flatMap { proposalId =>
                            val proposalTags = tags.filter(tag => line.tags.contains(tag.label)).map(_.tagId)
                            if (line.acceptProposal) {
                              acceptProposal(api, proposalId, line.content, question, proposalTags, line.labels)
                            } else {
                              Future.successful {}
                            }
                          }

                      }
                  }
                }
            }

        }
    }

  }
}
