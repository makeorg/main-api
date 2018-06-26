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
import org.make.api.user.UserResponse
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.reference._
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.make.core.{CirceFormatters, RequestContext}

final case class ModerationProposalResponse(indexedProposal: IndexedProposal, ideaProposals: Seq[IndexedProposal])

final case class ProposalResponse(proposalId: ProposalId,
                                  slug: String,
                                  content: String,
                                  author: UserResponse,
                                  labels: Seq[LabelId],
                                  theme: Option[ThemeId] = None,
                                  status: ProposalStatus,
                                  refusalReason: Option[String] = None,
                                  tags: Seq[TagId] = Seq.empty,
                                  votes: Seq[Vote],
                                  context: RequestContext,
                                  createdAt: Option[ZonedDateTime],
                                  updatedAt: Option[ZonedDateTime],
                                  events: Seq[ProposalActionResponse],
                                  similarProposals: Seq[ProposalId],
                                  idea: Option[IdeaId],
                                  ideaProposals: Seq[IndexedProposal],
                                  operationId: Option[OperationId],
                                  language: Option[String],
                                  country: Option[String])

object ProposalResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[ProposalResponse] = deriveEncoder[ProposalResponse]
  implicit val decoder: Decoder[ProposalResponse] = deriveDecoder[ProposalResponse]
}

final case class ProposalActionResponse(date: ZonedDateTime,
                                        user: Option[UserResponse],
                                        actionType: String,
                                        arguments: Map[String, String])

object ProposalActionResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[ProposalActionResponse] = deriveEncoder[ProposalActionResponse]
  implicit val decoder: Decoder[ProposalActionResponse] = deriveDecoder[ProposalActionResponse]
}

final case class ProposeProposalResponse(proposalId: ProposalId)

object ProposeProposalResponse {
  implicit val encoder: ObjectEncoder[ProposeProposalResponse] = deriveEncoder[ProposeProposalResponse]
}

final case class ProposalResult(id: ProposalId,
                                userId: UserId,
                                content: String,
                                slug: String,
                                status: ProposalStatus,
                                createdAt: ZonedDateTime,
                                updatedAt: Option[ZonedDateTime],
                                votes: Seq[VoteResponse],
                                context: Option[Context],
                                trending: Option[String],
                                labels: Seq[String],
                                author: Author,
                                organisations: Seq[IndexedOrganisationInfo],
                                country: String,
                                language: String,
                                themeId: Option[ThemeId],
                                tags: Seq[IndexedTag],
                                myProposal: Boolean,
                                idea: Option[IdeaId],
                                operationId: Option[OperationId])

object ProposalResult extends CirceFormatters {
  implicit val encoder: ObjectEncoder[ProposalResult] = deriveEncoder[ProposalResult]
  implicit val decoder: Decoder[ProposalResult] = deriveDecoder[ProposalResult]

  def apply(indexedProposal: IndexedProposal,
            myProposal: Boolean,
            voteAndQualifications: Option[VoteAndQualifications]): ProposalResult =
    ProposalResult(
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
            case Some(VoteAndQualifications(indexedVote.key, _)) => true
            case _                                               => false
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
      operationId = indexedProposal.operationId
    )
}

final case class ProposalsResultResponse(total: Int, results: Seq[ProposalResult])

object ProposalsResultResponse {
  implicit val encoder: ObjectEncoder[ProposalsResultResponse] = deriveEncoder[ProposalsResultResponse]
  implicit val decoder: Decoder[ProposalsResultResponse] = deriveDecoder[ProposalsResultResponse]
}

final case class ProposalsResultSeededResponse(total: Int, results: Seq[ProposalResult], seed: Option[Int])

object ProposalsResultSeededResponse {
  implicit val encoder: ObjectEncoder[ProposalsResultSeededResponse] = deriveEncoder[ProposalsResultSeededResponse]
  implicit val decoder: Decoder[ProposalsResultSeededResponse] = deriveDecoder[ProposalsResultSeededResponse]
}

final case class VoteResponse(voteKey: VoteKey,
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
              case Some(VoteAndQualifications(_, keys)) if keys.contains(qualification.key) => true
              case _                                                                        => false
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
              case Some(VoteAndQualifications(_, keys)) if keys.contains(qualification.key) => true
              case _                                                                        => false
            })
        ),
      hasVoted = hasVoted
    )
}

final case class QualificationResponse(qualificationKey: QualificationKey, count: Int, hasQualified: Boolean)

object QualificationResponse {
  implicit val encoder: ObjectEncoder[QualificationResponse] = deriveEncoder[QualificationResponse]
  implicit val decoder: Decoder[QualificationResponse] = deriveDecoder[QualificationResponse]

  def parseQualification(qualification: Qualification, hasQualified: Boolean): QualificationResponse =
    QualificationResponse(
      qualificationKey = qualification.key,
      count = qualification.count,
      hasQualified = hasQualified
    )
  def parseQualification(qualification: IndexedQualification, hasQualified: Boolean): QualificationResponse =
    QualificationResponse(
      qualificationKey = qualification.key,
      count = qualification.count,
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
