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

import org.make.api.MakeUnitTest
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.proposal.{ProposalSearchEngine, ProposalSearchEngineComponent}
import org.make.api.tag.{TagService, TagServiceComponent}
import org.make.core.SlugHelper
import org.make.core.proposal.SearchQuery
import org.make.core.reference._
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class ThemeServiceTest
    extends MakeUnitTest
    with DefaultThemeServiceComponent
    with TagServiceComponent
    with MakeDBExecutionContextComponent
    with PersistentThemeServiceComponent
    with ProposalSearchEngineComponent {

  override val tagService: TagService = mock[TagService]
  override val persistentThemeService: PersistentThemeService = mock[PersistentThemeService]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override def writeExecutionContext: ExecutionContext = mock[ExecutionContext]
  override def readExecutionContext: ExecutionContext = mock[ExecutionContext]

  val fooTheme = Theme(
    themeId = ThemeId("foo"),
    translations = Seq(ThemeTranslation(slug = SlugHelper("foo"), title = "Foo", language = "lg")),
    actionsCount = 7,
    proposalsCount = 0,
    votesCount = 0,
    country = "WE",
    color = "#00FFFF",
    gradient = Some(GradientColor("#0FF", "#0F0"))
  )

  val barTheme = Theme(
    themeId = ThemeId("bar"),
    translations = Seq(ThemeTranslation(slug = SlugHelper("bar"), title = "Bar", language = "lg")),
    actionsCount = 7,
    proposalsCount = 0,
    votesCount = 0,
    country = "WE",
    color = "#00FFFF",
    gradient = Some(GradientColor("#0FF", "#0F0"))
  )

  val fooTag = Tag(
    tagId = TagId("fooTag"),
    label = "foo",
    display = TagDisplay.Displayed,
    tagTypeId = TagTypeId("tagType"),
    weight = 1,
    themeId = Some(ThemeId("fooTheme")),
    operationId = None,
    country = "FR",
    language = "fr"
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

      Mockito
        .when(tagService.findByThemeId(any[ThemeId]))
        .thenReturn(Future.successful(Seq.empty))

      val futureThemes = themeService.findAll()

      whenReady(futureThemes, Timeout(3.seconds)) { themes =>
        themes.size shouldBe 2
        themes.foreach { theme =>
          theme.proposalsCount shouldBe 5
          theme.votesCount shouldBe 10
        }
      }

    }

    scenario("get all themes and get tag from persistent Tag") {
      Given("a list of theme")
      When("fetch this list")
      Then("tag is fetched from persistent tag")

      Mockito
        .when(persistentThemeService.findAll())
        .thenReturn(Future.successful(Seq(fooTheme, barTheme)))

      Mockito
        .when(tagService.findByThemeId(ArgumentMatchers.eq(ThemeId("foo"))))
        .thenReturn(Future.successful(Seq(fooTag)))

      Mockito
        .when(tagService.findByThemeId(ArgumentMatchers.eq(ThemeId("bar"))))
        .thenReturn(Future.successful(Seq.empty))

      val futureThemes = themeService.findAll()

      whenReady(futureThemes, Timeout(3.seconds)) { themes =>
        val fooTheme: Theme = themes.filter(theme => theme.themeId.value == "foo").head
        fooTheme.tags.size shouldBe 1
      }
    }

  }

}
