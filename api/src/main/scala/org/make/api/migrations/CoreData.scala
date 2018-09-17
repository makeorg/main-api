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
import org.make.api.migrations.ProposalHelper.{FixtureDataLine, UserInfo}
import org.make.core.proposal.{SearchFilters, SearchQuery, SlugSearchFilter}
import org.make.core.reference.{Country, LabelId, Language, ThemeId}
import org.make.core.{RequestContext, SlugHelper}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object CoreData extends Migration with ProposalHelper {

  val dataResource: String = "fixtures/proposals.csv"
  val requestContext: RequestContext = RequestContext.empty

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

  override def extractDataLine(line: String): Option[FixtureDataLine] = {
    line.drop(1).dropRight(1).split("""";"""") match {
      case Array(email, content, theme, tags, labels, country, language) =>
        Some(
          FixtureDataLine(
            email = email,
            content = content,
            theme = Some(ThemeId(theme)),
            operation = None,
            tags = tags.split('|').toSeq,
            labels = labels.split('|').map(LabelId.apply).toSeq,
            country = Country(country),
            language = Language(language),
            acceptProposal = true
          )
        )
      case Array(email, content, theme, tags, country, language) =>
        Some(
          FixtureDataLine(
            email = email,
            content = content,
            theme = Some(ThemeId(theme)),
            operation = None,
            tags = tags.split('|').toSeq,
            labels = Seq.empty,
            country = Country(country),
            language = Language(language),
            acceptProposal = true
          )
        )
      case _ => None
    }
  }

  override def initialize(api: MakeApi): Future[Unit] = Future.successful {}

  override def migrate(api: MakeApi): Future[Unit] = {
    sequentially(readProposalFile(dataResource).groupBy(_.theme).toSeq) {
      case (maybeThemeId, proposals) =>
        api.questionService.findQuestion(maybeThemeId, None, Country("FR"), Language("fr")).flatMap {
          case None => Future.failed(new IllegalStateException(s"no question for theme $maybeThemeId"))
          case Some(question) =>
            api.tagService.findByQuestionId(question.questionId).flatMap { tags =>
              sequentially(proposals) { proposal =>
                api.elasticsearchProposalAPI
                  .countProposals(
                    SearchQuery(
                      filters = Some(SearchFilters(slug = Some(SlugSearchFilter(SlugHelper(proposal.content)))))
                    )
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
    }

  }

  implicit override val executor: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
  override val runInProduction: Boolean = false
}
