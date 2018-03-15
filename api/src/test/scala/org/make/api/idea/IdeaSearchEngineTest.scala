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

  private val ideaAlias: IndexAndType = elasticsearchConfiguration.aliasName / IdeaSearchEngine.ideaIndexName
  private val client = HttpClient(ElasticsearchClientUri("elasticsearch://fake:3232"))

  feature("ordering in idea elastic search query") {
    scenario("any default sort is implemented") {
      Given("an IdeaSearchQuery with None order and None sort")
      val ideaSearchQuery: IdeaSearchQuery = IdeaSearchQuery(sort = None, order = None)
      When("I get raw elastic query")
      val request = search(ideaAlias).sortBy(IdeaSearchFilters.getSort(ideaSearchQuery))
      Then("sort is not present")
      client.show(request) shouldBe "{}"
    }
  }

}
