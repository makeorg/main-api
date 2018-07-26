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

import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.{ElasticsearchClientUri, IndexAndType}
import org.make.api.MakeUnitTest
import org.make.api.technical.elasticsearch.{ElasticsearchConfiguration, ElasticsearchConfigurationComponent}
import org.make.core.idea.{IdeaSearchFilters, IdeaSearchQuery}

class IdeaSearchEngineTest
    extends MakeUnitTest
    with DefaultIdeaSearchEngineComponent
    with ElasticsearchConfigurationComponent {
  override def elasticsearchConfiguration: ElasticsearchConfiguration = mock[ElasticsearchConfiguration]

  private val ideaAlias: IndexAndType = "idea-index" / "idea-type"
  private val client = HttpClient(ElasticsearchClientUri("elasticsearch://fake:3232"))

  feature("ordering in idea elastic search query") {
    scenario("any default sort is implemented") {
      Given("an IdeaSearchQuery with None order and None sort")
      val ideaSearchQuery: IdeaSearchQuery = IdeaSearchQuery(sort = None, order = None)
      When("I get raw elastic query")
      val request = searchWithType(ideaAlias).sortBy(IdeaSearchFilters.getSort(ideaSearchQuery))
      Then("sort is not present")
      client.show(request)(SearchShow) shouldBe """{"version":true}"""
    }
  }

}
