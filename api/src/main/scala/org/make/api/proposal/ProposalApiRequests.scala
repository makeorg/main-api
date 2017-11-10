package org.make.api.proposal

import org.make.api.technical.businessconfig.BusinessConfig
import org.make.core.Validation
import org.make.core.Validation.{maxLength, minLength, validate}
import org.make.core.common.indexed.SortRequest
import org.make.core.proposal._
import org.make.core.reference.{LabelId, TagId, ThemeId}

final case class ProposeProposalRequest(content: String) {
  private val maxProposalLength = BusinessConfig.defaultProposalMaxLength
  private val minProposalLength = BusinessConfig.defaultProposalMinLength
  validate(maxLength("content", maxProposalLength, content))
  validate(minLength("content", minProposalLength, content))
}

final case class UpdateProposalRequest(newContent: Option[String],
                                       theme: Option[ThemeId],
                                       labels: Seq[LabelId],
                                       tags: Seq[TagId],
                                       similarProposals: Seq[ProposalId]) {
  validate(Validation.requireNonEmpty("tags", tags))
}

final case class ValidateProposalRequest(newContent: Option[String],
                                         sendNotificationEmail: Boolean,
                                         theme: Option[ThemeId],
                                         labels: Seq[LabelId],
                                         tags: Seq[TagId],
                                         similarProposals: Seq[ProposalId]) {
  validate(Validation.requireNonEmpty("tags", tags))
}

final case class RefuseProposalRequest(sendNotificationEmail: Boolean, refusalReason: Option[String]) {
  validate(Validation.mandatoryField("refusalReason", refusalReason))
}

final case class ContextFilterRequest(operation: Option[String] = None,
                                      source: Option[String] = None,
                                      location: Option[String] = None,
                                      question: Option[String] = None) {
  def toContext: ContextSearchFilter = {
    ContextSearchFilter(operation, source, location, question)
  }
}

final case class SearchRequest(themesIds: Option[Seq[String]] = None,
                               tagsIds: Option[Seq[String]] = None,
                               labelsIds: Option[Seq[String]] = None,
                               trending: Option[String] = None,
                               content: Option[String] = None,
                               slug: Option[String] = None,
                               context: Option[ContextFilterRequest] = None,
                               sorts: Option[Seq[SortRequest]] = None,
                               limit: Option[Int] = None,
                               skip: Option[Int] = None) {
  def toSearchQuery: SearchQuery = {
    val fuzziness = "AUTO"
    val filters: Option[SearchFilters] =
      SearchFilters.parse(
        theme = themesIds.map(ThemeSearchFilter.apply),
        tags = tagsIds.map(TagsSearchFilter.apply),
        labels = labelsIds.map(LabelsSearchFilter.apply),
        trending = trending.map(value => TrendingSearchFilter(value)),
        content = content.map(text => {
          ContentSearchFilter(text, Some(fuzziness))
        }),
        slug = slug.map(value => SlugSearchFilter(value)),
        context = context.map(_.toContext)
      )

    SearchQuery(filters = filters, sorts = sorts.getOrElse(Seq.empty).map(_.toSort), limit = limit, skip = skip)
  }
}

final case class ExhaustiveSearchRequest(themesIds: Option[Seq[String]] = None,
                                         tagsIds: Option[Seq[String]] = None,
                                         labelsIds: Option[Seq[String]] = None,
                                         trending: Option[String] = None,
                                         content: Option[String] = None,
                                         context: Option[ContextFilterRequest] = None,
                                         status: Option[ProposalStatus] = None,
                                         sorts: Option[Seq[SortRequest]] = None,
                                         limit: Option[Int] = None,
                                         skip: Option[Int] = None) {
  def toSearchQuery: SearchQuery = {
    val fuzziness = "AUTO"
    val filters: Option[SearchFilters] =
      SearchFilters.parse(
        theme = themesIds.map(ThemeSearchFilter.apply),
        tags = tagsIds.map(TagsSearchFilter.apply),
        labels = labelsIds.map(LabelsSearchFilter.apply),
        trending = trending.map(value => TrendingSearchFilter(value)),
        content = content.map(text    => ContentSearchFilter(text, Some(fuzziness))),
        context = context.map(_.toContext),
        status = status.map(StatusSearchFilter.apply)
      )

    SearchQuery(filters = filters, sorts = sorts.getOrElse(Seq.empty).map(_.toSort), limit = limit, skip = skip)
  }
}

final case class VoteProposalRequest(voteKey: VoteKey)

final case class QualificationProposalRequest(qualificationKey: QualificationKey, voteKey: VoteKey)
