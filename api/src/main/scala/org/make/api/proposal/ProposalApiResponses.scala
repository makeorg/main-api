package org.make.api.proposal

import java.time.ZonedDateTime

import org.make.api.user.UserResponse
import org.make.core.RequestContext
import org.make.core.proposal.indexed.{Qualification, QualificationKey, Vote, VoteKey}
import org.make.core.proposal.{ProposalId, ProposalStatus}
import org.make.core.reference.{LabelId, TagId, ThemeId}
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

final case class VoteResponse(voteKey: VoteKey,
                              count: Int = 0,
                              qualifications: Seq[QualificationResponse],
                              hasVoted: Boolean)

object VoteResponse {
  def parseVote(vote: Vote, maybeUserId: Option[UserId], sessionId: String): VoteResponse =
    VoteResponse(
      voteKey = vote.key,
      count = vote.count,
      qualifications = vote.qualifications
        .map(qualification => QualificationResponse.parseQualification(qualification, maybeUserId, sessionId)),
      hasVoted = vote.userIds.contains(maybeUserId.getOrElse(UserId(""))) || vote.sessionIds.contains(sessionId)
    )
}

final case class QualificationResponse(qualificationKey: QualificationKey, count: Int = 0, hasQualified: Boolean)

object QualificationResponse {
  def parseQualification(qualification: Qualification,
                         maybeUserId: Option[UserId],
                         sessionId: String): QualificationResponse =
    QualificationResponse(
      qualificationKey = qualification.key,
      count = qualification.count,
      hasQualified = qualification.userIds.contains(maybeUserId.getOrElse(UserId(""))) || qualification.sessionIds
        .contains(sessionId)
    )
}
