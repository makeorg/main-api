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

package org.make.api.proposal

import java.time.ZonedDateTime

import org.make.core.RequestContext
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.idea.IdeaId
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.question.Question
import org.make.core.reference.LabelId
import org.make.core.tag.TagId
import org.make.core.user.{User, UserId}

sealed trait ProposalCommand {
  def proposalId: ProposalId
  def requestContext: RequestContext
}

final case class ProposeCommand(proposalId: ProposalId,
                                requestContext: RequestContext,
                                user: User,
                                createdAt: ZonedDateTime,
                                content: String,
                                question: Question)
    extends ProposalCommand

final case class UpdateProposalCommand(moderator: UserId,
                                       proposalId: ProposalId,
                                       requestContext: RequestContext,
                                       updatedAt: ZonedDateTime,
                                       newContent: Option[String],
                                       labels: Seq[LabelId],
                                       tags: Seq[TagId],
                                       idea: IdeaId,
                                       question: Question)
    extends ProposalCommand

final case class ViewProposalCommand(proposalId: ProposalId, requestContext: RequestContext) extends ProposalCommand

final case class GetProposal(proposalId: ProposalId, requestContext: RequestContext) extends ProposalCommand

final case class KillProposalShard(proposalId: ProposalId, requestContext: RequestContext) extends ProposalCommand

final case class AcceptProposalCommand(moderator: UserId,
                                       proposalId: ProposalId,
                                       requestContext: RequestContext,
                                       sendNotificationEmail: Boolean,
                                       newContent: Option[String],
                                       question: Question,
                                       labels: Seq[LabelId],
                                       tags: Seq[TagId],
                                       idea: Option[IdeaId])
    extends ProposalCommand

final case class RefuseProposalCommand(moderator: UserId,
                                       proposalId: ProposalId,
                                       requestContext: RequestContext,
                                       sendNotificationEmail: Boolean,
                                       refusalReason: Option[String])
    extends ProposalCommand

final case class PostponeProposalCommand(moderator: UserId, proposalId: ProposalId, requestContext: RequestContext)
    extends ProposalCommand

final case class VoteProposalCommand(proposalId: ProposalId,
                                     maybeUserId: Option[UserId],
                                     requestContext: RequestContext,
                                     voteKey: VoteKey,
                                     maybeOrganisationId: Option[UserId],
                                     vote: Option[VoteAndQualifications])
    extends ProposalCommand

final case class UnvoteProposalCommand(proposalId: ProposalId,
                                       maybeUserId: Option[UserId],
                                       requestContext: RequestContext,
                                       voteKey: VoteKey,
                                       maybeOrganisationId: Option[UserId],
                                       vote: Option[VoteAndQualifications])
    extends ProposalCommand

final case class QualifyVoteCommand(proposalId: ProposalId,
                                    maybeUserId: Option[UserId],
                                    requestContext: RequestContext,
                                    voteKey: VoteKey,
                                    qualificationKey: QualificationKey,
                                    vote: Option[VoteAndQualifications])
    extends ProposalCommand

final case class UnqualifyVoteCommand(proposalId: ProposalId,
                                      maybeUserId: Option[UserId],
                                      requestContext: RequestContext,
                                      voteKey: VoteKey,
                                      qualificationKey: QualificationKey,
                                      vote: Option[VoteAndQualifications])
    extends ProposalCommand

final case class LockProposalCommand(proposalId: ProposalId,
                                     moderatorId: UserId,
                                     moderatorName: Option[String],
                                     requestContext: RequestContext)
    extends ProposalCommand

final case class PatchProposalCommand(proposalId: ProposalId,
                                      userId: UserId,
                                      changes: PatchProposalRequest,
                                      requestContext: RequestContext)
    extends ProposalCommand

final case class AnonymizeProposalCommand(proposalId: ProposalId, requestContext: RequestContext = RequestContext.empty)
    extends ProposalCommand
