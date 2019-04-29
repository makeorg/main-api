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

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.api.user.UserResponse
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.question.QuestionId
import org.make.core.reference._
import org.make.core.tag.{Tag, TagId, TagTypeId}
import org.make.core.user.UserId
import org.make.core.{CirceFormatters, RequestContext}

import scala.annotation.meta.field

final case class ModerationProposalResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "927074a0-a51f-4183-8e7a-bebc705c081b")
  proposalId: ProposalId,
  slug: String,
  content: String,
  author: UserResponse,
  @(ApiModelProperty @field)(dataType = "list[string]")
  labels: Seq[LabelId],
  @(ApiModelProperty @field)(dataType = "string", example = "9aff4846-3cb8-4737-aea0-2c4a608f30fd")
  theme: Option[ThemeId] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "Accepted")
  status: ProposalStatus,
  refusalReason: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "list[string]")
  tags: Seq[TagId] = Seq.empty,
  votes: Seq[Vote],
  context: RequestContext,
  @(ApiModelProperty @field)(example = "2019-01-23T16:32:00.000Z")
  createdAt: Option[ZonedDateTime],
  @(ApiModelProperty @field)(example = "2019-01-23T16:32:00.000Z")
  updatedAt: Option[ZonedDateTime],
  events: Seq[ProposalActionResponse],
  @(ApiModelProperty @field)(dataType = "string", example = "2a774774-33ca-41a3-a0fa-65931397fbfc")
  idea: Option[IdeaId],
  @(ApiModelProperty @field)(hidden = true)
  ideaProposals: Seq[IndexedProposal],
  @(ApiModelProperty @field)(dataType = "string", example = "2d791a66-3cd5-4a2e-a117-9daa68bd3a33")
  questionId: Option[QuestionId],
  @(ApiModelProperty @field)(dataType = "string", example = "3a9cd696-7e0b-4758-952c-04ae6798039a")
  operationId: Option[OperationId],
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Option[Language],
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Option[Country]
)

object ModerationProposalResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[ModerationProposalResponse] = deriveEncoder[ModerationProposalResponse]
  implicit val decoder: Decoder[ModerationProposalResponse] = deriveDecoder[ModerationProposalResponse]
}

final case class ProposalActionResponse(@(ApiModelProperty @field)(example = "2019-01-23T16:32:00.000Z")
                                        date: ZonedDateTime,
                                        user: Option[UserResponse],
                                        actionType: String,
                                        @(ApiModelProperty @field)(dataType = "java.util.Map")
                                        arguments: Map[String, String])

object ProposalActionResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[ProposalActionResponse] = deriveEncoder[ProposalActionResponse]
  implicit val decoder: Decoder[ProposalActionResponse] = deriveDecoder[ProposalActionResponse]
}

@ApiModel
final case class ProposalIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "927074a0-a51f-4183-8e7a-bebc705c081b")
  proposalId: ProposalId
)

object ProposalIdResponse {
  implicit val encoder: ObjectEncoder[ProposalIdResponse] = deriveEncoder[ProposalIdResponse]
}

@ApiModel
final case class ProposalResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "927074a0-a51f-4183-8e7a-bebc705c081b")
  id: ProposalId,
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  userId: UserId,
  content: String,
  slug: String,
  @(ApiModelProperty @field)(dataType = "string", example = "Accepted")
  status: ProposalStatus,
  @(ApiModelProperty @field)(dataType = "string", example = "2019-01-23T12:12:12.012Z")
  createdAt: ZonedDateTime,
  @(ApiModelProperty @field)(dataType = "string", example = "2019-01-23T12:12:12.012Z")
  updatedAt: Option[ZonedDateTime],
  votes: Seq[VoteResponse],
  context: Option[Context],
  trending: Option[String],
  labels: Seq[String],
  author: Author,
  organisations: Seq[IndexedOrganisationInfo],
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language,
  @(ApiModelProperty @field)(dataType = "string", example = "9aff4846-3cb8-4737-aea0-2c4a608f30fd")
  themeId: Option[ThemeId],
  tags: Seq[IndexedTag],
  myProposal: Boolean,
  @(ApiModelProperty @field)(dataType = "string", example = "2a774774-33ca-41a3-a0fa-65931397fbfc")
  idea: Option[IdeaId],
  @(ApiModelProperty @field)(dataType = "string", example = "2d791a66-3cd5-4a2e-a117-9daa68bd3a33")
  questionId: Option[QuestionId],
  @(ApiModelProperty @field)(dataType = "string", example = "3a9cd696-7e0b-4758-952c-04ae6798039a")
  operationId: Option[OperationId],
  proposalKey: String
)

object ProposalResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[ProposalResponse] = deriveEncoder[ProposalResponse]
  implicit val decoder: Decoder[ProposalResponse] = deriveDecoder[ProposalResponse]

  def apply(indexedProposal: IndexedProposal,
            myProposal: Boolean,
            voteAndQualifications: Option[VoteAndQualifications],
            proposalKey: String): ProposalResponse =
    ProposalResponse(
      id = indexedProposal.id,
      userId = indexedProposal.userId,
      content = indexedProposal.content,
      slug = indexedProposal.slug,
      status = indexedProposal.status,
      createdAt = indexedProposal.createdAt,
      updatedAt = indexedProposal.updatedAt,
      votes = indexedProposal.votes.map { indexedVote =>
        VoteResponse
          .parseVote(indexedVote, hasVoted = voteAndQualifications match {
            case Some(VoteAndQualifications(indexedVote.key, _, _, _)) => true
            case _                                                     => false
          }, voteAndQualifications)
      },
      context = indexedProposal.context,
      trending = indexedProposal.trending,
      labels = indexedProposal.labels,
      author = indexedProposal.author,
      organisations = indexedProposal.organisations,
      country = indexedProposal.country,
      language = indexedProposal.language,
      themeId = indexedProposal.themeId,
      tags = indexedProposal.tags,
      myProposal = myProposal,
      idea = indexedProposal.ideaId,
      questionId = indexedProposal.questionId,
      operationId = indexedProposal.operationId,
      proposalKey = proposalKey
    )
}

@ApiModel
final case class ProposalsResultResponse(total: Long, results: Seq[ProposalResponse])

object ProposalsResultResponse {
  implicit val encoder: ObjectEncoder[ProposalsResultResponse] = deriveEncoder[ProposalsResultResponse]
  implicit val decoder: Decoder[ProposalsResultResponse] = deriveDecoder[ProposalsResultResponse]
}

final case class ProposalsResultSeededResponse(
  total: Long,
  results: Seq[ProposalResponse],
  @(ApiModelProperty @field)(dataType = "int", example = "42") seed: Option[Int]
)

object ProposalsResultSeededResponse {
  implicit val encoder: ObjectEncoder[ProposalsResultSeededResponse] = deriveEncoder[ProposalsResultSeededResponse]
  implicit val decoder: Decoder[ProposalsResultSeededResponse] = deriveDecoder[ProposalsResultSeededResponse]
}

final case class ProposalResultWithUserVote(
  proposal: ProposalResponse,
  @(ApiModelProperty @field)(dataType = "string", example = "agree")
  vote: VoteKey,
  @(ApiModelProperty @field)(dataType = "string", example = "2019-01-23T12:12:12.012Z")
  voteDate: ZonedDateTime,
  voteDetails: Option[VoteResponse]
)
object ProposalResultWithUserVote extends CirceFormatters {
  implicit val encoder: ObjectEncoder[ProposalResultWithUserVote] = deriveEncoder[ProposalResultWithUserVote]
  implicit val decoder: Decoder[ProposalResultWithUserVote] = deriveDecoder[ProposalResultWithUserVote]
}

@ApiModel
final case class ProposalsResultWithUserVoteSeededResponse(total: Long,
                                                           results: Seq[ProposalResultWithUserVote],
                                                           @(ApiModelProperty @field)(dataType = "int", example = "42")
                                                           seed: Option[Int])

object ProposalsResultWithUserVoteSeededResponse {
  implicit val encoder: ObjectEncoder[ProposalsResultWithUserVoteSeededResponse] =
    deriveEncoder[ProposalsResultWithUserVoteSeededResponse]
  implicit val decoder: Decoder[ProposalsResultWithUserVoteSeededResponse] =
    deriveDecoder[ProposalsResultWithUserVoteSeededResponse]
}

@ApiModel
final case class VoteResponse(@(ApiModelProperty @field)(dataType = "string", example = "agree")
                              voteKey: VoteKey,
                              count: Int,
                              qualifications: Seq[QualificationResponse],
                              hasVoted: Boolean)

object VoteResponse {

  implicit val encoder: ObjectEncoder[VoteResponse] = deriveEncoder[VoteResponse]
  implicit val decoder: Decoder[VoteResponse] = deriveDecoder[VoteResponse]

  def parseVote(vote: Vote, hasVoted: Boolean, voteAndQualifications: Option[VoteAndQualifications]): VoteResponse =
    VoteResponse(
      voteKey = vote.key,
      count = vote.count,
      qualifications = vote.qualifications
        .map(
          qualification =>
            QualificationResponse.parseQualification(qualification, hasQualified = voteAndQualifications match {
              case Some(VoteAndQualifications(_, keys, _, _)) if keys.contains(qualification.key) => true
              case _                                                                              => false
            })
        ),
      hasVoted = hasVoted
    )

  def parseVote(vote: IndexedVote,
                hasVoted: Boolean,
                voteAndQualifications: Option[VoteAndQualifications]): VoteResponse =
    VoteResponse(
      voteKey = vote.key,
      count = vote.count,
      qualifications = vote.qualifications
        .map(
          qualification =>
            QualificationResponse.parseQualification(qualification, hasQualified = voteAndQualifications match {
              case Some(VoteAndQualifications(_, keys, _, _)) if keys.contains(qualification.key) => true
              case _                                                                              => false
            })
        ),
      hasVoted = hasVoted
    )
}

@ApiModel
final case class QualificationResponse(@(ApiModelProperty @field)(dataType = "string", example = "likeIt")
                                       qualificationKey: QualificationKey,
                                       count: Int,
                                       countVerified: Int,
                                       hasQualified: Boolean)

object QualificationResponse {
  implicit val encoder: ObjectEncoder[QualificationResponse] = deriveEncoder[QualificationResponse]
  implicit val decoder: Decoder[QualificationResponse] = deriveDecoder[QualificationResponse]

  def parseQualification(qualification: Qualification, hasQualified: Boolean): QualificationResponse =
    QualificationResponse(
      qualificationKey = qualification.key,
      count = qualification.count,
      countVerified = qualification.countVerified,
      hasQualified = hasQualified
    )
  def parseQualification(qualification: IndexedQualification, hasQualified: Boolean): QualificationResponse =
    QualificationResponse(
      qualificationKey = qualification.key,
      count = qualification.count,
      countVerified = qualification.countVerified,
      hasQualified = hasQualified
    )
}

final case class DuplicateResponse(ideaId: IdeaId,
                                   ideaName: String,
                                   proposalId: ProposalId,
                                   proposalContent: String,
                                   score: Double)

object DuplicateResponse {
  implicit val encoder: ObjectEncoder[DuplicateResponse] = deriveEncoder[DuplicateResponse]
  implicit val decoder: Decoder[DuplicateResponse] = deriveDecoder[DuplicateResponse]
}

@ApiModel
final case class TagForProposalResponse(@(ApiModelProperty @field)(dataType = "string", example = "tag-slug") id: TagId,
                                        label: String,
                                        @(ApiModelProperty @field)(dataType = "string") tagTypeId: TagTypeId,
                                        weight: Float,
                                        @(ApiModelProperty @field)(dataType = "string") questionId: Option[QuestionId],
                                        checked: Boolean,
                                        predicted: Boolean)

object TagForProposalResponse {
  implicit val encoder: ObjectEncoder[TagForProposalResponse] = deriveEncoder[TagForProposalResponse]
  implicit val decoder: Decoder[TagForProposalResponse] = deriveDecoder[TagForProposalResponse]

  def apply(tag: Tag, checked: Boolean, predicted: Boolean): TagForProposalResponse =
    TagForProposalResponse(
      id = tag.tagId,
      label = tag.label,
      tagTypeId = tag.tagTypeId,
      weight = tag.weight,
      questionId = tag.questionId,
      checked = checked,
      predicted = predicted
    )
}

final case class TagsForProposalResponse(tags: Seq[TagForProposalResponse], modelName: String)

object TagsForProposalResponse {
  implicit val encoder: ObjectEncoder[TagsForProposalResponse] = deriveEncoder[TagsForProposalResponse]
  implicit val decoder: Decoder[TagsForProposalResponse] = deriveDecoder[TagsForProposalResponse]

  val empty = TagsForProposalResponse(tags = Seq.empty, modelName = "")
}
