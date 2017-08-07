package org.make.core.vote

import java.time.ZonedDateTime

import org.make.core.user.UserId
import org.make.core.proposal.ProposalId
import org.make.core.vote.VoteStatus.VoteStatus

trait VoteCommand {
  def voteId: VoteId
  def proposalId: ProposalId
}

case class PutVoteCommand(voteId: VoteId,
                          proposalId: ProposalId,
                          userId: UserId,
                          createdAt: ZonedDateTime,
                          status: VoteStatus)
    extends VoteCommand

case class ViewVoteCommand(voteId: VoteId, proposalId: ProposalId) extends VoteCommand

case class GetVote(voteId: VoteId)
