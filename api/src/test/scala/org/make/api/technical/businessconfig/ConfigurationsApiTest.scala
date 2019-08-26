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

package org.make.api.technical.businessconfig

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.theme.{ThemeService, ThemeServiceComponent}
import org.make.core.{FrontConfiguration, SlugHelper}
import org.make.core.question.QuestionId
import org.make.core.reference._
import org.make.core.tag.{Tag, TagDisplay, TagTypeId}
import org.mockito.Mockito._

import scala.concurrent.Future

class ConfigurationsApiTest extends MakeApiTestBase with DefaultConfigurationsApiComponent with ThemeServiceComponent {

  override val themeService: ThemeService = mock[ThemeService]

  def newTag(label: String): Tag = Tag(
    tagId = idGenerator.nextTagId(),
    label = label,
    display = TagDisplay.Inherit,
    weight = 0f,
    tagTypeId = TagTypeId("11111111-1111-1111-1111-11111111111"),
    operationId = None,
    themeId = None,
    country = Country("FR"),
    language = Language("fr"),
    questionId = None
  )
  val winterIsComingTags: Seq[Tag] = Seq(newTag("Stark"), newTag("Targaryen"), newTag("Lannister"))
  val winterIsHereTags: Seq[Tag] = Seq(newTag("White walker"))
  val themesList: Seq[Theme] = Seq(
    Theme(
      themeId = ThemeId("winterIsComingId"),
      questionId = Some(QuestionId("who-died-yesterday?")),
      translations = Seq(
        ThemeTranslation(slug = SlugHelper("winter-is-coming"), title = "Winter is coming", language = Language("dk"))
      ),
      actionsCount = 7,
      proposalsCount = 42,
      votesCount = 0,
      country = Country("WE"),
      color = "#00FFFF",
      gradient = Some(GradientColor("#0FF", "#0F0")),
      tags = winterIsComingTags
    ),
    Theme(
      themeId = ThemeId("winterIsHere"),
      questionId = Some(QuestionId("how-to-stop-the-undead?")),
      translations =
        Seq(ThemeTranslation(slug = SlugHelper("winter-is-here"), title = "Winter is here", language = Language("dk"))),
      actionsCount = 0,
      proposalsCount = 1000,
      votesCount = 0,
      country = Country("WE"),
      color = "#FFFFdd",
      gradient = Some(GradientColor("#FFC", "#FFF")),
      tags = winterIsHereTags
    )
  )

  when(themeService.findAll())
    .thenReturn(Future.successful(themesList))

  val routes: Route = sealRoute(configurationsApi.routes)

  feature("front business config") {
    scenario("unauthenticated") {
      Given("an un authenticated user")
      When("the user wants to get the front business config")
      Then("the front business config is returned")
      Get("/configurations/front") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val businessConfig: FrontConfiguration = entityAs[FrontConfiguration]
        businessConfig.themes.forall(themesList.contains) should be(true)
      }
    }

  }
}
