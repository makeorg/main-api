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

import java.time.ZonedDateTime

import com.sksamuel.elastic4s.searches.suggestion.Fuzziness
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.api.technical.MakeRandom
import org.make.api.technical.businessconfig.{BusinessConfig, FrontConfiguration}
import org.make.core.Validation._
import org.make.core.common.indexed.SortRequest
import org.make.core.idea.{CountrySearchFilter, IdeaId, LanguageSearchFilter}
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, LabelId, Language, ThemeId}
import org.make.core.session.{SessionId, VisitorId}
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.make.core.{CirceFormatters, RequestContext, Validation}

import scala.annotation.meta.field

final case class ProposeProposalRequest(content: String,
                                        operationId: Option[OperationId],
                                        questionId: Option[QuestionId],
                                        language: Language,
                                        country: Country) {
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
                                       idea: Option[IdeaId],
                                       labels: Seq[LabelId],
                                       tags: Seq[TagId],
                                       // TODO: remove similarProposals once BO stops sending them
                                       similarProposals: Option[Seq[ProposalId]],
                                       questionId: Option[QuestionId],
                                       theme: Option[ThemeId],
                                       operation: Option[OperationId])

object UpdateProposalRequest {
  implicit val decoder: Decoder[UpdateProposalRequest] = deriveDecoder[UpdateProposalRequest]
}

final case class ValidateProposalRequest(newContent: Option[String],
                                         sendNotificationEmail: Boolean,
                                         labels: Seq[LabelId],
                                         tags: Seq[TagId],
                                         // TODO: remove similarProposals once BO stops sending them
                                         similarProposals: Option[Seq[ProposalId]],
                                         idea: Option[IdeaId],
                                         theme: Option[ThemeId],
                                         operation: Option[OperationId],
                                         questionId: Option[QuestionId])

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
                               initialProposal: Option[Boolean] = None,
                               tagsIds: Option[Seq[TagId]] = None,
                               labelsIds: Option[Seq[LabelId]] = None,
                               operationId: Option[OperationId] = None,
                               questionIds: Option[Seq[QuestionId]] = None,
                               @Deprecated trending: Option[String] = None,
                               content: Option[String] = None,
                               slug: Option[String] = None,
                               seed: Option[Int] = None,
                               context: Option[ContextFilterRequest] = None,
                               language: Option[Language] = None,
                               country: Option[Country] = None,
                               @Deprecated sort: Option[SortRequest] = None,
                               limit: Option[Int] = None,
                               skip: Option[Int] = None,
                               @Deprecated isRandom: Option[Boolean] = Some(false),
                               sortAlgorithm: Option[String] = None) {

  def toSearchQuery(requestContext: RequestContext): SearchQuery = {
    val fuzziness = Fuzziness.Auto
    val filters: Option[SearchFilters] =
      SearchFilters.parse(
        proposals = proposalIds.map(ProposalSearchFilter.apply),
        initialProposal = initialProposal.map(InitialProposalFilter.apply),
        tags = tagsIds.map(TagsSearchFilter.apply),
        labels = labelsIds.map(LabelsSearchFilter.apply),
        operation = operationId.map(OperationSearchFilter.apply),
        question = questionIds.map(QuestionSearchFilter.apply),
        trending = trending.map(value => TrendingSearchFilter(value)),
        content = content.map(text => {
          ContentSearchFilter(text, Some(fuzziness))
        }),
        slug = slug.map(value => SlugSearchFilter(value)),
        context = context.map(_.toContext),
        language = language.map(LanguageSearchFilter.apply),
        country = country.map(CountrySearchFilter.apply)
      )

    val randomSeed: Int = seed.getOrElse(MakeRandom.random.nextInt())
    val searchSortAlgorithm: Option[SortAlgorithm] = AlgorithmSelector
      .select(sortAlgorithm, randomSeed)
      // Once the Deprecated field `isRandom` is deleted, replace following code by `None`
      .orElse(isRandom.flatMap { randomise =>
        if (randomise) {
          Some(RandomAlgorithm(Some(randomSeed)))
        } else {
          None
        }
      })
    SearchQuery(
      filters = filters,
      sort = sort.map(_.toSort),
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

final case class ExhaustiveSearchRequest(proposalIds: Option[Seq[ProposalId]] = None,
                                         initialProposal: Option[Boolean] = None,
                                         tagsIds: Option[Seq[TagId]] = None,
                                         labelsIds: Option[Seq[LabelId]] = None,
                                         operationId: Option[OperationId] = None,
                                         questionIds: Option[Seq[QuestionId]] = None,
                                         ideaId: Option[IdeaId] = None,
                                         trending: Option[String] = None,
                                         content: Option[String] = None,
                                         context: Option[ContextFilterRequest] = None,
                                         status: Option[Seq[ProposalStatus]] = None,
                                         minVotesCount: Option[Int] = None,
                                         toEnrich: Option[Boolean] = None,
                                         minScore: Option[Float] = None,
                                         language: Option[Language] = None,
                                         country: Option[Country] = None,
                                         sort: Option[SortRequest] = None,
                                         limit: Option[Int] = None,
                                         skip: Option[Int] = None,
                                         createdBefore: Option[ZonedDateTime] = None) {
  def toSearchQuery(requestContext: RequestContext): SearchQuery = {
    val fuzziness = Fuzziness.Auto
    val filters: Option[SearchFilters] =
      SearchFilters.parse(
        proposals = proposalIds.map(ProposalSearchFilter.apply),
        initialProposal = initialProposal.map(InitialProposalFilter.apply),
        tags = tagsIds.map(TagsSearchFilter.apply),
        labels = labelsIds.map(LabelsSearchFilter.apply),
        operation = operationId.map(OperationSearchFilter.apply),
        question = questionIds.map(QuestionSearchFilter.apply),
        idea = ideaId.map(IdeaSearchFilter.apply),
        trending = trending.map(value => TrendingSearchFilter(value)),
        content = content.map(text    => ContentSearchFilter(text, Some(fuzziness))),
        context = context.map(_.toContext),
        status = status.map(StatusSearchFilter.apply),
        minVotesCount = minVotesCount.map(MinVotesCountSearchFilter.apply),
        toEnrich = toEnrich.map(ToEnrichSearchFilter.apply),
        minScore = minScore.map(MinScoreSearchFilter.apply),
        language = language.map(LanguageSearchFilter.apply),
        country = country.map(CountrySearchFilter.apply),
        createdAt = createdBefore.map(createdBeforeDate => CreatedAtSearchFilter(Some(createdBeforeDate), None))
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

object ExhaustiveSearchRequest extends CirceFormatters {
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
                                      language: Option[Language] = None,
                                      questionId: Option[QuestionId] = None,
                                      country: Option[Country] = None)

object PatchProposalRequest {
  implicit val decoder: Decoder[PatchProposalRequest] = deriveDecoder[PatchProposalRequest]
}

final case class PatchRequestContext(currentTheme: Option[ThemeId] = None,
                                     requestId: Option[String] = None,
                                     sessionId: Option[SessionId] = None,
                                     visitorId: Option[VisitorId] = None,
                                     externalId: Option[String] = None,
                                     country: Option[Country] = None,
                                     language: Option[Language] = None,
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

final case class NextProposalToModerateRequest(questionId: Option[QuestionId],
                                               toEnrich: Boolean,
                                               minVotesCount: Option[Int],
                                               minScore: Option[Float]) {
  validate(requirePresent("questionId", questionId, Some("Next proposal needs a question")))
}

object NextProposalToModerateRequest {
  implicit val decoder: Decoder[NextProposalToModerateRequest] = deriveDecoder[NextProposalToModerateRequest]

}
