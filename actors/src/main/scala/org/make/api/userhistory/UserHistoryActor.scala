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

package org.make.api.userhistory

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import grizzled.slf4j.Logging
import org.make.api.sessionhistory.Ack
import org.make.api.technical.EffectBuilderHelper._
import org.make.api.userhistory.UserHistoryResponse.SessionEventsInjected
import org.make.core.history.HistoryActions._
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}

import java.time.ZonedDateTime

object UserHistoryActor extends Logging {

  val JournalPluginId: String = "make-api.event-sourcing.users.journal"
  val SnapshotPluginId: String = "make-api.event-sourcing.users.snapshot"
  val QueryJournalPluginId: String = "make-api.event-sourcing.users.query"

  def apply(): Behavior[UserHistoryCommand] = {
    Behaviors.setup { implicit context =>
      EventSourcedBehavior
        .withEnforcedReplies[UserHistoryCommand, UserHistoryEvent[_], UserVotesAndQualifications](
          persistenceId = PersistenceId.ofUniqueId(context.self.path.name),
          emptyState = UserVotesAndQualifications(Map.empty),
          commandHandler(),
          eventHandler
        )
        .withJournalPluginId(JournalPluginId)
        .withSnapshotPluginId(SnapshotPluginId)
        .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 10, keepNSnapshots = 50))
    }
  }

  def getVotesValues(
    userHistory: UserVotesAndQualifications,
    proposalIds: Seq[ProposalId]
  ): UserHistoryResponse[Map[ProposalId, VoteAndQualifications]] = {
    UserHistoryResponse(userHistory.votesAndQualifications.filter {
      case (proposalId, _) => proposalIds.contains(proposalId)
    })
  }

  def getVotedProposals(
    userHistory: UserVotesAndQualifications,
    filterVotes: Option[Seq[VoteKey]],
    filterQualifications: Option[Seq[QualificationKey]],
    proposalIds: Option[Seq[ProposalId]],
    limit: Int,
    skip: Int
  ): UserHistoryResponse[Seq[ProposalId]] = {
    val response: Seq[ProposalId] = userHistory.votesAndQualifications.filter {
      case (proposalId, votesAndQualifications) =>
        proposalIds.forall(_.contains(proposalId)) &&
          filterVotes.forall(_.contains(votesAndQualifications.voteKey)) &&
          filterQualifications.forall { qualifications =>
            votesAndQualifications.qualificationKeys.exists { case (value, _) => qualifications.contains(value) }
          } && votesAndQualifications.trust.isTrusted
    }.toSeq.sortBy { case (_, votesAndQualifications) => votesAndQualifications.date }.map {
      case (proposalId, _) => proposalId
    }.slice(skip, limit)
    UserHistoryResponse(response)
  }

  def commandHandler()(implicit context: ActorContext[_]): (
    UserVotesAndQualifications,
    UserHistoryCommand
  ) => ReplyEffect[UserHistoryEvent[_], UserVotesAndQualifications] = {
    case (_, command: InjectSessionEvents) =>
      Effect
        .persist(command.events)
        .thenReply(command.replyTo)(_ => UserHistoryResponse(SessionEventsInjected))
    case (state, RequestVoteValues(_, values, replyTo)) => onRetrieveVoteValues(state, values, replyTo)
    case (
        state,
        RequestUserVotedProposalsPaginate(_, filterVotes, filterQualifications, proposalsIds, limit, skip, replyTo)
        ) =>
      onRetrieveUserVotedProposals(state, filterVotes, filterQualifications, proposalsIds, limit, skip, replyTo)
    case (_, UserHistoryTransactionalEnvelope(_, event: TransactionalUserHistoryEvent[_], replyTo)) =>
      Effect
        .persist[UserHistoryEvent[_], UserVotesAndQualifications](event)
        .thenPublish(event)
        .thenReply(replyTo)(_ => UserHistoryResponse(Ack))
    case (_, UserHistoryEnvelope(_, event: UserHistoryEvent[_])) =>
      Effect
        .persist[UserHistoryEvent[_], UserVotesAndQualifications](event)
        .thenPublish(event)
        .thenNoReply()
    case (_, Stop(_, replyTo)) => Effect.stop().thenReply(replyTo)(_ => UserHistoryResponse(Ack))
  }

  def eventHandler: (UserVotesAndQualifications, UserHistoryEvent[_]) => UserVotesAndQualifications = {
    case (state, LogUserVoteEvent(_, _, UserAction(date, _, UserVote(proposalId, voteKey, voteTrust)))) =>
      applyVoteEvent(state, proposalId, voteKey, date, voteTrust)
    case (state, LogUserUnvoteEvent(_, _, UserAction(_, _, UserUnvote(proposalId, voteKey, _)))) =>
      applyUnvoteEvent(state, proposalId, voteKey)
    case (
        state,
        LogUserQualificationEvent(_, _, UserAction(_, _, UserQualification(proposalId, qualificationKey, voteTrust)))
        ) =>
      applyQualificationEvent(state, proposalId, qualificationKey, voteTrust)
    case (
        state,
        LogUserUnqualificationEvent(_, _, UserAction(_, _, UserUnqualification(proposalId, qualificationKey, _)))
        ) =>
      applyUnqualificationEvent(state, proposalId, qualificationKey)
    case (state, _) => state
  }

  def onRetrieveVoteValues(
    state: UserVotesAndQualifications,
    proposalIds: Seq[ProposalId],
    replyTo: ActorRef[UserHistoryResponse[Map[ProposalId, VoteAndQualifications]]]
  ): ReplyEffect[UserHistoryEvent[_], UserVotesAndQualifications] = {
    Effect.reply(replyTo)(getVotesValues(state, proposalIds))
  }

  def onRetrieveUserVotedProposals(
    state: UserVotesAndQualifications,
    filterVotes: Option[Seq[VoteKey]],
    filterQualifications: Option[Seq[QualificationKey]],
    proposalIds: Option[Seq[ProposalId]],
    limit: Int,
    skip: Int,
    replyTo: ActorRef[UserHistoryResponse[Seq[ProposalId]]]
  ): ReplyEffect[UserHistoryEvent[_], UserVotesAndQualifications] = {
    Effect.reply(replyTo)(getVotedProposals(state, filterVotes, filterQualifications, proposalIds, limit, skip))
  }

  def applyVoteEvent(
    state: UserVotesAndQualifications,
    proposalId: ProposalId,
    voteKey: VoteKey,
    voteDate: ZonedDateTime,
    voteTrust: VoteTrust
  ): UserVotesAndQualifications = {
    state.votesAndQualifications.get(proposalId) match {
      case Some(voteAndQualifications) if voteAndQualifications.voteKey == voteKey => state
      case _ =>
        state.copy(votesAndQualifications = state.votesAndQualifications + (proposalId -> VoteAndQualifications(
          voteKey,
          Map.empty,
          voteDate,
          voteTrust
        ))
        )
    }
  }

  def applyQualificationEvent(
    state: UserVotesAndQualifications,
    proposalId: ProposalId,
    qualificationKey: QualificationKey,
    voteTrust: VoteTrust
  ): UserVotesAndQualifications = {
    state.votesAndQualifications.get(proposalId) match {
      case None => state
      case Some(voteAndQualifications) =>
        val newVoteAndQualifications: VoteAndQualifications = voteAndQualifications
          .copy(qualificationKeys = voteAndQualifications.qualificationKeys + (qualificationKey -> voteTrust))
        state
          .copy(votesAndQualifications = state.votesAndQualifications + (proposalId -> newVoteAndQualifications))
    }
  }

  def applyUnqualificationEvent(
    state: UserVotesAndQualifications,
    proposalId: ProposalId,
    qualificationKey: QualificationKey
  ): UserVotesAndQualifications = {
    state.votesAndQualifications.get(proposalId) match {
      case None => state
      case Some(voteAndQualifications) =>
        val newVoteAndQualifications =
          voteAndQualifications
            .copy(qualificationKeys = voteAndQualifications.qualificationKeys.filter {
              case (key, _) => key != qualificationKey
            })
        state
          .copy(votesAndQualifications = state.votesAndQualifications + (proposalId -> newVoteAndQualifications))
    }
  }

  def applyUnvoteEvent(
    state: UserVotesAndQualifications,
    proposalId: ProposalId,
    voteKey: VoteKey
  ): UserVotesAndQualifications = {
    if (state.votesAndQualifications.get(proposalId).exists(_.voteKey == voteKey)) {
      state.copy(votesAndQualifications = state.votesAndQualifications - proposalId)
    } else {
      state
    }
  }
}
