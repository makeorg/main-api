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

package org.make.api.sequence

import org.make.core.RequestContext
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalId
import org.make.core.reference.ThemeId
import org.make.core.sequence.{Sequence, SequenceId, SequenceStatus}
import org.make.core.user.UserId

sealed trait SequenceCommand {
  def sequenceId: SequenceId
  def requestContext: RequestContext
}

final case class CreateSequenceCommand(sequenceId: SequenceId,
                                       title: String,
                                       slug: String,
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
                                       themeIds: Seq[ThemeId])
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
