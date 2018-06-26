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
  this: PersistentThemeServiceComponent with ProposalSearchEngineComponent with TagServiceComponent =>

  val themeService = new ThemeService {

    override def findAll(): Future[Seq[Theme]] = {
      persistentThemeService.findAll().flatMap { themes =>
        Future.traverse(themes) { theme =>
          val maybeProposalsCount = elasticsearchProposalAPI
            .countProposals(
              SearchQuery(filters = Some(SearchFilters(theme = Some(ThemeSearchFilter(Seq(theme.themeId))))))
            )

          val maybeVotesCount = elasticsearchProposalAPI
            .countVotedProposals(
              SearchQuery(filters = Some(SearchFilters(theme = Some(ThemeSearchFilter(Seq(theme.themeId))))))
            )

          val maybeTags: Future[Seq[Tag]] = tagService.findByThemeId(theme.themeId)

          for {
            proposalsCount <- maybeProposalsCount
            votesCount     <- maybeVotesCount
            tags           <- maybeTags
          } yield theme.copy(proposalsCount = proposalsCount, votesCount = votesCount, tags = tags)
        }
      }
    }

    override def findByIds(themeIds: Seq[ThemeId]): Future[Seq[Theme]] = {
      findAll().map(_.filter(theme => themeIds.contains(theme.themeId)))
    }
  }
}
