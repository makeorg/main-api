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
import org.make.core.operation.OperationId
import org.make.core.proposal.{Proposal, ProposalId, QualificationKey, VoteKey, _}
import org.make.core.reference.{Country, LabelId, Language, ThemeId}
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
                                operation: Option[OperationId] = None,
                                theme: Option[ThemeId] = None,
                                language: Option[Language],
                                country: Option[Country])
    extends ProposalCommand

final case class UpdateProposalCommand(moderator: UserId,
                                       proposalId: ProposalId,
                                       requestContext: RequestContext,
                                       updatedAt: ZonedDateTime,
                                       newContent: Option[String],
                                       theme: Option[ThemeId],
                                       labels: Seq[LabelId],
                                       tags: Seq[TagId],
                                       similarProposals: Seq[ProposalId],
                                       idea: Option[IdeaId],
                                       operation: Option[OperationId])
    extends ProposalCommand

final case class ViewProposalCommand(proposalId: ProposalId, requestContext: RequestContext) extends ProposalCommand

final case class GetProposal(proposalId: ProposalId, requestContext: RequestContext) extends ProposalCommand

final case class KillProposalShard(proposalId: ProposalId, requestContext: RequestContext) extends ProposalCommand

final case class AcceptProposalCommand(moderator: UserId,
                                       proposalId: ProposalId,
                                       requestContext: RequestContext,
                                       sendNotificationEmail: Boolean,
                                       newContent: Option[String],
                                       theme: Option[ThemeId],
                                       labels: Seq[LabelId],
                                       tags: Seq[TagId],
                                       similarProposals: Seq[ProposalId],
                                       idea: Option[IdeaId],
                                       operation: Option[OperationId])
    extends ProposalCommand

final case class UpdateDuplicatedProposalsCommand(proposalId: ProposalId,
                                                  duplicates: Seq[ProposalId],
                                                  requestContext: RequestContext = RequestContext.empty)
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
                                     organisationInfo: Option[OrganisationInfo],
                                     vote: Option[VoteAndQualifications])
    extends ProposalCommand

final case class UnvoteProposalCommand(proposalId: ProposalId,
                                       maybeUserId: Option[UserId],
                                       requestContext: RequestContext,
                                       voteKey: VoteKey,
                                       organisationInfo: Option[OrganisationInfo],
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

final case class ClearSimilarProposalsCommand(proposalId: ProposalId, requestContext: RequestContext)
    extends ProposalCommand

final case class RemoveSimilarProposalCommand(proposalId: ProposalId,
                                              similarToRemove: ProposalId,
                                              requestContext: RequestContext)
    extends ProposalCommand

final case class ReplaceProposalCommand(proposalId: ProposalId,
                                        proposal: Proposal,
                                        requestContext: RequestContext = RequestContext.empty)
    extends ProposalCommand

final case class PatchProposalCommand(proposalId: ProposalId,
                                      userId: UserId,
                                      changes: PatchProposalRequest,
                                      requestContext: RequestContext)
    extends ProposalCommand

final case class AnonymizeProposalCommand(proposalId: ProposalId, requestContext: RequestContext = RequestContext.empty)
    extends ProposalCommand
