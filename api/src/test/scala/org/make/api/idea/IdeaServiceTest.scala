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

package org.make.api.idea

import org.make.api.MakeUnitTest
import org.make.api.technical.{EventBusService, EventBusServiceComponent}
import org.make.core.DateHelper
import org.make.core.idea.indexed.IdeaSearchResult
import org.make.core.idea.{Idea, IdeaId, IdeaSearchQuery}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class IdeaServiceTest
    extends MakeUnitTest
    with DefaultIdeaServiceComponent
    with PersistentIdeaServiceComponent
    with IdeaSearchEngineComponent
    with EventBusServiceComponent {

  override val eventBusService: EventBusService = mock[EventBusService]
  override val persistentIdeaService: PersistentIdeaService = mock[PersistentIdeaService]
  override val elasticsearchIdeaAPI: IdeaSearchEngine = mock[IdeaSearchEngine]

  feature("fetch all ideas") {
    scenario("get all ideas") {
      Given("a list of idea")
      When("fetch this list")
      Then("Elastic search idea Api will be callend")

      Mockito
        .when(elasticsearchIdeaAPI.searchIdeas(any[IdeaSearchQuery]))
        .thenReturn(Future.successful(IdeaSearchResult.empty))

      val futureIdeas =
        ideaService.fetchAll(IdeaSearchQuery(filters = None, limit = None, skip = None, language = Some("en")))

      whenReady(futureIdeas, Timeout(3.seconds)) { ideas =>
        ideas.total shouldBe 0
      }
    }
  }

  feature("fetch one idea") {
    scenario("get on idea") {
      Given("an idea")
      When("i fetch the idea")
      Then("Persistent Idea service will be called")

      val persistentIdea =
        Idea(IdeaId("foo-idea"), "fooIdea", createdAt = Some(DateHelper.now()), updatedAt = Some(DateHelper.now()))

      Mockito
        .when(persistentIdeaService.findOne(any[IdeaId]))
        .thenReturn(Future.successful(Some(persistentIdea)))

      val futureIdea = ideaService.fetchOne(IdeaId("foo-idea"))

      whenReady(futureIdea, Timeout(3.seconds)) { idea =>
        idea shouldBe Some(persistentIdea)
      }
    }
  }
}
