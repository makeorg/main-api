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

package org.make.api.proposal

import eu.timepit.refined.auto._

import java.time.ZonedDateTime
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}
import io.circe.{Codec, Decoder, Encoder}
import io.circe.refined._
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.api.technical.MakeRandom
import org.make.core.Validation._
import org.make.core.common.indexed.Sort
import org.make.core.idea.IdeaId
import org.make.core.operation.{OperationId, OperationKind}
import org.make.core.proposal._
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, LabelId, Language}
import org.make.core.session.{SessionId, VisitorId}
import org.make.core.tag.TagId
import org.make.core.user.{UserId, UserType}
import org.make.core.{
  ApplicationName,
  BusinessConfig,
  CirceFormatters,
  FrontConfiguration,
  Order,
  RequestContext,
  Validation
}

import scala.annotation.meta.field

@ApiModel
final case class ProposeProposalRequest(
  content: String,
  @(ApiModelProperty @field)(dataType = "string", example = "2d791a66-3cd5-4a2e-a117-9daa68bd3a33")
  questionId: QuestionId,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Country
) {
  private val maxProposalLength = BusinessConfig.defaultProposalMaxLength
  private val minProposalLength = FrontConfiguration.defaultProposalMinLength
  validate(
    maxLength("content", maxProposalLength, content),
    minLength("content", minProposalLength, content),
    mandatoryField("language", language),
    mandatoryField("country", country),
    validateUserInput("content", content, None)
  )
}

object ProposeProposalRequest {
  implicit val decoder: Decoder[ProposeProposalRequest] = deriveDecoder[ProposeProposalRequest]
}

final case class UpdateProposalRequest(
  newContent: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "2a774774-33ca-41a3-a0fa-65931397fbfc")
  idea: Option[IdeaId],
  @(ApiModelProperty @field)(dataType = "list[string]")
  labels: Seq[LabelId],
  @(ApiModelProperty @field)(dataType = "list[string]")
  tags: Seq[TagId],
  @(ApiModelProperty @field)(dataType = "string", example = "2d791a66-3cd5-4a2e-a117-9daa68bd3a33")
  questionId: Option[QuestionId],
  @(ApiModelProperty @field)(dataType = "list[string]")
  predictedTags: Option[Seq[TagId]],
  @(ApiModelProperty @field)(dataType = "string", example = "auto")
  predictedTagsModelName: Option[String]
) {
  validateOptional(newContent.map(value => validateUserInput("newContent", value, None)))
}

object UpdateProposalRequest {
  implicit val decoder: Decoder[UpdateProposalRequest] = deriveDecoder[UpdateProposalRequest]
}

final case class ValidateProposalRequest(
  newContent: Option[String],
  sendNotificationEmail: Boolean,
  @(ApiModelProperty @field)(dataType = "list[string]")
  labels: Seq[LabelId],
  @(ApiModelProperty @field)(dataType = "list[string]")
  tags: Seq[TagId],
  @(ApiModelProperty @field)(dataType = "string", example = "2a774774-33ca-41a3-a0fa-65931397fbfc")
  idea: Option[IdeaId],
  @(ApiModelProperty @field)(dataType = "string", example = "2d791a66-3cd5-4a2e-a117-9daa68bd3a33")
  questionId: Option[QuestionId],
  @(ApiModelProperty @field)(dataType = "list[string]")
  predictedTags: Option[Seq[TagId]],
  @(ApiModelProperty @field)(dataType = "string", example = "auto")
  predictedTagsModelName: Option[String]
) {
  validateOptional(newContent.map(value => validateUserInput("newContent", value, None)))
}

object ValidateProposalRequest {
  implicit val decoder: Decoder[ValidateProposalRequest] = deriveDecoder[ValidateProposalRequest]
  implicit val encoder: Encoder[ValidateProposalRequest] = deriveEncoder[ValidateProposalRequest]
}

final case class UpdateQualificationRequest(
  @(ApiModelProperty @field)(
    dataType = "string",
    example = "likeIt",
    allowableValues =
      "likeIt,doable,platitudeAgree,noWay,impossible,platitudeDisagree,doNotUnderstand,noOpinion,doNotCare"
  )
  key: QualificationKey,
  @(ApiModelProperty @field)(dataType = "int")
  count: Option[Int] = None,
  @(ApiModelProperty @field)(dataType = "int")
  countVerified: Option[Int] = None,
  @(ApiModelProperty @field)(dataType = "int")
  countSequence: Option[Int] = None,
  @(ApiModelProperty @field)(dataType = "int")
  countSegment: Option[Int] = None
)

object UpdateQualificationRequest {
  implicit val decoder: Decoder[UpdateQualificationRequest] = deriveDecoder[UpdateQualificationRequest]
  implicit val encoder: Encoder[UpdateQualificationRequest] =
    deriveEncoder[UpdateQualificationRequest]

}

final case class UpdateVoteRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "agree", allowableValues = "agree,disagree,neutral")
  key: VoteKey,
  @(ApiModelProperty @field)(dataType = "int")
  count: Option[Int] = None,
  @(ApiModelProperty @field)(dataType = "int")
  countVerified: Option[Int] = None,
  @(ApiModelProperty @field)(dataType = "int")
  countSequence: Option[Int] = None,
  @(ApiModelProperty @field)(dataType = "int")
  countSegment: Option[Int] = None,
  qualifications: Seq[UpdateQualificationRequest]
)

object UpdateVoteRequest {
  implicit val decoder: Decoder[UpdateVoteRequest] = deriveDecoder[UpdateVoteRequest]
  implicit val encoder: Encoder[UpdateVoteRequest] =
    deriveEncoder[UpdateVoteRequest]

}

final case class UpdateProposalVotesRequest(votes: Seq[UpdateVoteRequest])

object UpdateProposalVotesRequest {
  implicit val decoder: Decoder[UpdateProposalVotesRequest] = deriveDecoder[UpdateProposalVotesRequest]
  implicit val encoder: Encoder[UpdateProposalVotesRequest] =
    deriveEncoder[UpdateProposalVotesRequest]
}

final case class RefuseProposalRequest(
  sendNotificationEmail: Boolean,
  @(ApiModelProperty @field)(dataType = "string", example = "other")
  refusalReason: Option[String]
) {
  validate(Validation.mandatoryField("refusalReason", refusalReason))
  validateOptional(refusalReason.map(value => validateUserInput("refusalReason", value, None)))
}

object RefuseProposalRequest {
  implicit val decoder: Decoder[RefuseProposalRequest] = deriveDecoder[RefuseProposalRequest]
  implicit val encoder: Encoder[RefuseProposalRequest] = deriveEncoder[RefuseProposalRequest]
}

final case class ContextFilterRequest(
  operation: Option[OperationId] = None,
  source: Option[String] = None,
  location: Option[String] = None,
  question: Option[String] = None
) {
  def toContext: ContextSearchFilter = {
    ContextSearchFilter(operation, source, location, question)
  }
}

object ContextFilterRequest {
  def parse(
    operationId: Option[OperationId],
    source: Option[String],
    location: Option[String],
    question: Option[String]
  ): Option[ContextFilterRequest] = {
    (operationId, source, location, question) match {
      case (None, None, None, None) => None
      case _                        => Some(ContextFilterRequest(operationId, source, location, question))
    }
  }
  implicit val decoder: Decoder[ContextFilterRequest] = deriveDecoder[ContextFilterRequest]
}

final case class SearchRequest(
  proposalIds: Option[Seq[ProposalId]] = None,
  initialProposal: Option[Boolean] = None,
  tagsIds: Option[Seq[TagId]] = None,
  labelsIds: Option[Seq[LabelId]] = None,
  operationId: Option[OperationId] = None,
  questionIds: Option[Seq[QuestionId]] = None,
  content: Option[String] = None,
  slug: Option[String] = None,
  seed: Option[Int] = None,
  context: Option[ContextFilterRequest] = None,
  language: Option[Language] = None,
  country: Option[Country] = None,
  sort: Option[String] = None,
  order: Option[Order] = None,
  limit: Option[Int] = None,
  skip: Option[Int] = None,
  sortAlgorithm: Option[String] = None,
  operationKinds: Option[Seq[OperationKind]] = None,
  userTypes: Option[Seq[UserType]] = None,
  ideaIds: Option[Seq[IdeaId]] = None,
  keywords: Option[Seq[ProposalKeywordKey]] = None,
  excludedProposalIds: Option[Seq[ProposalId]] = None
) {

  def toSearchQuery(requestContext: RequestContext): SearchQuery = {
    val filters: Option[SearchFilters] =
      SearchFilters.parse(
        proposals = proposalIds.map(ProposalSearchFilter.apply),
        initialProposal = initialProposal.map(InitialProposalFilter.apply),
        tags = tagsIds.map(TagsSearchFilter.apply),
        labels = labelsIds.map(LabelsSearchFilter.apply),
        operation = operationId.map(opId => OperationSearchFilter(Seq(opId))),
        question = questionIds.map(QuestionSearchFilter.apply),
        content = content.map(ContentSearchFilter.apply),
        slug = slug.map(value => SlugSearchFilter(value)),
        context = context.map(_.toContext),
        language = language.map(LanguageSearchFilter.apply),
        country = country.map(CountrySearchFilter.apply),
        operationKinds = operationKinds.map(OperationKindsSearchFilter.apply),
        userTypes = userTypes.map(UserTypesSearchFilter.apply),
        idea = ideaIds.map(IdeaSearchFilter.apply),
        keywords = keywords.map(KeywordsSearchFilter)
      )
    val excludesFilter: Option[SearchFilters] =
      SearchFilters.parse(proposals = excludedProposalIds.map(ProposalSearchFilter.apply))

    val randomSeed: Int = seed.getOrElse(MakeRandom.nextInt())
    val searchSortAlgorithm: Option[SortAlgorithm] = AlgorithmSelector.select(sortAlgorithm, randomSeed)
    SearchQuery(
      filters = filters,
      excludes = excludesFilter,
      sort = Sort.parse(sort, order),
      limit = limit,
      skip = skip,
      language = requestContext.language,
      sortAlgorithm = searchSortAlgorithm
    )
  }
}

object SearchRequest {
  implicit val decoder: Decoder[SearchRequest] = deriveDecoder[SearchRequest]
}

final case class ExhaustiveSearchRequest(
  proposalIds: Option[Seq[ProposalId]] = None,
  initialProposal: Option[Boolean] = None,
  tagsIds: Option[Seq[TagId]] = None,
  labelsIds: Option[Seq[LabelId]] = None,
  operationId: Option[OperationId] = None,
  questionIds: Option[Seq[QuestionId]] = None,
  ideaIds: Option[Seq[IdeaId]] = None,
  content: Option[String] = None,
  context: Option[ContextFilterRequest] = None,
  status: Option[Seq[ProposalStatus]] = None,
  minVotesCount: Option[Int] = None,
  toEnrich: Option[Boolean] = None,
  minScore: Option[Double] = None,
  language: Option[Language] = None,
  country: Option[Country] = None,
  sort: Option[String] = None,
  order: Option[Order] = None,
  limit: Option[Int] = None,
  skip: Option[Int] = None,
  createdBefore: Option[ZonedDateTime] = None,
  userTypes: Option[Seq[UserType]] = None,
  keywords: Option[Seq[ProposalKeywordKey]] = None,
  userId: Option[UserId] = None
) {
  def toSearchQuery(requestContext: RequestContext): SearchQuery = {
    val filters: Option[SearchFilters] =
      SearchFilters.parse(
        proposals = proposalIds.map(ProposalSearchFilter.apply),
        initialProposal = initialProposal.map(InitialProposalFilter.apply),
        tags = tagsIds.map(TagsSearchFilter.apply),
        labels = labelsIds.map(LabelsSearchFilter.apply),
        operation = operationId.map(opId => OperationSearchFilter(Seq(opId))),
        question = questionIds.map(QuestionSearchFilter.apply),
        idea = ideaIds.map(IdeaSearchFilter.apply),
        content = content.map(ContentSearchFilter.apply),
        context = context.map(_.toContext),
        status = status.map(StatusSearchFilter.apply),
        minVotesCount = minVotesCount.map(MinVotesCountSearchFilter.apply),
        toEnrich = toEnrich.map(ToEnrichSearchFilter.apply),
        minScore = minScore.map(MinScoreSearchFilter.apply),
        language = language.map(LanguageSearchFilter.apply),
        country = country.map(CountrySearchFilter.apply),
        createdAt = createdBefore.map(createdBeforeDate => CreatedAtSearchFilter(Some(createdBeforeDate), None)),
        userTypes = userTypes.map(UserTypesSearchFilter.apply),
        user = userId.map(userId => UserSearchFilter(Seq(userId))),
        keywords = keywords.map(KeywordsSearchFilter)
      )

    SearchQuery(
      filters = filters,
      sort = Sort.parse(sort, order),
      limit = limit,
      skip = skip,
      language = requestContext.language
    )
  }
}

object ExhaustiveSearchRequest extends CirceFormatters {
  implicit val decoder: Decoder[ExhaustiveSearchRequest] = deriveDecoder[ExhaustiveSearchRequest]
}

@ApiModel
final case class VoteProposalRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "agree")
  voteKey: VoteKey,
  proposalKey: Option[String]
)

object VoteProposalRequest {
  implicit val decoder: Decoder[VoteProposalRequest] = deriveDecoder[VoteProposalRequest]
}

@ApiModel
final case class QualificationProposalRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "likeIt")
  qualificationKey: QualificationKey,
  @(ApiModelProperty @field)(dataType = "string", example = "agree")
  voteKey: VoteKey,
  proposalKey: Option[String]
)

object QualificationProposalRequest {
  implicit val decoder: Decoder[QualificationProposalRequest] = deriveDecoder[QualificationProposalRequest]
}

final case class PatchProposalRequest(
  slug: Option[String] = None,
  content: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  author: Option[UserId] = None,
  @(ApiModelProperty @field)(dataType = "list[string]")
  labels: Option[Seq[LabelId]] = None,
  @(ApiModelProperty @field)(
    dataType = "string",
    example = "Accepted",
    allowableValues = "Pending,Accepted,Refused,Postponed,Archived"
  )
  status: Option[ProposalStatus] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "other")
  refusalReason: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "list[string]")
  tags: Option[Seq[TagId]] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "2d791a66-3cd5-4a2e-a117-9daa68bd3a33")
  questionId: Option[QuestionId] = None,
  creationContext: Option[PatchRequestContext] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "2a774774-33ca-41a3-a0fa-65931397fbfc")
  ideaId: Option[IdeaId] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "3a9cd696-7e0b-4758-952c-04ae6798039a")
  operation: Option[OperationId] = None,
  @(ApiModelProperty @field)(dataType = "boolean")
  initialProposal: Option[Boolean] = None,
  keywords: Option[Seq[ProposalKeyword]] = None
)

object PatchProposalRequest {
  implicit val encoder: Encoder[PatchProposalRequest] = deriveEncoder[PatchProposalRequest]
  implicit val decoder: Decoder[PatchProposalRequest] = deriveDecoder[PatchProposalRequest]
}

final case class PatchRequestContext(
  @(ApiModelProperty @field)(dataType = "string", example = "5b13da67-17bb-413f-9f4f-e73699383153")
  requestId: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "af938667-a15a-482b-bd0f-681f09c83e51")
  sessionId: Option[SessionId] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "e52d2ac3-a929-43ec-acfa-fb1f486a8c75")
  visitorId: Option[VisitorId] = None,
  @(ApiModelProperty @field)(dataType = "dateTime")
  visitorCreatedAt: Option[ZonedDateTime] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "ea3caa65-0bc3-430a-9af9-e8c473730601")
  externalId: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Option[Country] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  detectedCountry: Option[Country] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Option[Language] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "2d791a66-3cd5-4a2e-a117-9daa68bd3a33")
  operation: Option[OperationId] = None,
  source: Option[String] = None,
  location: Option[String] = None,
  question: Option[String] = None,
  hostname: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "0.0.0.x")
  ipAddress: Option[String] = None,
  ipAddressHash: Option[String] = None,
  getParameters: Option[Map[String, String]] = None,
  userAgent: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "f626cabc-d0f1-49ef-aec1-eb8d50c8dda1")
  questionId: Option[QuestionId] = None,
  @(ApiModelProperty @field)(
    dataType = "string",
    example = "main-front",
    allowableValues = "main-front,legacy-front,backoffice,widget,widget-manager,dial,bi-batchs,dial-batchs,infra"
  )
  applicationName: Option[ApplicationName] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "main-front")
  referrer: Option[String] = None,
  customData: Option[Map[String, String]] = None
)

object PatchRequestContext extends CirceFormatters {
  implicit val encoder: Encoder[PatchRequestContext] = deriveEncoder[PatchRequestContext]
  implicit val decoder: Decoder[PatchRequestContext] = deriveDecoder[PatchRequestContext]
}

@ApiModel
final case class PatchProposalsIdeaRequest(
  @(ApiModelProperty @field)(dataType = "list[string]") proposalIds: Seq[ProposalId],
  @(ApiModelProperty @field)(dataType = "string", example = "f335b26e-f917-4247-99f2-dc63bdb2f99a") ideaId: IdeaId
)
object PatchProposalsIdeaRequest {
  implicit val decoder: Decoder[PatchProposalsIdeaRequest] = deriveDecoder[PatchProposalsIdeaRequest]
}

final case class NextProposalToModerateRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "2d791a66-3cd5-4a2e-a117-9daa68bd3a33")
  questionId: Option[QuestionId],
  toEnrich: Boolean,
  @(ApiModelProperty @field)(dataType = "int", example = "0")
  minVotesCount: Option[Int],
  @(ApiModelProperty @field)(dataType = "int", example = "0")
  minScore: Option[Double]
) {
  validate(requirePresent("questionId", questionId, Some("Next proposal needs a question")))
}

object NextProposalToModerateRequest {
  implicit val decoder: Decoder[NextProposalToModerateRequest] = deriveDecoder[NextProposalToModerateRequest]
}

final case class ProposalKeywordRequest(proposalId: ProposalId, keywords: Seq[ProposalKeyword])

object ProposalKeywordRequest {
  implicit val decoder: Decoder[ProposalKeywordRequest] = deriveDecoder[ProposalKeywordRequest]
}

final case class BulkAcceptProposal(@(ApiModelProperty @field)(dataType = "list[string]") proposalIds: Seq[ProposalId])

object BulkAcceptProposal {
  implicit val codec: Codec[BulkAcceptProposal] = deriveCodec[BulkAcceptProposal]
}

final case class BulkRefuseProposal(@(ApiModelProperty @field)(dataType = "list[string]") proposalIds: Seq[ProposalId])

object BulkRefuseProposal {
  implicit val codec: Codec[BulkRefuseProposal] = deriveCodec[BulkRefuseProposal]
}

final case class BulkTagProposal(
  @(ApiModelProperty @field)(dataType = "list[string]") proposalIds: Seq[ProposalId],
  @(ApiModelProperty @field)(dataType = "list[string]") tagIds: Seq[TagId]
)

object BulkTagProposal {
  implicit val codec: Codec[BulkTagProposal] = deriveCodec[BulkTagProposal]
}

final case class BulkDeleteTagProposal(
  @(ApiModelProperty @field)(dataType = "list[string]") proposalIds: Seq[ProposalId],
  @(ApiModelProperty @field)(dataType = "string", example = "50150a2c-e43f-4876-b38c-5426c4a0a5d9")
  tagId: TagId
)

object BulkDeleteTagProposal {
  implicit val codec: Codec[BulkDeleteTagProposal] = deriveCodec[BulkDeleteTagProposal]
}

final case class LockProposalsRequest(
  @(ApiModelProperty @field)(dataType = "list[string]") proposalIds: Set[ProposalId]
)

object LockProposalsRequest {
  implicit val codec: Codec[LockProposalsRequest] = deriveCodec[LockProposalsRequest]
}
