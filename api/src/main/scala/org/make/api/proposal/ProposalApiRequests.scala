package org.make.api.proposal

import io.circe.{Decoder, ObjectEncoder}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import org.make.api.technical.businessconfig.BusinessConfig
import org.make.core.Validation
import org.make.core.Validation.{maxLength, minLength, validate}
import org.make.core.common.indexed.SortRequest
import org.make.core.proposal._
import org.make.core.reference.{IdeaId, LabelId, TagId, ThemeId}

import scala.util.Random

final case class ProposeProposalRequest(content: String) {
  private val maxProposalLength = BusinessConfig.defaultProposalMaxLength
  private val minProposalLength = BusinessConfig.defaultProposalMinLength
  validate(maxLength("content", maxProposalLength, content))
  validate(minLength("content", minProposalLength, content))
}

object ProposeProposalRequest {
  implicit val decoder: Decoder[ProposeProposalRequest] = deriveDecoder[ProposeProposalRequest]
}

final case class UpdateProposalRequest(newContent: Option[String],
                                       theme: Option[ThemeId],
                                       labels: Seq[LabelId],
                                       tags: Seq[TagId],
                                       similarProposals: Seq[ProposalId],
                                       newIdea: Option[IdeaId]) {
  validate(Validation.requireNonEmpty("tags", tags))
}

object UpdateProposalRequest {
  implicit val decoder: Decoder[UpdateProposalRequest] = deriveDecoder[UpdateProposalRequest]
}

final case class ValidateProposalRequest(newContent: Option[String],
                                         sendNotificationEmail: Boolean,
                                         theme: Option[ThemeId],
                                         labels: Seq[LabelId],
                                         tags: Seq[TagId],
                                         similarProposals: Seq[ProposalId]) {
  validate(Validation.requireNonEmpty("tags", tags))
}

object ValidateProposalRequest {
  implicit val decoder: Decoder[ValidateProposalRequest] = deriveDecoder[ValidateProposalRequest]
  implicit val encoder: ObjectEncoder[ValidateProposalRequest] = deriveEncoder[ValidateProposalRequest]
}

final case class RefuseProposalRequest(sendNotificationEmail: Boolean, refusalReason: Option[String]) {
  validate(Validation.mandatoryField("refusalReason", refusalReason))
}

object RefuseProposalRequest {
  implicit val decoder: Decoder[RefuseProposalRequest] = deriveDecoder[RefuseProposalRequest]
  implicit val encoder: ObjectEncoder[RefuseProposalRequest] = deriveEncoder[RefuseProposalRequest]
}

final case class ContextFilterRequest(operation: Option[String] = None,
                                      source: Option[String] = None,
                                      location: Option[String] = None,
                                      question: Option[String] = None) {
  def toContext: ContextSearchFilter = {
    ContextSearchFilter(operation, source, location, question)
  }
}

object ContextFilterRequest {
  implicit val decoder: Decoder[ContextFilterRequest] = deriveDecoder[ContextFilterRequest]
}

final case class SearchRequest(proposalIds: Option[Seq[String]] = None,
                               themesIds: Option[Seq[String]] = None,
                               tagsIds: Option[Seq[String]] = None,
                               labelsIds: Option[Seq[String]] = None,
                               trending: Option[String] = None,
                               content: Option[String] = None,
                               slug: Option[String] = None,
                               seed: Option[Int] = None,
                               context: Option[ContextFilterRequest] = None,
                               sorts: Option[Seq[SortRequest]] = None,
                               limit: Option[Int] = None,
                               skip: Option[Int] = None,
                               isRandom: Option[Boolean] = Some(false)) {

  val randomScoreSeed: Option[Int] = isRandom.flatMap { randomise =>
    if (randomise) {
      Some(seed.getOrElse(Random.nextInt()))
    } else {
      None
    }
  }
  def toSearchQuery: SearchQuery = {
    val fuzziness = "AUTO"
    val filters: Option[SearchFilters] =
      SearchFilters.parse(
        proposals = proposalIds.map(ProposalSearchFilter.apply),
        themes = themesIds.map(ThemeSearchFilter.apply),
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

object SearchRequest {
  implicit val decoder: Decoder[SearchRequest] = deriveDecoder[SearchRequest]
}

final case class ExhaustiveSearchRequest(proposalIds: Option[Seq[String]] = None,
                                         themesIds: Option[Seq[String]] = None,
                                         tagsIds: Option[Seq[String]] = None,
                                         labelsIds: Option[Seq[String]] = None,
                                         trending: Option[String] = None,
                                         content: Option[String] = None,
                                         context: Option[ContextFilterRequest] = None,
                                         status: Option[Seq[ProposalStatus]] = None,
                                         sorts: Option[Seq[SortRequest]] = None,
                                         limit: Option[Int] = None,
                                         skip: Option[Int] = None) {
  def toSearchQuery: SearchQuery = {
    val fuzziness = "AUTO"
    val filters: Option[SearchFilters] =
      SearchFilters.parse(
        proposals = proposalIds.map(ProposalSearchFilter.apply),
        themes = themesIds.map(ThemeSearchFilter.apply),
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

object ExhaustiveSearchRequest {
  implicit val decoder: Decoder[ExhaustiveSearchRequest] = deriveDecoder[ExhaustiveSearchRequest]
}

final case class VoteProposalRequest(voteKey: VoteKey)

object VoteProposalRequest {
  implicit val decoder: Decoder[VoteProposalRequest] = deriveDecoder[VoteProposalRequest]
}

final case class QualificationProposalRequest(qualificationKey: QualificationKey, voteKey: VoteKey)

object QualificationProposalRequest {
  implicit val decoder: Decoder[QualificationProposalRequest] = deriveDecoder[QualificationProposalRequest]
}
