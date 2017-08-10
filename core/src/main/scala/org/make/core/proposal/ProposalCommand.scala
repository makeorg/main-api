package org.make.core.proposal

import java.time.ZonedDateTime

import org.make.core.user.UserId

sealed trait ProposalCommand {
  def proposalId: ProposalId
}

case class ProposeCommand(proposalId: ProposalId, userId: UserId, createdAt: ZonedDateTime, content: String)
    extends ProposalCommand

case class UpdateProposalCommand(proposalId: ProposalId, updatedAt: ZonedDateTime, content: String)
    extends ProposalCommand

case class ViewProposalCommand(proposalId: ProposalId) extends ProposalCommand

case class GetProposal(proposalId: ProposalId) extends ProposalCommand

case class KillProposalShard(proposalId: ProposalId) extends ProposalCommand
