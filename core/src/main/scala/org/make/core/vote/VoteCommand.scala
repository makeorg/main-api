package org.make.core.vote

import org.make.core.citizen.CitizenId
import org.make.core.proposition.PropositionId

trait VoteCommand {
  def voteId: VoteId
}

case class AgreeCommand(voteId: VoteId, propositionId: PropositionId, citizenId: CitizenId) extends VoteCommand

case class DisagreeCommand(voteId: VoteId, propositionId: PropositionId, citizenId: CitizenId) extends VoteCommand

case class UnsureCommand(voteId: VoteId, propositionId: PropositionId, citizenId: CitizenId) extends VoteCommand