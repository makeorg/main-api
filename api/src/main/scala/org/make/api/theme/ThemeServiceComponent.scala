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

package org.make.api.theme

import org.make.api.proposal.ProposalSearchEngineComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.tag.TagServiceComponent
import org.make.api.technical.ShortenedNames
import org.make.core.proposal.{SearchFilters, SearchQuery, ThemeSearchFilter}
import org.make.core.reference._
import org.make.core.tag.Tag

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ThemeServiceComponent {
  def themeService: ThemeService
}

trait ThemeService extends ShortenedNames {
  def findAll(): Future[Seq[Theme]]
  def findByIds(themeIds: Seq[ThemeId]): Future[Seq[Theme]]
}

trait DefaultThemeServiceComponent extends ThemeServiceComponent with ShortenedNames {
  this: PersistentThemeServiceComponent
    with ProposalSearchEngineComponent
    with TagServiceComponent
    with QuestionServiceComponent =>

  val themeService: ThemeService = new ThemeService {

    override def findAll(): Future[Seq[Theme]] = {
      persistentThemeService.findAll().flatMap { themes =>
        Future.traverse(themes) { theme =>
          val maybeProposalsCount = elasticsearchProposalAPI
            .countProposals(
              SearchQuery(filters = Some(SearchFilters(theme = Some(ThemeSearchFilter(Seq(theme.themeId))))))
            )

          val votesCount = elasticsearchProposalAPI
            .countVotedProposals(
              SearchQuery(filters = Some(SearchFilters(theme = Some(ThemeSearchFilter(Seq(theme.themeId))))))
            )

          val retrieveQuestion = questionService
            .findQuestion(
              Some(theme.themeId),
              None,
              theme.country,
              theme.translations.headOption.map(_.language).getOrElse(Language("fr"))
            )

          val tags: Future[Seq[Tag]] =
            retrieveQuestion
              .flatMap(
                maybeQuestion =>
                  maybeQuestion
                    .map(question => tagService.findByQuestionId(question.questionId))
                    .getOrElse(Future.successful(Seq.empty))
              )

          for {
            proposalsCount <- maybeProposalsCount
            maybeQuestion  <- retrieveQuestion
            votesCount     <- votesCount
            tags           <- tags
          } yield
            theme.copy(
              proposalsCount = proposalsCount,
              votesCount = votesCount,
              tags = tags,
              questionId = maybeQuestion.map(_.questionId)
            )
        }
      }
    }

    override def findByIds(themeIds: Seq[ThemeId]): Future[Seq[Theme]] = {
      findAll().map(_.filter(theme => themeIds.contains(theme.themeId)))
    }
  }
}
