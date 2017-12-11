package org.make.api.proposal

import java.time.ZonedDateTime

import org.make.core.RequestContext
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.{Proposal, ProposalId, QualificationKey, VoteKey}
import org.make.core.reference.{IdeaId, LabelId, TagId, ThemeId}
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
                                theme: Option[ThemeId] = None)
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
                                       newIdea: Option[IdeaId])
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
                                       similarProposals: Seq[ProposalId])
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
                                     vote: Option[VoteAndQualifications])
    extends ProposalCommand

final case class UnvoteProposalCommand(proposalId: ProposalId,
                                       maybeUserId: Option[UserId],
                                       requestContext: RequestContext,
                                       voteKey: VoteKey,
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

final case class PatchProposalCommand(proposalId: ProposalId,
                                      proposal: Proposal,
                                      requestContext: RequestContext = RequestContext.empty)
    extends ProposalCommand
