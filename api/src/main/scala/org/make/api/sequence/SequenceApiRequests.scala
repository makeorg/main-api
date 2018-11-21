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

package org.make.api.sequence

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.core.common.indexed.SortRequest
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalId
import org.make.core.question.QuestionId
import org.make.core.reference.ThemeId
import org.make.core.sequence._

import scala.annotation.meta.field

// ToDo: handle translations
@ApiModel
final case class CreateSequenceRequest(@(ApiModelProperty @field)(example = "ma séquence") title: String,
                                       @(ApiModelProperty @field)(dataType = "list[string]") themeIds: Seq[ThemeId],
                                       operationId: Option[OperationId],
                                       searchable: Boolean)

object CreateSequenceRequest {
  implicit val decoder: Decoder[CreateSequenceRequest] = deriveDecoder[CreateSequenceRequest]
}

@ApiModel
final case class AddProposalSequenceRequest(
  @(ApiModelProperty @field)(dataType = "list[string]") proposalIds: Seq[ProposalId]
)

object AddProposalSequenceRequest {
  implicit val decoder: Decoder[AddProposalSequenceRequest] = deriveDecoder[AddProposalSequenceRequest]
}

@ApiModel
final case class RemoveProposalSequenceRequest(
  @(ApiModelProperty @field)(dataType = "list[string]") proposalIds: Seq[ProposalId]
)

object RemoveProposalSequenceRequest {
  implicit val decoder: Decoder[RemoveProposalSequenceRequest] = deriveDecoder[RemoveProposalSequenceRequest]
}

@ApiModel
final case class UpdateSequenceRequest(
  title: Option[String],
  status: Option[String],
  operation: Option[OperationId],
  @(ApiModelProperty @field)(dataType = "list[string]") themeIds: Option[Seq[ThemeId]]
)

object UpdateSequenceRequest {
  implicit val decoder: Decoder[UpdateSequenceRequest] = deriveDecoder[UpdateSequenceRequest]
}

@ApiModel
final case class ExhaustiveSearchRequest(@(ApiModelProperty @field)(dataType = "list[string]") themeIds: Seq[ThemeId] =
                                           Seq.empty,
                                         title: Option[String] = None,
                                         slug: Option[String] = None,
                                         context: Option[ContextFilterRequest] = None,
                                         operationId: Option[OperationId] = None,
                                         status: Option[SequenceStatus] = None,
                                         searchable: Option[Boolean] = None,
                                         sorts: Seq[SortRequest] = Seq.empty,
                                         limit: Option[Int] = None,
                                         skip: Option[Int] = None) {
  def toSearchQuery: SearchQuery = {
    val filters: Option[SearchFilters] = {
      val themesFilter: Option[ThemesSearchFilter] = if (themeIds.isEmpty) None else Some(ThemesSearchFilter(themeIds))
      SearchFilters.parse(
        slug = slug.map(text => SlugSearchFilter(text)),
        themes = themesFilter,
        title = title.map(text => TitleSearchFilter(text)),
        context = context.map(_.toContext),
        operationId = operationId.map(OperationSearchFilter.apply),
        status = status.map(StatusSearchFilter.apply),
        searchable = searchable
      )
    }
    SearchQuery(filters = filters, sorts = sorts.map(_.toSort), limit = limit, skip = skip)
  }
}

object ExhaustiveSearchRequest {
  implicit val decoder: Decoder[ExhaustiveSearchRequest] = deriveDecoder[ExhaustiveSearchRequest]
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

object SearchStartSequenceRequest {
  implicit val decoder: Decoder[SearchStartSequenceRequest] = deriveDecoder[SearchStartSequenceRequest]
}

final case class SequenceConfigurationRequest(newProposalsRatio: Double,
                                              newProposalsVoteThreshold: Int,
                                              testedProposalsEngagementThreshold: Double,
                                              testedProposalsScoreThreshold: Double,
                                              testedProposalsControversyThreshold: Double,
                                              banditEnabled: Boolean,
                                              banditMinCount: Int,
                                              banditProposalsRatio: Double,
                                              ideaCompetitionEnabled: Boolean,
                                              ideaCompetitionTargetCount: Int,
                                              ideaCompetitionControversialRatio: Double,
                                              ideaCompetitionControversialCount: Int) {
  def toSequenceConfiguration(sequenceId: SequenceId, questionId: QuestionId): SequenceConfiguration = {
    SequenceConfiguration(
      sequenceId = sequenceId,
      questionId = questionId,
      newProposalsRatio = newProposalsRatio,
      newProposalsVoteThreshold = newProposalsVoteThreshold,
      testedProposalsEngagementThreshold = testedProposalsEngagementThreshold,
      testedProposalsScoreThreshold = testedProposalsScoreThreshold,
      testedProposalsControversyThreshold = testedProposalsControversyThreshold,
      banditEnabled = banditEnabled,
      banditMinCount = banditMinCount,
      banditProposalsRatio = banditProposalsRatio,
      ideaCompetitionEnabled = ideaCompetitionEnabled,
      ideaCompetitionTargetCount = ideaCompetitionTargetCount,
      ideaCompetitionControversialRatio = ideaCompetitionControversialRatio,
      ideaCompetitionControversialCount = ideaCompetitionControversialCount
    )
  }
}

object SequenceConfigurationRequest {
  implicit val decoder: Decoder[SequenceConfigurationRequest] = deriveDecoder[SequenceConfigurationRequest]
}
