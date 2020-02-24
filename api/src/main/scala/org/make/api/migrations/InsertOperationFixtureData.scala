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
import org.make.api.migrations.ProposalHelper.{FixtureDataLine, UserInfo}
import org.make.core.SlugHelper
import org.make.core.proposal.{SearchFilters, SearchQuery, SlugSearchFilter}
import org.make.core.question.Question
import org.make.core.reference.{Country, Language}
import org.make.core.tag.Tag

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait InsertOperationFixtureData extends Migration with ProposalHelper with StrictLogging {

  def operationSlug: String
  def country: Country
  def language: Language

  // This method _must_ be implemented with a def, or else it will lead to NPE at startup
  def dataResource: String

  var question: Question = _
  var tags: Seq[Tag] = _

  override def extractDataLine(line: String): Option[FixtureDataLine] = {
    line.drop(1).dropRight(1).split("""";"""") match {
      case Array(email, content, proposalTags, country, language) =>
        Some(
          FixtureDataLine(
            email = email,
            content = content,
            operation = None,
            tags = proposalTags.split('|').toSeq,
            labels = Seq.empty,
            country = Country(country),
            language = Language(language),
            acceptProposal = true
          )
        )
      case _ => None
    }
  }

  override val users: Seq[UserInfo] = {
    readProposalFile(dataResource).map { line =>
      UserInfo(
        email = line.email,
        firstName = extractFirstNameFromEmail(line.email),
        age = userAge(line.email),
        country = line.country,
        language = line.language
      )
    }.distinct
  }

  override def initialize(api: MakeApi): Future[Unit] = {
    api.operationService.findOneBySlug(operationSlug).flatMap {
      case None => Future.failed(new IllegalStateException(s"operation $operationSlug doesn't exist"))
      case Some(operation) =>
        api.questionService.findQuestion(Some(operation.operationId), country, language).flatMap {
          case None => Future.failed(new IllegalStateException(s"No question for operation $operationSlug"))
          case Some(q) =>
            this.question = q
            api.tagService.findByQuestionId(q.questionId).flatMap { tagList =>
              this.tags = tagList
              Future.successful {}
            }
        }
    }
  }

  implicit override val executor: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  override def migrate(api: MakeApi): Future[Unit] = {
    sequentially(readProposalFile(dataResource)) { proposal =>
      api.elasticsearchProposalAPI
        .countProposals(
          SearchQuery(filters = Some(SearchFilters(slug = Some(SlugSearchFilter(SlugHelper(proposal.content))))))
        )
        .flatMap {
          case total if total > 0 => Future.successful {}
          case _ =>
            insertProposal(api, proposal.content, proposal.email, question).flatMap { proposalId =>
              val proposalTags = tags.filter(tag => proposal.tags.contains(tag.label)).map(_.tagId)
              acceptProposal(api, proposalId, proposal.content, question, proposalTags, proposal.labels)
            }
        }
    }
  }

}
