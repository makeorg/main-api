package org.make.api.proposal

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.api.technical.businessconfig.{BusinessConfig, FrontConfiguration}
import org.make.core.{RequestContext, Validation}
import org.make.core.Validation._
import org.make.core.common.indexed.SortRequest
import org.make.core.idea.{CountrySearchFilter, IdeaId, LanguageSearchFilter}
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.reference.{LabelId, TagId, ThemeId}
import org.make.core.session.SessionId
import org.make.core.user.UserId

import scala.annotation.meta.field
import scala.util.Random

final case class ProposeProposalRequest(content: String,
                                        operationId: Option[OperationId],
                                        language: Option[String],
                                        country: Option[String]) {
  private val maxProposalLength = BusinessConfig.defaultProposalMaxLength
  private val minProposalLength = FrontConfiguration.defaultProposalMinLength
  validate(maxLength("content", maxProposalLength, content))
  validate(minLength("content", minProposalLength, content))
  validate(mandatoryField("language", language))
  validate(mandatoryField("country", country))
}

object ProposeProposalRequest {
  implicit val decoder: Decoder[ProposeProposalRequest] = deriveDecoder[ProposeProposalRequest]
}

final case class UpdateProposalRequest(newContent: Option[String],
                                       theme: Option[ThemeId],
                                       labels: Seq[LabelId],
                                       tags: Seq[TagId],
                                       similarProposals: Seq[ProposalId],
                                       idea: Option[IdeaId],
                                       operation: Option[OperationId]) {
  validate(Validation.requireNonEmpty("tags", tags), requirePresent("idea", idea))
}

object UpdateProposalRequest {
  implicit val decoder: Decoder[UpdateProposalRequest] = deriveDecoder[UpdateProposalRequest]
}

final case class ValidateProposalRequest(newContent: Option[String],
                                         sendNotificationEmail: Boolean,
                                         theme: Option[ThemeId],
                                         labels: Seq[LabelId],
                                         tags: Seq[TagId],
                                         similarProposals: Seq[ProposalId],
                                         idea: Option[IdeaId],
                                         operation: Option[OperationId]) {
  validate(Validation.requireNonEmpty("tags", tags), requirePresent("idea", idea))
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

final case class ContextFilterRequest(operation: Option[OperationId] = None,
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

final case class SearchRequest(proposalIds: Option[Seq[ProposalId]] = None,
                               themesIds: Option[Seq[ThemeId]] = None,
                               tagsIds: Option[Seq[TagId]] = None,
                               labelsIds: Option[Seq[LabelId]] = None,
                               operationId: Option[OperationId] = None,
                               trending: Option[String] = None,
                               content: Option[String] = None,
                               slug: Option[String] = None,
                               seed: Option[Int] = None,
                               context: Option[ContextFilterRequest] = None,
                               language: Option[String] = None,
                               country: Option[String] = None,
                               sort: Option[SortRequest] = None,
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
  def toSearchQuery(requestContext: RequestContext): SearchQuery = {
    val fuzziness = "AUTO"
    val filters: Option[SearchFilters] =
      SearchFilters.parse(
        proposals = proposalIds.map(ProposalSearchFilter.apply),
        themes = themesIds.map(ThemeSearchFilter.apply),
        tags = tagsIds.map(TagsSearchFilter.apply),
        labels = labelsIds.map(LabelsSearchFilter.apply),
        operation = operationId.map(OperationSearchFilter.apply),
        trending = trending.map(value => TrendingSearchFilter(value)),
        content = content.map(text => {
          ContentSearchFilter(text, Some(fuzziness))
        }),
        slug = slug.map(value => SlugSearchFilter(value)),
        context = context.map(_.toContext),
        language = language.map(LanguageSearchFilter.apply),
        country = country.map(CountrySearchFilter.apply)
      )

    SearchQuery(
      filters = filters,
      sort = sort.map(_.toSort),
      limit = limit,
      skip = skip,
      language = requestContext.language
    )
  }
}

object SearchRequest {
  implicit val decoder: Decoder[SearchRequest] = deriveDecoder[SearchRequest]
}

final case class ExhaustiveSearchRequest(proposalIds: Option[Seq[ProposalId]] = None,
                                         themesIds: Option[Seq[ThemeId]] = None,
                                         tagsIds: Option[Seq[TagId]] = None,
                                         labelsIds: Option[Seq[LabelId]] = None,
                                         operationId: Option[OperationId] = None,
                                         ideaId: Option[IdeaId] = None,
                                         trending: Option[String] = None,
                                         content: Option[String] = None,
                                         context: Option[ContextFilterRequest] = None,
                                         status: Option[Seq[ProposalStatus]] = None,
                                         language: Option[String] = None,
                                         country: Option[String] = None,
                                         sort: Option[SortRequest] = None,
                                         limit: Option[Int] = None,
                                         skip: Option[Int] = None) {
  def toSearchQuery(requestContext: RequestContext): SearchQuery = {
    val fuzziness = "AUTO"
    val filters: Option[SearchFilters] =
      SearchFilters.parse(
        proposals = proposalIds.map(ProposalSearchFilter.apply),
        themes = themesIds.map(ThemeSearchFilter.apply),
        tags = tagsIds.map(TagsSearchFilter.apply),
        labels = labelsIds.map(LabelsSearchFilter.apply),
        operation = operationId.map(OperationSearchFilter.apply),
        idea = ideaId.map(IdeaSearchFilter.apply),
        trending = trending.map(value => TrendingSearchFilter(value)),
        content = content.map(text    => ContentSearchFilter(text, Some(fuzziness))),
        context = context.map(_.toContext),
        status = status.map(StatusSearchFilter.apply),
        language = language.map(LanguageSearchFilter.apply),
        country = country.map(CountrySearchFilter.apply)
      )

    SearchQuery(
      filters = filters,
      sort = sort.map(_.toSort),
      limit = limit,
      skip = skip,
      language = requestContext.language
    )
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

final case class PatchProposalRequest(slug: Option[String] = None,
                                      content: Option[String] = None,
                                      ideaId: Option[IdeaId] = None,
                                      author: Option[UserId] = None,
                                      labels: Option[Seq[LabelId]] = None,
                                      theme: Option[ThemeId] = None,
                                      status: Option[ProposalStatus] = None,
                                      refusalReason: Option[String] = None,
                                      tags: Option[Seq[TagId]] = None,
                                      creationContext: Option[PatchRequestContext] = None,
                                      operation: Option[OperationId] = None,
                                      language: Option[String] = None,
                                      country: Option[String] = None)

object PatchProposalRequest {
  implicit val decoder: Decoder[PatchProposalRequest] = deriveDecoder[PatchProposalRequest]
}

final case class PatchRequestContext(currentTheme: Option[ThemeId] = None,
                                     requestId: Option[String] = None,
                                     sessionId: Option[SessionId] = None,
                                     externalId: Option[String] = None,
                                     country: Option[String] = None,
                                     language: Option[String] = None,
                                     operation: Option[OperationId] = None,
                                     source: Option[String] = None,
                                     location: Option[String] = None,
                                     question: Option[String] = None,
                                     hostname: Option[String] = None,
                                     ipAddress: Option[String] = None,
                                     getParameters: Option[Map[String, String]] = None,
                                     userAgent: Option[String] = None)

object PatchRequestContext {
  implicit val decoder: Decoder[PatchRequestContext] = deriveDecoder[PatchRequestContext]
}

@ApiModel
final case class PatchProposalsIdeaRequest(
  @(ApiModelProperty @field)(dataType = "list[string]") proposalIds: Seq[ProposalId],
  @(ApiModelProperty @field)(dataType = "string") ideaId: IdeaId
)
object PatchProposalsIdeaRequest {
  implicit val decoder: Decoder[PatchProposalsIdeaRequest] = deriveDecoder[PatchProposalsIdeaRequest]
}

final case class NextProposalToModerateRequest(operationId: Option[OperationId],
                                               themeId: Option[ThemeId],
                                               country: String,
                                               language: String) {
  validate(
    requirePresent("operationId", operationId.orElse(themeId), Some("Next proposal needs a theme or an operation")),
    mandatoryField("country", country, Some("country is mandatory")),
    mandatoryField("language", language, Some("language is mandatory")),
  )
}

object NextProposalToModerateRequest {
  implicit val decoder: Decoder[NextProposalToModerateRequest] = deriveDecoder[NextProposalToModerateRequest]

}
