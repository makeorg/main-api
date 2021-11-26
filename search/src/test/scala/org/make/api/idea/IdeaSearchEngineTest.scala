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

import akka.actor.typed.scaladsl.adapter._
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.akka.{AkkaHttpClient, AkkaHttpClientSettings}
import org.make.api.MakeUnitTest
import org.make.core.idea.{IdeaSearchFilters, IdeaSearchQuery}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors

class IdeaSearchEngineTest extends MakeUnitTest {

  private implicit val actorSystem: ActorSystem[Nothing] =
    ActorSystem(Behaviors.empty, "IdeaSearchEngineTest")

  private val ideaAlias: Index = "idea-index"
  private val client = ElasticClient(AkkaHttpClient(AkkaHttpClientSettings(List("fake:3232")))(actorSystem.toClassic))

  Feature("ordering in idea elastic search query") {
    ignore("any default sort is implemented") {
      Given("an IdeaSearchQuery with None order and None sort")
      val ideaSearchQuery: IdeaSearchQuery = IdeaSearchQuery(sort = None, order = None)
      When("I get raw elastic query")
      val request = search(ideaAlias).sortBy(IdeaSearchFilters.getSort(ideaSearchQuery))
      Then("sort is not present")
      client.show(request) shouldBe """{"version":true}"""
    }
  }

}
