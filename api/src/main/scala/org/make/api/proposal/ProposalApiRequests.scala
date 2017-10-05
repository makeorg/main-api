package org.make.api.proposal

import io.circe.{Decoder, Encoder, Json}
import org.elasticsearch.search.sort.SortOrder
import org.make.api.technical.businessconfig.BusinessConfig
import org.make.core.Validation
import org.make.core.Validation.{maxLength, minLength, validate}
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.reference.{LabelId, TagId, ThemeId}

final case class ProposeProposalRequest(content: String, theme: Option[ThemeId]) {
  private val maxProposalLength = BusinessConfig.defaultProposalMaxLength
  private val minProposalLength = BusinessConfig.defaultProposalMinLength
  validate(maxLength("content", maxProposalLength, content))
  validate(minLength("content", minProposalLength, content))
}

final case class UpdateProposalRequest(content: String)

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

sealed trait Order { val shortName: String }

case object OrderAsc extends Order { override val shortName: String = "ASC" }
case object OrderDesc extends Order { override val shortName: String = "DESC" }

object Order {
  implicit lazy val orderEncoder: Encoder[Order] = (order: Order) => Json.fromString(order.shortName)
  implicit lazy val orderDecoder: Decoder[Order] = Decoder.decodeString.map(
    order => matchOrder(order).getOrElse(throw new IllegalArgumentException(s"$order is not a Order"))
  )

  val orders: Map[String, Order] = Map(OrderAsc.shortName -> OrderAsc, OrderDesc.shortName -> OrderDesc)

  def matchOrder(order: String): Option[Order] = {
    val maybeOrder = orders.get(order.toUpperCase)
    maybeOrder
  }
}

final case class SortRequest(field: Option[String], direction: Option[Order]) {
  def toSort: Sort = {
    val maybeOrderDirection = direction match {
      case Some(OrderAsc)  => Some(SortOrder.ASC)
      case Some(OrderDesc) => Some(SortOrder.DESC)
      case None            => None
    }

    Sort(field, maybeOrderDirection)
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

final case class SearchRequest(themesIds: Option[Seq[String]] = None,
                               tagsIds: Option[Seq[String]] = None,
                               labelsIds: Option[Seq[String]] = None,
                               trending: Option[String] = None,
                               content: Option[String] = None,
                               context: Option[ContextFilterRequest] = None,
                               sorts: Option[Seq[SortRequest]] = None,
                               limit: Option[Int] = None,
                               skip: Option[Int] = None) {
  def toSearchQuery: SearchQuery = {
    val fuzziness = 2
    val filters: Option[SearchFilters] =
      SearchFilters.parse(
        theme = themesIds.map(ThemeSearchFilter.apply),
        tags = tagsIds.map(TagsSearchFilter.apply),
        labels = labelsIds.map(LabelsSearchFilter.apply),
        trending = trending.map(value => TrendingSearchFilter(value)),
        content = content.map(text => {
          ContentSearchFilter(text, Some(fuzziness))
        }),
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
    val filters: Option[SearchFilters] =
      SearchFilters.parse(
        theme = themesIds.map(ThemeSearchFilter.apply),
        tags = tagsIds.map(TagsSearchFilter.apply),
        labels = labelsIds.map(LabelsSearchFilter.apply),
        trending = trending.map(value => TrendingSearchFilter(value)),
        content = content.map(text    => ContentSearchFilter(text)),
        context = context.map(_.toContext),
        status = status.map(StatusSearchFilter.apply)
      )

    SearchQuery(filters = filters, sorts = sorts.getOrElse(Seq.empty).map(_.toSort), limit = limit, skip = skip)
  }
}

final case class VoteProposalRequest(voteKey: VoteKey)

final case class QualificationProposalRequest(qualificationKey: QualificationKey, voteKey: VoteKey)
