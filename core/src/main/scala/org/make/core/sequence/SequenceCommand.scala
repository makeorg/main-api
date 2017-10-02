package org.make.core.sequence

import org.make.core.RequestContext
import org.make.core.proposal.ProposalId
import org.make.core.reference.{TagId, ThemeId}
import org.make.core.user.UserId

sealed trait SequenceCommand {
  def sequenceId: SequenceId
  def requestContext: RequestContext
}

final case class CreateSequenceCommand(sequenceId: SequenceId,
                                       title: String,
                                       slug: String,
                                       tagIds: Seq[TagId] = Seq.empty,
                                       themeIds: Seq[ThemeId] = Seq.empty,
                                       requestContext: RequestContext,
                                       moderatorId: UserId,
                                       status: SequenceStatus)
    extends SequenceCommand

final case class UpdateSequenceCommand(sequenceId: SequenceId,
                                       requestContext: RequestContext,
                                       moderatorId: UserId,
                                       title: String)
    extends SequenceCommand

final case class RemoveProposalsSequenceCommand(sequenceId: SequenceId,
                                                proposalIds: Seq[ProposalId],
                                                requestContext: RequestContext,
                                                moderatorId: UserId)
    extends SequenceCommand
final case class AddProposalsSequenceCommand(sequenceId: SequenceId,
                                             proposalIds: Seq[ProposalId],
                                             requestContext: RequestContext,
                                             moderatorId: UserId)
    extends SequenceCommand
final case class ViewSequenceCommand(sequenceId: SequenceId, requestContext: RequestContext) extends SequenceCommand
final case class GetSequence(sequenceId: SequenceId, requestContext: RequestContext) extends SequenceCommand
final case class KillSequenceShard(sequenceId: SequenceId, requestContext: RequestContext) extends SequenceCommand
