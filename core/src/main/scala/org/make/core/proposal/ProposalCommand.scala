package org.make.core.proposal

import java.time.ZonedDateTime

import org.make.core.RequestContext
import org.make.core.user.UserId

sealed trait ProposalCommand {
  def proposalId: ProposalId
  def context: RequestContext
}

final case class ProposeCommand(proposalId: ProposalId,
                                context: RequestContext,
                                userId: UserId,
                                createdAt: ZonedDateTime,
                                content: String)
    extends ProposalCommand

final case class UpdateProposalCommand(proposalId: ProposalId,
                                       context: RequestContext,
                                       updatedAt: ZonedDateTime,
                                       content: String)
    extends ProposalCommand

final case class ViewProposalCommand(proposalId: ProposalId, context: RequestContext) extends ProposalCommand

final case class GetProposal(proposalId: ProposalId, context: RequestContext) extends ProposalCommand

final case class KillProposalShard(proposalId: ProposalId, context: RequestContext) extends ProposalCommand
