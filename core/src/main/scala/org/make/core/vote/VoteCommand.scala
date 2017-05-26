package org.make.core.vote

import java.time.ZonedDateTime

import org.make.core.citizen.CitizenId
import org.make.core.proposition.PropositionId
import org.make.core.vote.VoteStatus.VoteStatus

trait VoteCommand {
  def voteId: VoteId
  def propositionId: PropositionId
}

case class PutVoteCommand(voteId: VoteId,
                          propositionId: PropositionId,
                          citizenId: CitizenId,
                          createdAt: ZonedDateTime,
                          status: VoteStatus)
    extends VoteCommand

case class ViewVoteCommand(voteId: VoteId, propositionId: PropositionId)
    extends VoteCommand

case class GetVote(voteId: VoteId)
