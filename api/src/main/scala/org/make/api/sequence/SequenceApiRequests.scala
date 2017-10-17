package org.make.api.sequence

import org.make.core.common.indexed.SortRequest
import org.make.core.sequence._

// ToDo: handle translations
final case class CreateSequenceRequest(title: String, themeIds: Seq[String], tagIds: Seq[String], searchable: Boolean)
final case class AddProposalSequenceRequest(proposalIds: Seq[String])
final case class RemoveProposalSequenceRequest(proposalIds: Seq[String])

final case class UpdateSequenceRequest(title: String)

final case class ExhaustiveSearchRequest(tagIds: Seq[String] = Seq.empty,
                                         themeIds: Seq[String] = Seq.empty,
                                         title: Option[String] = None,
                                         slug: Option[String] = None,
                                         context: Option[ContextFilterRequest] = None,
                                         status: Option[SequenceStatus] = None,
                                         searchable: Option[Boolean] = None,
                                         sorts: Seq[SortRequest] = Seq.empty,
                                         limit: Option[Int] = None,
                                         skip: Option[Int] = None) {
  def toSearchQuery: SearchQuery = {
    val filters: Option[SearchFilters] = {
      val tagsFilter: Option[TagsSearchFilter] = if (tagIds.isEmpty) None else Some(TagsSearchFilter(tagIds))
      val themesFilter: Option[ThemesSearchFilter] = if (themeIds.isEmpty) None else Some(ThemesSearchFilter(themeIds))
      SearchFilters.parse(
        tags = tagsFilter,
        slug = slug.map(text => SlugSearchFilter(text)),
        themes = themesFilter,
        title = title.map(text => TitleSearchFilter(text)),
        context = context.map(_.toContext),
        status = status.map(StatusSearchFilter.apply),
        searchable = searchable
      )
    }
    SearchQuery(filters = filters, sorts = sorts.map(_.toSort), limit = limit, skip = skip)
  }
}

final case class ContextFilterRequest(operation: Option[String] = None,
                                      source: Option[String] = None,
                                      location: Option[String] = None,
                                      question: Option[String] = None) {
  def toContext: ContextSearchFilter = {
    ContextSearchFilter(operation, source, location, question)
  }
}

final case class SearchStartSequenceRequest(slug: String) {
  def toSearchQuery: SearchQuery = {
    val filters: Option[SearchFilters] =
      SearchFilters.parse(
        status = Some(StatusSearchFilter.apply(SequenceStatus.Published)),
        slug = Some(SlugSearchFilter(slug))
      )

    SearchQuery(filters = filters, limit = Some(1))
  }
}
