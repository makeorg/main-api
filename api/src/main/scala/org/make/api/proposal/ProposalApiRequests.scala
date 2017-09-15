package org.make.api.proposal

import io.circe.{Decoder, Encoder, Json}
import org.elasticsearch.search.sort.SortOrder
import org.make.api.technical.businessconfig.BusinessConfig
import org.make.core.Validation
import org.make.core.Validation.{maxLength, validate}
import org.make.core.proposal._
import org.make.core.reference.{LabelId, TagId, ThemeId}

final case class ProposeProposalRequest(content: String) {
  private val maxProposalLength = BusinessConfig.defaultProposalMaxLength
  validate(maxLength("content", maxProposalLength, content))
}

final case class ProposeProposalResponse(proposalId: ProposalId)

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

final case class SortOptionRequest(field: String, mode: Option[Order]) {
  def toSortOption: SortOption = {
    val maybeOrder = mode match {
      case Some(OrderAsc)  => Some(SortOrder.ASC)
      case Some(OrderDesc) => Some(SortOrder.DESC)
      case None            => None
    }
    SortOption(field, maybeOrder)
  }
}

final case class SearchOptionsRequest(sort: Seq[SortOptionRequest], limit: Option[Int], skip: Option[Int]) {
  def toSearchOptions: Option[SearchOptions] =
    SearchOptions.parseSearchOptions(sort.map(_.toSortOption), limit.map(LimitOption), skip.map(SkipOption))
}

final case class SearchRequest(themesIds: Option[Seq[String]] = None,
                               tagsIds: Option[Seq[String]] = None,
                               content: Option[String] = None,
                               options: Option[SearchOptionsRequest] = None) {
  def toSearchQuery: SearchQuery = {
    val filter: Option[SearchFilter] = SearchFilter.parseSearchFilter(
      theme = themesIds.map(ThemeSearchFilter),
      tag = tagsIds.map(TagSearchFilter),
      content = content.map(text => ContentSearchFilter(text))
    )
    val searchOptions: Option[SearchOptions] = options.flatMap(_.toSearchOptions)
    SearchQuery(filter = filter, options = searchOptions)
  }
}

final case class ExhaustiveSearchRequest(themesIds: Option[Seq[String]] = None,
                                         tagsIds: Option[Seq[String]] = None,
                                         content: Option[String] = None,
                                         status: Option[ProposalStatus] = None,
                                         options: Option[SearchOptionsRequest] = None) {
  def toSearchQuery: SearchQuery = {
    val filter: Option[SearchFilter] = SearchFilter.parseSearchFilter(
      theme = themesIds.map(ThemeSearchFilter),
      tag = tagsIds.map(TagSearchFilter),
      content = content.map(text => ContentSearchFilter(text)),
      status = status.map(StatusSearchFilter)
    )
    val searchOptions: Option[SearchOptions] = options.flatMap(_.toSearchOptions)
    SearchQuery(filter = filter, options = searchOptions)
  }
}
