package org.make.core.proposal

import com.sksamuel.elastic4s.ElasticApi
import com.sksamuel.elastic4s.http.ElasticDsl
import com.sksamuel.elastic4s.searches.sort.FieldSortDefinition
import org.elasticsearch.search.sort.SortOrder
import org.make.core.common.indexed.{Sort => IndexedSort}
import org.make.core.proposal.indexed.ProposalElasticsearchFieldNames
import org.make.core.reference.{LabelId, TagId, ThemeId}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

class SearchQueryTest extends FeatureSpec with GivenWhenThen with MockitoSugar with Matchers with ElasticDsl {
  val themeValue = ThemeId("Theme")
  val themeFilter = ThemeSearchFilter(Seq(themeValue))
  val tagValue = TagId("Tag1")
  val tagsFilter = TagsSearchFilter(Seq(tagValue))
  val labelValue = LabelId("Label")
  val labelsFilter = LabelsSearchFilter(Seq(labelValue))
  val trendingValue = "Trending"
  val trendingFilter = TrendingSearchFilter(trendingValue)
  val textValue = "text to search"
  val contentFilter = ContentSearchFilter(text = textValue, fuzzy = None)
  val statusFilter = StatusSearchFilter(status = Seq(ProposalStatus.Pending))
  val slugFilter = SlugSearchFilter(slug = "my-awesome-slug")

  val filters =
    SearchFilters(
      theme = Some(themeFilter),
      tags = Some(tagsFilter),
      labels = Some(labelsFilter),
      trending = Some(trendingFilter),
      content = Some(contentFilter),
      status = Some(statusFilter),
      context = None,
      slug = Some(slugFilter)
    )

  val sorts = Seq(IndexedSort(Some("field"), Some(SortOrder.ASC)))
  val limit = 10
  val skip = 0

  val searchQuery = SearchQuery(Some(filters), sorts, Some(limit), Some(skip))

  feature("transform searchFilter into QueryDefinition") {
    scenario("get Sort from Search filter") {
      Given("a searchFilter")
      When("call getSort with SearchQuery")
      val listFieldSortDefinition = SearchFilters.getSort(searchQuery)
      listFieldSortDefinition shouldBe List(FieldSortDefinition("field", None, None, None, None, None, SortOrder.ASC))
    }

    scenario("get skip from Search filter") {
      Given("a searchFilter")
      When("call getSkipSearch with SearchQuery")
      val skipResult = SearchFilters.getSkipSearch(searchQuery)
      skipResult shouldBe skip
    }

    scenario("get limit from Search filter") {
      Given("a searchFilter")
      When("call getLimitSearch with SearchQuery")
      val limitResult = SearchFilters.getLimitSearch(searchQuery)
      limitResult shouldBe limit
    }

    scenario("build ThemeSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildThemeSearchFilter with SearchQuery")
      val themeSearchFilterResult = SearchFilters.buildThemeSearchFilter(searchQuery)
      themeSearchFilterResult shouldBe Some(ElasticApi.matchQuery(ProposalElasticsearchFieldNames.themeId, themeValue))
    }

    scenario("build TagsSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildTagsSearchFilter with SearchQuery")
      val tagsSearchFilterResult = SearchFilters.buildTagsSearchFilter(searchQuery)
      tagsSearchFilterResult shouldBe Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.tagId, tagValue))
    }

    scenario("build LabelsSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildLabelsSearchFilter with SearchQuery")
      val labelsSearchFilterResult = SearchFilters.buildLabelsSearchFilter(searchQuery)
      labelsSearchFilterResult shouldBe Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.labels, labelValue))
    }

    scenario("build TrendingSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildTrendingSearchFilter with SearchQuery")
      val trendingSearchFilterResult = SearchFilters.buildTrendingSearchFilter(searchQuery)
      trendingSearchFilterResult shouldBe Some(
        ElasticApi.termQuery(ProposalElasticsearchFieldNames.trending, trendingValue)
      )
    }

    scenario("build ContentSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildContentSearchFilter with SearchQuery")
      val contentSearchFilterResult = SearchFilters.buildContentSearchFilter(searchQuery)
      contentSearchFilterResult shouldBe Some(
        ElasticApi
          .multiMatchQuery(textValue)
          .fields(
            Map(
              ProposalElasticsearchFieldNames.content -> 2.toFloat,
              ProposalElasticsearchFieldNames.contentStemmed -> 1.toFloat
            )
          )
      )
    }

    scenario("build StatusSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildStatusSearchFilter with SearchQuery")
      val statusSearchFilterResult = SearchFilters.buildStatusSearchFilter(searchQuery)
      statusSearchFilterResult shouldBe Some(
        ElasticApi.matchQuery(ProposalElasticsearchFieldNames.status, ProposalStatus.Pending.shortName)
      )
    }
    scenario("build SlugSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildSlugSearchFilter with SearchQuery")
      val slugSearchFilterResult = SearchFilters.buildSlugSearchFilter(searchQuery)
      slugSearchFilterResult shouldBe Some(
        ElasticApi.termQuery(ProposalElasticsearchFieldNames.slug, "my-awesome-slug")
      )
    }
  }
}
