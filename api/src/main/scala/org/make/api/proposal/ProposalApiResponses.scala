package org.make.api.proposal

import java.time.ZonedDateTime

import org.make.api.user.UserResponse
import org.make.core.RequestContext
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.reference.{LabelId, Tag, TagId, ThemeId}
import org.make.core.user.UserId

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
                                  creationContext: RequestContext,
                                  createdAt: Option[ZonedDateTime],
                                  updatedAt: Option[ZonedDateTime],
                                  events: Seq[ProposalActionResponse])

final case class ProposalActionResponse(date: ZonedDateTime,
                                        user: Option[UserResponse],
                                        actionType: String,
                                        arguments: Map[String, String])

final case class ProposeProposalResponse(proposalId: ProposalId)

final case class ProposalsResultResponse(id: ProposalId,
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
                                         country: String,
                                         language: String,
                                         themeId: Option[ThemeId],
                                         tags: Seq[Tag])

final case class ProposalsResult(total: Int, results: Seq[IndexedProposal])

final case class VoteResponse(voteKey: VoteKey,
                              count: Int,
                              qualifications: Seq[QualificationResponse],
                              hasVoted: Boolean)

object VoteResponse {
  def parseVote(vote: Vote): VoteResponse =
    VoteResponse(
      voteKey = vote.key,
      count = vote.count,
      qualifications = vote.qualifications
        .map(qualification => QualificationResponse.parseQualification(qualification)),
      hasVoted = false
    )
}

final case class QualificationResponse(qualificationKey: QualificationKey, count: Int, hasQualified: Boolean)

object QualificationResponse {
  def parseQualification(qualification: Qualification): QualificationResponse =
    QualificationResponse(qualificationKey = qualification.key, count = qualification.count, hasQualified = false)
}
