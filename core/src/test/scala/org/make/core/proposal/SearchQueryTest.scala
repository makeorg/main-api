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

package org.make.core.proposal

import com.sksamuel.elastic4s.ElasticApi
import com.sksamuel.elastic4s.http.ElasticDsl
import com.sksamuel.elastic4s.searches.sort.{FieldSort, SortOrder}
import org.make.core.common.indexed.{Sort => IndexedSort}
import org.make.core.idea.{CountrySearchFilter, LanguageSearchFilter}
import org.make.core.operation.OperationId
import org.make.core.proposal.indexed.ProposalElasticsearchFieldNames
import org.make.core.reference.{Country, LabelId, Language}
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

class SearchQueryTest extends FeatureSpec with GivenWhenThen with MockitoSugar with Matchers with ElasticDsl {
  val initialProposalFilter = InitialProposalFilter(true)
  val tagValue = "Tag1"
  val tagsFilter = TagsSearchFilter(Seq(TagId(tagValue)))
  val labelValue = "Label"
  val labelsFilter = LabelsSearchFilter(Seq(LabelId(labelValue)))
  val operationValue = "Operation"
  val operationFilter = OperationSearchFilter(Seq(OperationId(operationValue)))
  val trendingValue = "Trending"
  val trendingFilter = TrendingSearchFilter(trendingValue)
  val textValue = "text to search"
  val contentFilter = ContentSearchFilter(text = textValue, fuzzy = None)
  val statusFilter = StatusSearchFilter(status = Seq(ProposalStatus.Pending))
  val slugFilter = SlugSearchFilter(slug = "my-awesome-slug")
  val languageFilter = LanguageSearchFilter(language = Language("en"))
  val countryFilter = CountrySearchFilter(country = Country("GB"))
  val userFilter = UserSearchFilter(userId = UserId("A34343-ERER"))

  val filters =
    SearchFilters(
      initialProposal = Some(initialProposalFilter),
      tags = Some(tagsFilter),
      labels = Some(labelsFilter),
      operation = Some(operationFilter),
      trending = Some(trendingFilter),
      content = Some(contentFilter),
      status = Some(statusFilter),
      context = None,
      slug = Some(slugFilter),
      language = Some(languageFilter),
      country = Some(countryFilter),
      user = Some(userFilter)
    )

  val sort = Some(IndexedSort(Some("field"), Some(SortOrder.ASC)))
  val limit = 10
  val skip = 0

  val searchQuery = SearchQuery(Some(filters), sort, Some(limit), Some(skip), Some(Language("en")))

  feature("transform searchFilter into Query") {
    scenario("get Sort from Search filter") {
      Given("a searchFilter")
      When("call getSort with SearchQuery")
      val fieldSortDefinition = SearchFilters.getSort(searchQuery)
      Then("result is a list of FieldSortDefinition")
      fieldSortDefinition shouldBe Some(FieldSort("field", None, None, None, None, None, SortOrder.ASC))
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

    scenario("build InitialProposalSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildInitialProposalSearchFilterwith SearchQuery")
      val themeSearchFilterResult = SearchFilters.buildInitialProposalSearchFilter(searchQuery)
      Then("result is a termQuery")
      themeSearchFilterResult shouldBe Some(
        ElasticApi.termQuery(field = ProposalElasticsearchFieldNames.initialProposal, value = true)
      )
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
          ProposalElasticsearchFieldNames.contentGeneral -> 1D,
          ProposalElasticsearchFieldNames.contentEn -> 2D,
          ProposalElasticsearchFieldNames.content -> 3D,
          ProposalElasticsearchFieldNames.contentEnStemmed -> 1.5D
        )
      contentSearchFilterResult shouldBe Some(ElasticApi.multiMatchQuery(textValue).fields(fieldsBoosts))
    }

    scenario("build ContentSearchFilter from Search filter without any language") {
      Given("a searchFilter")
      When("call buildContentSearchFilter with SearchQuery")
      val contentSearchFilterResult = SearchFilters.buildContentSearchFilter(searchQuery.copy(language = None))
      Then("result is a multiMatchQuery")
      val fieldsBoosts =
        Map(ProposalElasticsearchFieldNames.contentGeneral -> 1D, ProposalElasticsearchFieldNames.content -> 3D)
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

    scenario("build UserSearchFilter from Search filter") {
      Given("a searchFilter")
      When("call buildUserSearchFilter with SearchQuery")
      val userSearchFilterResult = SearchFilters.buildUserSearchFilter(searchQuery)
      Then("result is a termQuery")
      userSearchFilterResult shouldBe Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.userId, "A34343-ERER"))
    }
  }
}
