package org.make.core.proposal

import com.sksamuel.elastic4s.ElasticApi
import com.sksamuel.elastic4s.http.ElasticDsl
import com.sksamuel.elastic4s.searches.sort.FieldSortDefinition
import org.elasticsearch.search.sort.SortOrder
import org.make.core.common.indexed.{Sort => IndexedSort}
import org.make.core.idea.{CountrySearchFilter, LanguageSearchFilter}
import org.make.core.operation.OperationId
import org.make.core.proposal.indexed.ProposalElasticsearchFieldNames
import org.make.core.reference.{LabelId, TagId, ThemeId}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

class SearchQueryTest extends FeatureSpec with GivenWhenThen with MockitoSugar with Matchers with ElasticDsl {
  val themeValue = "Theme"
  val themeFilter = ThemeSearchFilter(Seq(ThemeId(themeValue)))
  val tagValue = "Tag1"
  val tagsFilter = TagsSearchFilter(Seq(TagId(tagValue)))
  val labelValue = "Label"
  val labelsFilter = LabelsSearchFilter(Seq(LabelId(labelValue)))
  val operationValue = "Operation"
  val operationFilter = OperationSearchFilter(OperationId(operationValue))
  val trendingValue = "Trending"
  val trendingFilter = TrendingSearchFilter(trendingValue)
  val textValue = "text to search"
  val contentFilter = ContentSearchFilter(text = textValue, fuzzy = None)
  val statusFilter = StatusSearchFilter(status = Seq(ProposalStatus.Pending))
  val slugFilter = SlugSearchFilter(slug = "my-awesome-slug")
  val languageFilter = LanguageSearchFilter(language = "en")
  val countryFilter = CountrySearchFilter(country = "GB")

  val filters =
    SearchFilters(
      theme = Some(themeFilter),
      tags = Some(tagsFilter),
      labels = Some(labelsFilter),
      operation = Some(operationFilter),
      trending = Some(trendingFilter),
      content = Some(contentFilter),
      status = Some(statusFilter),
      context = None,
      slug = Some(slugFilter),
      language = Some(languageFilter),
      country = Some(countryFilter)
    )

  val sort = Some(IndexedSort(Some("field"), Some(SortOrder.ASC)))
  val limit = 10
  val skip = 0

  val searchQuery = SearchQuery(Some(filters), sort, Some(limit), Some(skip), Some("en"))

  feature("transform searchFilter into QueryDefinition") {
    scenario("get Sort from Search filter") {
      Given("a searchFilter")
      When("call getSort with SearchQuery")
      val fieldSortDefinition = SearchFilters.getSort(searchQuery)
      Then("result is a list of FieldSortDefinition")
      fieldSortDefinition shouldBe Some(FieldSortDefinition("field", None, None, None, None, None, SortOrder.ASC))
    }

    scenario("get skip from Search filter") {
      Given("a searchFilter")
      When("call getSkipSearch with SearchQuery")
      val skipResult = SearchFilters.getSkipSearch(searchQuery)
      Then("result is 0")
      skipResult shouldBe skip
    }

    scenario("get limit from Search filter") {
      Given("a searchFilter")
      When("call getLimitSearch with SearchQuery")
      val limitResult = SearchFilters.getLimitSearch(searchQuery)
      Then("result is 10")
      limitResult shouldBe limit
    }

    scenario("build ThemeSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildThemeSearchFilter with SearchQuery")
      val themeSearchFilterResult = SearchFilters.buildThemeSearchFilter(searchQuery)
      Then("result is a termQuery")
      themeSearchFilterResult shouldBe Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.themeId, themeValue))
    }

    scenario("build TagsSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildTagsSearchFilter with SearchQuery")
      val tagsSearchFilterResult = SearchFilters.buildTagsSearchFilter(searchQuery)
      Then("result is a termQuery")
      tagsSearchFilterResult shouldBe Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.tagId, tagValue))
    }

    scenario("build LabelsSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildLabelsSearchFilter with SearchQuery")
      val labelsSearchFilterResult = SearchFilters.buildLabelsSearchFilter(searchQuery)
      Then("result is a termsQuery")
      labelsSearchFilterResult shouldBe Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.labels, labelValue))
    }

    scenario("build OperationSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildOperationSearchFilter with SearchQuery")
      val operationSearchFilterResult = SearchFilters.buildOperationSearchFilter(searchQuery)
      Then("result is a matchQuery")
      operationSearchFilterResult shouldBe Some(
        ElasticApi.termQuery(ProposalElasticsearchFieldNames.operationId, operationValue)
      )
    }

    scenario("build TrendingSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildTrendingSearchFilter with SearchQuery")
      val trendingSearchFilterResult = SearchFilters.buildTrendingSearchFilter(searchQuery)
      Then("result is a termQuery")
      trendingSearchFilterResult shouldBe Some(
        ElasticApi.termQuery(ProposalElasticsearchFieldNames.trending, trendingValue)
      )
    }

    scenario("build ContentSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildContentSearchFilter with SearchQuery")
      val contentSearchFilterResult = SearchFilters.buildContentSearchFilter(searchQuery)
      Then("result is a multiMatchQuery")
      val fieldsBoosts =
        Map(
          ProposalElasticsearchFieldNames.contentGeneral -> 1F,
          ProposalElasticsearchFieldNames.contentEn -> 2F,
          ProposalElasticsearchFieldNames.content -> 3F,
          ProposalElasticsearchFieldNames.contentEnStemmed -> 1.5F
        )
      contentSearchFilterResult shouldBe Some(ElasticApi.multiMatchQuery(textValue).fields(fieldsBoosts))
    }

    scenario("build ContentSearchFilter from Search filter without any language") {
      Given("a searchFilter")
      When("call buildContentSearchFilter with SearchQuery")
      val contentSearchFilterResult = SearchFilters.buildContentSearchFilter(searchQuery.copy(language = None))
      Then("result is a multiMatchQuery")
      val fieldsBoosts =
        Map(ProposalElasticsearchFieldNames.contentGeneral -> 1F, ProposalElasticsearchFieldNames.content -> 3F)
      contentSearchFilterResult shouldBe Some(ElasticApi.multiMatchQuery(textValue).fields(fieldsBoosts))
    }

    scenario("build StatusSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildStatusSearchFilter with SearchQuery")
      val statusSearchFilterResult = SearchFilters.buildStatusSearchFilter(searchQuery)
      Then("result is a termQuery")
      statusSearchFilterResult shouldBe Some(
        ElasticApi.termQuery(ProposalElasticsearchFieldNames.status, ProposalStatus.Pending.shortName)
      )
    }

    scenario("build SlugSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildSlugSearchFilter with SearchQuery")
      val slugSearchFilterResult = SearchFilters.buildSlugSearchFilter(searchQuery)
      Then("result is a termQuery")
      slugSearchFilterResult shouldBe Some(
        ElasticApi.termQuery(ProposalElasticsearchFieldNames.slug, "my-awesome-slug")
      )
    }
  }
}
