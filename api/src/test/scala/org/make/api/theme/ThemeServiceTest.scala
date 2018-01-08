package org.make.api.theme

import org.make.api.MakeUnitTest
import org.make.api.proposal.{ProposalSearchEngine, ProposalSearchEngineComponent}
import org.make.core.SlugHelper
import org.make.core.proposal.SearchQuery
import org.make.core.reference._
import org.mockito.Mockito
import org.mockito.ArgumentMatchers.any
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ThemeServiceTest
    extends MakeUnitTest
    with DefaultThemeServiceComponent
    with PersistentThemeServiceComponent
    with ProposalSearchEngineComponent {

  override val persistentThemeService: PersistentThemeService = mock[PersistentThemeService]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]

  val fooTheme = Theme(
    themeId = ThemeId("foo"),
    translations =
      Seq(ThemeTranslation(slug = SlugHelper("foo"), title = "Foo", language = "lg")),
    actionsCount = 7,
    proposalsCount = 0,
    votesCount = 0,
    country = "WE",
    color = "#00FFFF",
    gradient = Some(GradientColor("#0FF", "#0F0"))
  )

  val barTheme = Theme(
    themeId = ThemeId("bar"),
    translations =
      Seq(ThemeTranslation(slug = SlugHelper("bar"), title = "Bar", language = "lg")),
    actionsCount = 7,
    proposalsCount = 0,
    votesCount = 0,
    country = "WE",
    color = "#00FFFF",
    gradient = Some(GradientColor("#0FF", "#0F0"))
  )

  feature("get all themes") {
    scenario("get all themes and count number of proposal and vote") {
      Given("a list of theme")
      When("fetch this list")
      Then("proposals and vote number will be calculated")

      Mockito
        .when(persistentThemeService.findAll())
        .thenReturn(Future.successful(Seq(fooTheme, barTheme)))

      Mockito
        .when(elasticsearchProposalAPI.countProposals(any[SearchQuery]))
        .thenReturn(Future.successful(5))

      Mockito
        .when(elasticsearchProposalAPI.countVotedProposals(any[SearchQuery]))
        .thenReturn(Future.successful(10))

      val futureThemes = themeService.findAll()

      whenReady(futureThemes, Timeout(3.seconds)) { themes =>
        themes.size shouldBe 2
        themes.foreach { theme =>
          theme.proposalsCount shouldBe 5
          theme.votesCount shouldBe 10
        }
      }

    }
  }

}
