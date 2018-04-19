package org.make.api.sequence

import org.make.core.RequestContext
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalId
import org.make.core.reference.ThemeId
import org.make.core.sequence.{Sequence, SequenceId, SequenceStatus}
import org.make.core.tag.TagId
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
                                       operationId: Option[OperationId] = None,
                                       requestContext: RequestContext,
                                       moderatorId: UserId,
                                       status: SequenceStatus,
                                       searchable: Boolean)
    extends SequenceCommand

final case class UpdateSequenceCommand(sequenceId: SequenceId,
                                       requestContext: RequestContext,
                                       moderatorId: UserId,
                                       title: Option[String],
                                       status: Option[SequenceStatus],
                                       operationId: Option[OperationId] = None,
                                       themeIds: Seq[ThemeId],
                                       tagIds: Seq[TagId])
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
final case class PatchSequenceCommand(sequenceId: SequenceId,
                                      sequence: Sequence,
                                      requestContext: RequestContext = RequestContext.empty)
    extends SequenceCommand
