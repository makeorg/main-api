package org.make.api.technical.elasticsearch

/**
  * The class holding the entire search query
  *
  * @param filter  sequence of search filters
  * @param options sequence of search options
  */
case class SearchQuery(filter: SearchFilter, options: Option[SearchOptions])

case class SearchFilter(theme: Option[ThemeSearchFilter],
                        tag: Option[TagSearchFilter],
                        content: Option[ContentSearchFilter])

case class ThemeSearchFilter(id: Seq[String]) {
  if (id.isEmpty) {
    throw new IllegalArgumentException("list of ids cannot be empty")
  }
}

case class TagSearchFilter(id: Seq[String]) {
  if (id.isEmpty) {
    throw new IllegalArgumentException("list of ids cannot be empty")
  }
}

case class ContentSearchFilter(text: String, fuzzy: Option[Boolean])

/**
  * Search option that allows modifying the search response
  * @param sort sorting results by a field and a mode
  * @param limit limiting the number of search responses
  * @param skip skipping a number of results, used for pagination
  */
case class SearchOptions(sort: Option[SortOption], limit: Option[LimitOption], skip: Option[SkipOption])

case class SortOption(field: String, mode: Option[String]) {}

case class LimitOption(value: Int)

case class SkipOption(value: Int)
