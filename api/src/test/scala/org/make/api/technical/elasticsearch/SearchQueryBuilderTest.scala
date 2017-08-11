package org.make.api.technical.elasticsearch

import org.make.api.MakeUnitTest

class SearchQueryBuilderTest extends MakeUnitTest with SearchQueryBuilderComponent {
  val searchQueryBuilder = new SearchQueryBuilder()

  feature("elasticsearch search query") {
    scenario("decoding json to search query") {
      Given("a valid json")
      val jsonStringValid = """
                         | {
                         |  "filter": {
                         |    "theme": {"id": ["in"]},
                         |    "tag": {"id": ["quod"]},
                         |    "content": {"text": "programmes"}
                         |  },
                         |
                         |  "options": {
                         |    "sort": {"field": "createdAt", "mode": "asc"},
                         |    "skip": {"value": 0},
                         |    "limit": {"value": 10}
                         |   }
                         | }
                       """.stripMargin

      val searchQuery = searchQueryBuilder.buildSearchQueryFromJson(jsonString = jsonStringValid)

      searchQuery shouldBe a[SearchQuery]
      searchQuery should be(
        SearchQuery(
          filter = SearchFilter(
            theme = Some(ThemeSearchFilter(id = Vector("in"))),
            tag = Some(TagSearchFilter(id = Vector("quod"))),
            content = Some(ContentSearchFilter(text = "programmes", fuzzy = None))
          ),
          options = Some(
            SearchOptions(
              sort = Some(SortOption(field = "createdAt", mode = Some("asc"))),
              skip = Some(SkipOption(value = 0)),
              limit = Some(LimitOption(value = 10))
            )
          )
        )
      )

      Given("an invalid sort mode")
      val jsonStringInvalidSort = """
                                  | {
                                  |  "filter": {},
                                  |  "options": {
                                  |    "sort": {"field": "createdAt", "mode": "Invalid"},
                                  |   }
                                  | }
                                """.stripMargin
      an[IllegalArgumentException] should be thrownBy {
        searchQueryBuilder.buildSearchQueryFromJson(jsonStringInvalidSort)
      }

      Given("an non integer skip value")
      val jsonStringInvalidSkip = """
                                     | {
                                     |  "filter": {},
                                     |  "options": {
                                     |    "skip": {"value": "Invalid"}
                                     |   }
                                     | }
                                   """.stripMargin
      an[IllegalArgumentException] should be thrownBy {
        searchQueryBuilder.buildSearchQueryFromJson(jsonStringInvalidSkip)
      }

      Given("an non integer limit value")
      val jsonStringInvalidLimit = """
                                     | {
                                     |  "filter": {},
                                     |  "options": {
                                     |    "limit": {"value": "Invalid"}
                                     |   }
                                     | }
                                   """.stripMargin
      an[IllegalArgumentException] should be thrownBy {
        searchQueryBuilder.buildSearchQueryFromJson(jsonStringInvalidLimit)
      }
    }
  }
}
