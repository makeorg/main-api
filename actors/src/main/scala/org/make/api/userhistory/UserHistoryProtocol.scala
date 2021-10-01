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

import akka.actor.typed.ActorRef
import org.make.api.sessionhistory.Ack
import org.make.api.technical.{ActorCommand, ActorProtocol}
import org.make.api.userhistory.UserHistoryResponse.SessionEventsInjected
import org.make.core.MakeSerializable
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.user.UserId
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

//State

final case class UserVotesAndQualifications(votesAndQualifications: Map[ProposalId, VoteAndQualifications])
    extends MakeSerializable

object UserVotesAndQualifications {
  implicit val formatter: RootJsonFormat[UserVotesAndQualifications] =
    DefaultJsonProtocol.jsonFormat1(UserVotesAndQualifications.apply)
}

// Protocol

sealed trait UserHistoryActorProtocol extends ActorProtocol

// Commands

sealed trait UserHistoryCommand extends ActorCommand[UserId] with UserHistoryActorProtocol {
  def id: UserId = userId
  def userId: UserId
}

final case class RequestVoteValues(
  userId: UserId,
  proposalIds: Seq[ProposalId],
  replyTo: ActorRef[UserHistoryResponse[Map[ProposalId, VoteAndQualifications]]]
) extends UserHistoryCommand

final case class RequestUserVotedProposalsPaginate(
  userId: UserId,
  filterVotes: Option[Seq[VoteKey]] = None,
  filterQualifications: Option[Seq[QualificationKey]] = None,
  proposalsIds: Option[Seq[ProposalId]] = None,
  limit: Int,
  skip: Int,
  replyTo: ActorRef[UserHistoryResponse[Seq[ProposalId]]]
) extends UserHistoryCommand

final case class UserHistoryTransactionalEnvelope[T <: TransactionalUserHistoryEvent[_]](
  userId: UserId,
  event: T,
  replyTo: ActorRef[UserHistoryResponse[Ack.type]]
) extends UserHistoryCommand

final case class UserHistoryEnvelope[T <: UserHistoryEvent[_]](userId: UserId, event: T) extends UserHistoryCommand

final case class InjectSessionEvents(
  userId: UserId,
  events: Seq[UserHistoryEvent[_]],
  replyTo: ActorRef[UserHistoryResponse[SessionEventsInjected.type]]
) extends UserHistoryCommand

final case class Stop(userId: UserId, replyTo: ActorRef[UserHistoryResponse[Unit]]) extends UserHistoryCommand

// Responses

final case class UserHistoryResponse[T](value: T) extends UserHistoryActorProtocol

object UserHistoryResponse {
  case object SessionEventsInjected
}
