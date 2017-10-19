package org.make.api.sequence

import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.core.common.indexed.SortRequest
import org.make.core.proposal.ProposalId
import org.make.core.reference.{TagId, ThemeId}
import org.make.core.sequence._

import scala.annotation.meta.field

// ToDo: handle translations
@ApiModel
final case class CreateSequenceRequest(@(ApiModelProperty @field)(example = "ma séquence") title: String,
                                       @(ApiModelProperty @field)(dataType = "list[string]") themeIds: Seq[ThemeId],
                                       @(ApiModelProperty @field)(dataType = "list[string]") tagIds: Seq[TagId],
                                       searchable: Boolean)

@ApiModel
final case class AddProposalSequenceRequest(
  @(ApiModelProperty @field)(dataType = "list[string]") proposalIds: Seq[ProposalId]
)

@ApiModel
final case class RemoveProposalSequenceRequest(
  @(ApiModelProperty @field)(dataType = "list[string]") proposalIds: Seq[ProposalId]
)

final case class UpdateSequenceRequest(title: Option[String], status: Option[String])

@ApiModel
final case class ExhaustiveSearchRequest(
  @(ApiModelProperty @field)(dataType = "list[string]") tagIds: Seq[TagId] = Seq.empty,
  @(ApiModelProperty @field)(dataType = "list[string]") themeIds: Seq[ThemeId] = Seq.empty,
  title: Option[String] = None,
  slug: Option[String] = None,
  context: Option[ContextFilterRequest] = None,
  status: Option[SequenceStatus] = None,
  searchable: Option[Boolean] = None,
  sorts: Seq[SortRequest] = Seq.empty,
  limit: Option[Int] = None,
  skip: Option[Int] = None
) {
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
