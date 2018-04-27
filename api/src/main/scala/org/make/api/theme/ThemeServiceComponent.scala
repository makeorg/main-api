package org.make.api.theme

import org.make.api.proposal.ProposalSearchEngineComponent
import org.make.api.tag.DefaultPersistentTagServiceComponent
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
    with DefaultPersistentTagServiceComponent =>

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

          val maybeTags: Future[Seq[Tag]] = persistentTagService.findByThemeId(theme.themeId)

          for {
            proposalsCount <- maybeProposalsCount
            votesCount <- maybeVotesCount
            tags <- maybeTags
          } yield
            theme.copy(proposalsCount = proposalsCount, votesCount = votesCount, tags = tags)
        }
      }
    }

    override def findByIds(themeIds: Seq[ThemeId]): Future[Seq[Theme]] = {
      findAll().map(_.filter(theme => themeIds.contains(theme.themeId)))
    }
  }
}
