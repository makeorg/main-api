package org.make.api.theme

import org.make.api.proposal.ProposalSearchEngineComponent
import org.make.api.technical.ShortenedNames
import org.make.core.proposal.{SearchFilters, SearchQuery, ThemeSearchFilter}
import org.make.core.reference._

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
  this: PersistentThemeServiceComponent with ProposalSearchEngineComponent =>

  val themeService = new ThemeService {

    override def findAll(): Future[Seq[Theme]] = {
      persistentThemeService.findAll().flatMap { themes =>
        Future.traverse(themes) { theme =>
          elasticsearchProposalAPI
            .countProposals(
              SearchQuery(filters = Some(SearchFilters(theme = Some(ThemeSearchFilter(Seq(theme.themeId.value))))))
            )
            .map { count =>
              theme.copy(proposalsCount = count)
            }
        }
      }
    }

    override def findByIds(themeIds: Seq[ThemeId]) = {
      findAll().map(_.filter(theme => themeIds.contains(theme.themeId)))
    }
  }
}
