/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.api.sessionhistory

import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.ActorRef
import akka.persistence.typed.scaladsl.{Effect, ReplyEffect}
import akka.util.Timeout
import grizzled.slf4j.Logging
import org.make.api.extensions.MakeSettings
import org.make.api.sessionhistory.SessionHistoryActor._
import org.make.api.sessionhistory.SessionHistoryResponse.Error.{ExpiredSession, LockAlreadyAcquired}
import org.make.api.sessionhistory.SessionHistoryResponse.{Envelope, LockAcquired, LockReleased}
import org.make.api.technical.ActorSystemHelper._
import org.make.api.technical.BetterLoggingActors.BetterLoggingTypedActorRef
import org.make.api.userhistory._
import org.make.core.history.HistoryActions._
import org.make.core.proposal.{ProposalId, QualificationKey}
import org.make.core.session.SessionId
import org.make.core.technical.IdGenerator
import org.make.core.user.UserId
import org.make.core.{DateHelper, MakeSerializable, RequestContext, SprayJsonFormatters}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.ExecutionContext.Implicits.global
import java.time.ZonedDateTime
import scala.util.{Failure, Success}

final case class SessionHistory(events: List[SessionHistoryEvent[_]]) extends MakeSerializable

object SessionHistory {
  implicit val persister: RootJsonFormat[SessionHistory] = DefaultJsonProtocol.jsonFormat1(SessionHistory.apply)
}

sealed trait State extends MakeSerializable with Logging {
  def lastEventDate: Option[ZonedDateTime]

  def handleCommand(
    userHistoryCoordinator: ActorRef[UserHistoryCommand],
    lockHandler: LockHandler,
    settings: MakeSettings,
    idGenerator: IdGenerator
  )(
    implicit context: ActorContext[SessionHistoryCommand],
    timeout: Timeout
  ): SessionHistoryCommand => Effect[SessionHistoryEvent[_], State]

  def handleEvent(settings: MakeSettings): SessionHistoryEvent[_] => State

  def retrieveOrExpireSession(
    command: GetCurrentSession,
    settings: MakeSettings,
    lastEventDate: Option[ZonedDateTime]
  ): ReplyEffect[SessionHistoryEvent[_], State] = {
    val isSessionExpired: Boolean =
      lastEventDate.exists(_.isBefore(DateHelper.now().minusNanos(settings.SessionCookie.lifetime.toNanos)))

    if (isSessionExpired) {
      Effect
        .persist(
          SessionExpired(
            sessionId = command.sessionId,
            requestContext = RequestContext.empty,
            action =
              SessionAction(date = DateHelper.now(), actionType = "sessionExpired", arguments = command.newSessionId)
          )
        )
        .thenReply(command.replyTo)(_ => Envelope(command.newSessionId))
    } else {
      Effect
        .persist[SessionHistoryEvent[_], State](
          SaveLastEventDate(
            command.sessionId,
            RequestContext.empty,
            action = SessionAction(
              date = DateHelper.now(),
              actionType = "saveLastEventDate",
              arguments = Some(DateHelper.now())
            )
          )
        )
        .thenReply(command.replyTo)(_ => Envelope(command.sessionId))
    }
  }

  def acquireLockForVotesIfPossible(
    command: LockProposalForVote,
    lockHandler: LockHandler
  ): ReplyEffect[SessionHistoryEvent[_], State] = {
    lockHandler.getOrExpire(command.proposalId) match {
      case None =>
        lockHandler.add(command.proposalId -> Vote)
        Effect.reply(command.replyTo)(LockAcquired)
      case Some(_) =>
        Effect.reply(command.replyTo)(
          LockAlreadyAcquired(
            s"Trying to vote on a locked proposal ${command.proposalId.value} for session ${command.sessionId.value}"
          )
        )
    }
  }

  def acquireLockForQualificationIfPossible(
    command: LockProposalForQualification,
    lockHandler: LockHandler
  ): ReplyEffect[SessionHistoryEvent[_], State] = {
    lockHandler.getOrExpire(command.proposalId) match {
      case None =>
        lockHandler.add(command.proposalId -> ChangeQualifications(Seq(command.key)))
        Effect.reply(command.replyTo)(LockAcquired)
      case Some(Vote) =>
        Effect.reply(command.replyTo)(
          LockAlreadyAcquired(
            s"Trying to qualify on a locked proposal ${command.proposalId.value} for session ${command.sessionId.value} (vote)"
          )
        )
      case Some(ChangeQualifications(keys)) if keys.contains(command.key) =>
        Effect.reply(command.replyTo)(
          LockAlreadyAcquired(
            s"Trying to qualify on a locked proposal ${command.proposalId.value} for session ${command.sessionId.value} (same qualification)"
          )
        )
      case Some(ChangeQualifications(keys)) =>
        lockHandler.add(command.proposalId -> ChangeQualifications(keys ++ Seq(command.key)))
        Effect.reply(command.replyTo)(LockAcquired)
    }
  }

  def releaseProposalLock(
    command: ReleaseProposalForVote,
    lockHandler: LockHandler
  ): ReplyEffect[SessionHistoryEvent[_], State] = {
    lockHandler.release(command.proposalId)
    Effect.reply(command.replyTo)(LockReleased)
  }

  def releaseLockForQualification(
    command: ReleaseProposalForQualification,
    lockHandler: LockHandler
  ): ReplyEffect[SessionHistoryEvent[_], State] = {
    lockHandler.getOrExpire(command.proposalId) match {
      case Some(ChangeQualifications(keys)) =>
        val remainingLocks = keys.filter(_ != command.key)
        if (remainingLocks.isEmpty) {
          lockHandler.release(command.proposalId)
        } else {
          lockHandler.add(command.proposalId -> ChangeQualifications(remainingLocks))
        }
      case _ =>
    }
    Effect.reply(command.replyTo)(LockReleased)
  }

  def onUserHistoryFailure[T](command: UserHistoryFailure[T]): ReplyEffect[SessionHistoryEvent[_], State] = {
    logger.warn(s"Error while requesting user history: ${command.exception}")
    Effect.reply(command.replyTo)(Envelope(command.response))
  }

  def stopSessionHistoryActor(
    command: StopSessionActor,
    lastEventDate: Option[ZonedDateTime]
  ): ReplyEffect[SessionHistoryEvent[_], State] = {
    Effect
      .persist[SessionHistoryEvent[_], State](
        SaveLastEventDate(
          command.id,
          RequestContext.empty,
          action = SessionAction(date = DateHelper.now(), actionType = "saveLastEventDate", arguments = lastEventDate)
        )
      )
      .thenStop()
      .thenReply(command.replyTo)(_ => Envelope(Ack))
  }
}

final case class Active(sessionHistory: SessionHistory, lastEventDate: Option[ZonedDateTime])
    extends State
    with Logging {
  override def handleCommand(
    userHistoryCoordinator: ActorRef[UserHistoryCommand],
    lockHandler: LockHandler,
    settings: MakeSettings,
    idGenerator: IdGenerator
  )(
    implicit context: ActorContext[SessionHistoryCommand],
    timeout: Timeout
  ): SessionHistoryCommand => Effect[SessionHistoryEvent[_], State] = {
    case command: GetCurrentSession                    => retrieveOrExpireSession(command, settings, lastEventDate)
    case EventEnvelope(_, event, replyTo)              => Effect.persist(event).thenReply(replyTo)(_ => Envelope(Ack))
    case command: SessionVoteValuesCommand             => onRetrieveVoteValues(sessionHistory, command)
    case command: SessionVotedProposalsPaginateCommand => onRetrieveVotedProposals(sessionHistory, command)
    case command: UserConnected                        => transformSession(sessionHistory, command, userHistoryCoordinator)
    case command: LockProposalForVote                  => acquireLockForVotesIfPossible(command, lockHandler)
    case command: LockProposalForQualification         => acquireLockForQualificationIfPossible(command, lockHandler)
    case command: ReleaseProposalForVote               => releaseProposalLock(command, lockHandler)
    case command: ReleaseProposalForQualification      => releaseLockForQualification(command, lockHandler)
    // Unhandled
    case _: SessionClosed         => Effect.unhandled
    case _: UserHistorySuccess[_] => Effect.unhandled
    case _: UserHistoryFailure[_] => Effect.unhandled
    // Test
    case GetState(_, replyTo)      => Effect.reply(replyTo)(Envelope(Active(sessionHistory, lastEventDate)))
    case command: StopSessionActor => stopSessionHistoryActor(command, lastEventDate)
  }

  override def handleEvent(settings: MakeSettings): SessionHistoryEvent[_] => State = {
    case event: SaveLastEventDate =>
      Active(
        sessionHistory.copy(events = (event :: sessionHistory.events).take(settings.maxUserHistoryEvents)),
        lastEventDate = event.action.arguments
      )
    case event: SessionTransforming =>
      Transforming(
        sessionHistory.copy(events = (event :: sessionHistory.events).take(settings.maxUserHistoryEvents)),
        event.requestContext,
        lastEventDate = Some(event.action.date)
      )
    case event: SessionExpired => Expired(event.action.arguments, lastEventDate)
    case event: SessionHistoryEvent[_] =>
      Active(
        sessionHistory.copy(events = (event :: sessionHistory.events).take(settings.maxUserHistoryEvents)),
        lastEventDate = Some(event.action.date)
      )
  }

  def onRetrieveVoteValues(
    state: SessionHistory,
    command: SessionVoteValuesCommand
  ): ReplyEffect[SessionHistoryEvent[_], State] = {
    val voteRelatedActions: Seq[VoteRelatedAction] = actions(state, command.proposalIds)
    val voteAndQualifications: Map[ProposalId, VoteAndQualifications] = voteByProposalId(voteRelatedActions).map {
      case (proposalId, voteAction) =>
        proposalId -> VoteAndQualifications(
          voteAction.key,
          qualifications(voteRelatedActions).getOrElse(proposalId, Map.empty),
          voteAction.date,
          voteAction.trust
        )
    }
    Effect.reply(command.replyTo)(Envelope(voteAndQualifications))
  }

  @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
  def voteByProposalId(actions: Seq[VoteRelatedAction]): Map[ProposalId, VoteAction] = {
    actions.collect {
      case voteAction: GenericVoteAction => voteAction
    }.groupBy(_.proposalId)
      .map {
        case (proposalId, voteActions) =>
          proposalId -> voteActions.maxBy(_.date.toString)
      }
      .collect {
        case (k, value: VoteAction) => k -> value
      }
  }

  def onRetrieveVotedProposals(
    state: SessionHistory,
    command: SessionVotedProposalsPaginateCommand
  ): ReplyEffect[SessionHistoryEvent[_], State] = {
    val votedProposals: Seq[ProposalId] = voteByProposalId(voteActions(state)).toSeq.sortBy {
      case (_, votesAndQualifications) => votesAndQualifications.date
    }.collect {
      case (proposalId, _) if command.proposalsIds.forall(_.contains(proposalId)) => proposalId
    }.slice(command.skip, command.limit)

    Effect.reply(command.replyTo)(Envelope(votedProposals))
  }

  def transformSession(
    state: SessionHistory,
    command: UserConnected,
    userHistoryCoordinator: ActorRef[UserHistoryCommand]
  )(
    implicit context: ActorContext[SessionHistoryCommand],
    timeout: Timeout
  ): ReplyEffect[SessionHistoryEvent[_], State] = {
    logger.info(
      s"Transforming session ${command.id} to user ${command.userId.value} with events ${state.events.map(_.toString).mkString(", ")}"
    )

    val events: Seq[LoggableHistoryEvent[_]] = state.events.collect {
      case event: LoggableHistoryEvent[_] => event
    }.sortBy(_.action.date.toString)

    context.pipeToSelf(
      userHistoryCoordinator ?? (InjectSessionEvents(
        command.userId,
        events.map(_.toUserHistoryEvent(command.userId)),
        _
      ))
    ) {
      case Success(_)         => SessionClosed(command.id, command.userId, command.replyTo)
      case Failure(exception) => UserHistoryFailure[LogResult](command.id, Failed, exception, command.replyTo)
    }

    Effect
      .persist(
        SessionTransforming(
          command.id,
          command.requestContext,
          SessionAction(DateHelper.now(), "transformingSession", command.userId)
        )
      )
      .thenNoReply()
  }

  private def actions(state: SessionHistory, proposalIds: Seq[ProposalId]): Seq[VoteRelatedAction] =
    state.events.flatMap {
      case LogSessionVoteEvent(_, _, SessionAction(date, _, SessionVote(proposalId, voteKey, trust)))
          if proposalIds.contains(proposalId) =>
        Some(VoteAction(proposalId, date, voteKey, trust))
      case LogSessionUnvoteEvent(_, _, SessionAction(date, _, SessionUnvote(proposalId, voteKey, trust)))
          if proposalIds.contains(proposalId) =>
        Some(UnvoteAction(proposalId, date, voteKey, trust))
      case LogSessionQualificationEvent(
          _,
          _,
          SessionAction(date, _, SessionQualification(proposalId, qualificationKey, trust))
          ) if proposalIds.contains(proposalId) =>
        Some(QualificationAction(proposalId, date, qualificationKey, trust))
      case LogSessionUnqualificationEvent(
          _,
          _,
          SessionAction(date, _, SessionUnqualification(proposalId, qualificationKey, trust))
          ) if proposalIds.contains(proposalId) =>
        Some(UnqualificationAction(proposalId, date, qualificationKey, trust))
      case _ => None
    }

  @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
  private def qualifications(actions: Seq[VoteRelatedAction]): Map[ProposalId, Map[QualificationKey, VoteTrust]] = {
    actions.collect {
      case qualificationAction: GenericQualificationAction => qualificationAction
    }.groupBy { qualificationAction =>
      qualificationAction.proposalId -> qualificationAction.key
    }.map {
      case (groupKey, qualificationAction) => groupKey -> qualificationAction.maxBy(_.date.toString)
    }.collect {
      case (k, v: QualificationAction) => k -> v
    }.toList.map {
      case ((proposalId, key), action) => proposalId -> (key -> action.trust)
    }.groupBy {
      case (proposalId, _) => proposalId
    }.map {
      case (proposalId, qualificationList) =>
        proposalId -> qualificationList.map {
          case (_, key) => key
        }.toMap
    }
  }

  private def voteActions(state: SessionHistory): Seq[VoteRelatedAction] = state.events.flatMap {
    case LogSessionVoteEvent(_, _, SessionAction(date, _, SessionVote(proposalId, voteKey, trust))) =>
      Some(VoteAction(proposalId, date, voteKey, trust))
    case LogSessionUnvoteEvent(_, _, SessionAction(date, _, SessionUnvote(proposalId, voteKey, trust))) =>
      Some(UnvoteAction(proposalId, date, voteKey, trust))
    case LogSessionQualificationEvent(
        _,
        _,
        SessionAction(date, _, SessionQualification(proposalId, qualificationKey, trust))
        ) =>
      Some(QualificationAction(proposalId, date, qualificationKey, trust))
    case LogSessionUnqualificationEvent(
        _,
        _,
        SessionAction(date, _, SessionUnqualification(proposalId, qualificationKey, trust))
        ) =>
      Some(UnqualificationAction(proposalId, date, qualificationKey, trust))
    case _ => None
  }
}

object Active extends SprayJsonFormatters {
  implicit val jsonFormat: RootJsonFormat[Active] = DefaultJsonProtocol.jsonFormat2(Active.apply)
}

final case class Transforming(
  sessionHistory: SessionHistory,
  requestContext: RequestContext,
  lastEventDate: Option[ZonedDateTime]
) extends State {
  override def handleCommand(
    userHistoryCoordinator: ActorRef[UserHistoryCommand],
    lockHandler: LockHandler,
    settings: MakeSettings,
    idGenerator: IdGenerator
  )(
    implicit context: ActorContext[SessionHistoryCommand],
    timeout: Timeout
  ): SessionHistoryCommand => Effect[SessionHistoryEvent[_], State] = {
    case _: EventEnvelope[_]                      => Effect.stash()
    case command: LockProposalForVote             => acquireLockForVotesIfPossible(command, lockHandler)
    case command: LockProposalForQualification    => acquireLockForQualificationIfPossible(command, lockHandler)
    case command: ReleaseProposalForVote          => releaseProposalLock(command, lockHandler)
    case command: ReleaseProposalForQualification => releaseLockForQualification(command, lockHandler)
    // Internal
    case command: SessionClosed         => onSessionClosed(command, requestContext, idGenerator)
    case command: UserHistoryFailure[_] => onUserHistoryFailure(command)
    // Unhandled
    case _: GetCurrentSession                    => Effect.unhandled
    case _: SessionVoteValuesCommand             => Effect.unhandled
    case _: SessionVotedProposalsPaginateCommand => Effect.unhandled
    case _: UserConnected                        => Effect.unhandled
    case _: UserHistorySuccess[_]                => Effect.unhandled
    // Test
    case GetState(_, replyTo) =>
      Effect.reply(replyTo)(Envelope(Transforming(sessionHistory, requestContext, lastEventDate)))
    case command: StopSessionActor => stopSessionHistoryActor(command, lastEventDate)
  }

  override def handleEvent(settings: MakeSettings): SessionHistoryEvent[_] => State = {
    case transformed: SessionTransformed =>
      Closed(transformed.action.arguments, lastEventDate = Some(transformed.action.date))
    case event: SaveLastEventDate =>
      Transforming(
        sessionHistory.copy(events = (event :: sessionHistory.events).take(settings.maxUserHistoryEvents)),
        requestContext,
        lastEventDate = event.action.arguments
      )
    case event: SessionHistoryEvent[_] =>
      Transforming(
        sessionHistory.copy(events = (event :: sessionHistory.events).take(settings.maxUserHistoryEvents)),
        requestContext,
        lastEventDate = Some(event.action.date)
      )
  }

  def onSessionClosed(command: SessionClosed, requestContext: RequestContext, idGenerator: IdGenerator)(
    implicit context: ActorContext[SessionHistoryCommand]
  ): ReplyEffect[SessionHistoryEvent[_], State] = {
    Effect
      .persist(
        SessionTransformed(
          sessionId = command.id,
          requestContext = RequestContext.empty,
          action = SessionAction(date = DateHelper.now(), actionType = "transformSession", arguments = command.userId)
        )
      )
      .thenRun { _: State =>
        context.system.eventStream ! EventStream.Publish(
          UserConnectedEvent(
            connectedUserId = Some(command.userId),
            eventDate = DateHelper.now(),
            userId = command.userId,
            requestContext = requestContext,
            eventId = Some(idGenerator.nextEventId())
          )
        )
      }
      .thenReply(command.replyTo)(_ => Envelope(Ack))
      .thenUnstashAll()
  }
}

object Transforming extends SprayJsonFormatters {
  implicit val jsonFormat: RootJsonFormat[Transforming] = DefaultJsonProtocol.jsonFormat3(Transforming.apply)
}

final case class Closed(userId: UserId, lastEventDate: Option[ZonedDateTime]) extends State with Logging {
  override def handleCommand(
    userHistoryCoordinator: ActorRef[UserHistoryCommand],
    lockHandler: LockHandler,
    settings: MakeSettings,
    idGenerator: IdGenerator
  )(
    implicit context: ActorContext[SessionHistoryCommand],
    timeout: Timeout
  ): SessionHistoryCommand => Effect[SessionHistoryEvent[_], State] = {
    case command: GetCurrentSession => retrieveOrExpireSession(command, settings, lastEventDate)
    case command @ EventEnvelope(_, _: LoggableHistoryEvent[_], _) =>
      transferEventWhilstClosed(command, userId, userHistoryCoordinator)
    case command: SessionVoteValuesCommand => onRetrieveUserVoteValues(command, userId, userHistoryCoordinator)
    case command: SessionVotedProposalsPaginateCommand =>
      onRetrieveUserVotedProposals(command, userId, userHistoryCoordinator)
    case command: UserConnected                   => transformClosedSession(command, userId)
    case command: LockProposalForVote             => acquireLockForVotesIfPossible(command, lockHandler)
    case command: LockProposalForQualification    => acquireLockForQualificationIfPossible(command, lockHandler)
    case command: ReleaseProposalForVote          => releaseProposalLock(command, lockHandler)
    case command: ReleaseProposalForQualification => releaseLockForQualification(command, lockHandler)
    // Internal
    case UserHistorySuccess(_, response, replyTo) => Effect.reply(replyTo)(Envelope(response))
    case command: UserHistoryFailure[_]           => onUserHistoryFailure(command)
    // Unhandled
    case _: SessionClosed => Effect.unhandled
    // Test
    case GetState(_, replyTo)      => Effect.reply(replyTo)(Envelope(Closed(userId, lastEventDate)))
    case command: StopSessionActor => stopSessionHistoryActor(command, lastEventDate)
  }

  override def handleEvent(settings: MakeSettings): SessionHistoryEvent[_] => State = {
    case transformed: SessionTransformed =>
      Closed(transformed.action.arguments, lastEventDate = Some(transformed.action.date))
    case event: SaveLastEventDate =>
      Closed(userId, lastEventDate = event.action.arguments)
    case event: SessionExpired =>
      Expired(event.action.arguments, lastEventDate)
    case event: SessionHistoryEvent[_] =>
      Closed(userId, lastEventDate = Some(event.action.date))
  }

  def onRetrieveUserVoteValues(
    command: SessionVoteValuesCommand,
    userId: UserId,
    userHistoryCoordinator: ActorRef[UserHistoryCommand]
  )(
    implicit context: ActorContext[SessionHistoryCommand],
    timeout: Timeout
  ): ReplyEffect[SessionHistoryEvent[_], State] = {
    context.pipeToSelf(userHistoryCoordinator ?? (RequestVoteValues(userId, command.proposalIds, _))) {
      case Success(value) =>
        UserHistorySuccess[Map[ProposalId, VoteAndQualifications]](command.id, value.value, command.replyTo)
      case Failure(exception) =>
        UserHistoryFailure[Map[ProposalId, VoteAndQualifications]](command.id, Map.empty, exception, command.replyTo)
    }
    Effect.noReply
  }

  def onRetrieveUserVotedProposals(
    command: SessionVotedProposalsPaginateCommand,
    userId: UserId,
    userHistoryCoordinator: ActorRef[UserHistoryCommand]
  )(
    implicit context: ActorContext[SessionHistoryCommand],
    timeout: Timeout
  ): ReplyEffect[SessionHistoryEvent[_], State] = {
    context.pipeToSelf(
      userHistoryCoordinator ?? (RequestUserVotedProposalsPaginate(
        userId = userId,
        filterVotes = None,
        filterQualifications = None,
        proposalsIds = command.proposalsIds,
        limit = command.limit,
        skip = command.skip,
        _
      ))
    ) {
      case Success(value) => UserHistorySuccess[Seq[ProposalId]](command.id, value.value, command.replyTo)
      case Failure(exception) =>
        UserHistoryFailure[Seq[ProposalId]](command.id, Seq.empty, exception, command.replyTo)
    }
    Effect.noReply
  }

  def transformClosedSession(command: UserConnected, userId: UserId): ReplyEffect[SessionHistoryEvent[_], State] = {
    if (command.userId != userId) {
      logger.warn(s"Session ${command.id} has moved from user ${userId.value} to user ${command.userId.value}")
      Effect.persist(
        SessionTransformed(
          command.id,
          command.requestContext,
          SessionAction(DateHelper.now(), "sessionTransformed", command.userId)
        )
      )
    }
    Effect.reply(command.replyTo)(Envelope(Ack))
  }

  def transferEventWhilstClosed(
    command: EventEnvelope[LoggableHistoryEvent[_]],
    userId: UserId,
    userHistoryCoordinator: ActorRef[UserHistoryCommand]
  )(
    implicit context: ActorContext[SessionHistoryCommand],
    timeout: Timeout
  ): ReplyEffect[SessionHistoryEvent[_], State] = {
    context.pipeToSelf(userHistoryCoordinator ?? { replyTo: ActorRef[UserHistoryResponse[Ack.type]] =>
      UserHistoryTransactionalEnvelope(userId, command.event.toUserHistoryEvent(userId), replyTo)
    }) {
      case Success(UserHistoryResponse(log)) => UserHistorySuccess[LogResult](command.id, log, command.replyTo)
      case Failure(exception)                => UserHistoryFailure[LogResult](command.id, Failed, exception, command.replyTo)
    }
    Effect.noReply
  }
}

object Closed extends SprayJsonFormatters {
  implicit val jsonFormat: RootJsonFormat[Closed] = DefaultJsonProtocol.jsonFormat2(Closed.apply)
}

final case class Expired(newSessionId: SessionId, lastEventDate: Option[ZonedDateTime]) extends State {
  override def handleCommand(
    userHistoryCoordinator: ActorRef[UserHistoryCommand],
    lockHandler: LockHandler,
    settings: MakeSettings,
    idGenerator: IdGenerator
  )(
    implicit context: ActorContext[SessionHistoryCommand],
    timeout: Timeout
  ): SessionHistoryCommand => Effect[SessionHistoryEvent[_], State] = {
    case command: GetCurrentSession           => retrieveOrExpireSession(command, settings, lastEventDate)
    case command: SessionExpiredCommand[_, _] => Effect.reply(command.replyTo)(ExpiredSession(newSessionId))
    // Unhandled
    case _: SessionClosed         => Effect.unhandled
    case _: UserHistorySuccess[_] => Effect.unhandled
    case _: UserHistoryFailure[_] => Effect.unhandled
    // Test
    case GetState(_, replyTo)      => Effect.reply(replyTo)(Envelope(Expired(newSessionId, lastEventDate)))
    case command: StopSessionActor => stopSessionHistoryActor(command, lastEventDate)
  }

  override def handleEvent(settings: MakeSettings): SessionHistoryEvent[_] => State = { _: SessionHistoryEvent[_] =>
    Expired(newSessionId, lastEventDate)
  }
}

object Expired extends SprayJsonFormatters {
  implicit val jsonFormat: RootJsonFormat[Expired] = DefaultJsonProtocol.jsonFormat2(Expired.apply)
}
