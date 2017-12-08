package org.make.api.sessionhistory

import akka.actor.{ActorLogging, ActorRef}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout
import org.make.api.sessionhistory.SessionHistoryActor.SessionHistory
import org.make.core.history.HistoryActions._
import org.make.core.proposal.{ProposalId, QualificationKey}
import org.make.core.session._
import org.make.core.user.UserId
import org.make.core.{DateHelper, MakeSerializable, RequestContext}

import scala.concurrent.duration.DurationInt
import akka.pattern.ask
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import spray.json.DefaultJsonProtocol._

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

class SessionHistoryActor(userHistoryCoordinator: ActorRef) extends PersistentActor with ActorLogging {

  implicit val timeout: Timeout = Timeout(3.seconds)

  def sessionId: SessionId = SessionId(self.path.name)

  private var state: SessionHistory = SessionHistory(Nil)

  override def receiveRecover: Receive = {
    case event: SessionHistoryEvent[_]              => state = applyEvent(event)
    case SnapshotOffer(_, snapshot: SessionHistory) => state = snapshot
    case _: RecoveryCompleted                       =>
  }

  override def receiveCommand: Receive = {
    case GetSessionHistory(_)                     => sender() ! state
    case command: LogSessionVoteEvent             => persistEvent(command)()
    case command: LogSessionUnvoteEvent           => persistEvent(command)()
    case command: LogSessionQualificationEvent    => persistEvent(command)()
    case command: LogSessionUnqualificationEvent  => persistEvent(command)()
    case command: LogSessionSearchProposalsEvent  => persistEvent(command)()
    case RequestSessionVoteValues(_, proposalIds) => retrieveVoteValues(proposalIds)
    case RequestSessionVotedProposals(_)          => retrieveVotedProposals()
    case UserConnected(_, userId)                 => transformSession(userId)
    case UserCreated(_, userId)                   => transformSession(userId)
  }

  private def transformSession(userId: UserId): Unit = {
    log.debug(
      "Transforming session {} to user {} with events {}",
      persistenceId,
      userId.value,
      state.events.map(_.toString).mkString(", ")
    )
    persistEvent(
      SessionTransformed(
        sessionId = sessionId,
        requestContext = RequestContext.empty,
        action = SessionAction(date = DateHelper.now(), actionType = "transformSession", arguments = userId)
      )
    ) { event =>
      val events = state.events
      val originalSender = sender()
      Future
        .traverse(events) {
          case event: LogSessionVoteEvent =>
            userHistoryCoordinator ? event.toUserHistoryEvent(userId)
          case event: LogSessionUnvoteEvent =>
            userHistoryCoordinator ? event.toUserHistoryEvent(userId)
          case event: LogSessionQualificationEvent =>
            userHistoryCoordinator ? event.toUserHistoryEvent(userId)
          case event: LogSessionUnqualificationEvent =>
            userHistoryCoordinator ? event.toUserHistoryEvent(userId)
          case event: LogSessionSearchProposalsEvent =>
            userHistoryCoordinator ? event.toUserHistoryEvent(userId)
          case other =>
            Future.successful {}
        }
        .onComplete {
          case Success(_) => originalSender ! event
          case Failure(e) => log.error(e, "error while transforming session")
        }
      state = state.copy(events = Nil)
      // We need a snapshot here since we don't want to be able to replay a session transformation event
      saveSnapshot(state)
    }

  }

  private def retrieveVotedProposals(): Unit = {
    sender() ! voteByProposalId(voteActions()).keys.toSeq
  }

  private def retrieveVoteValues(proposalIds: Seq[ProposalId]): Unit = {
    val voteRelatedActions: Seq[VoteRelatedAction] = actions(proposalIds)

    val voteAndQualifications: Map[ProposalId, VoteAndQualifications] = voteByProposalId(voteRelatedActions).map {
      case (proposalId, voteKey) =>
        proposalId -> VoteAndQualifications(
          voteKey,
          qualifications(voteRelatedActions).getOrElse(proposalId, Seq.empty).sortBy(_.shortName)
        )
    }
    sender() ! voteAndQualifications
  }

  private def actions(proposalIds: Seq[ProposalId]): Seq[VoteRelatedAction] = state.events.flatMap {
    case LogSessionVoteEvent(_, _, SessionAction(date, _, SessionVote(proposalId, voteKey)))
        if proposalIds.contains(proposalId) =>
      Some(VoteAction(proposalId, date, voteKey))
    case LogSessionUnvoteEvent(_, _, SessionAction(date, _, SessionUnvote(proposalId, voteKey)))
        if proposalIds.contains(proposalId) =>
      Some(UnvoteAction(proposalId, date, voteKey))
    case LogSessionQualificationEvent(_, _, SessionAction(date, _, SessionQualification(proposalId, qualificationKey)))
        if proposalIds.contains(proposalId) =>
      Some(QualificationAction(proposalId, date, qualificationKey))
    case LogSessionUnqualificationEvent(
        _,
        _,
        SessionAction(date, _, SessionUnqualification(proposalId, qualificationKey))
        ) if proposalIds.contains(proposalId) =>
      Some(UnqualificationAction(proposalId, date, qualificationKey))
    case _ => None
  }

  private def voteActions(): Seq[VoteRelatedAction] = state.events.flatMap {
    case LogSessionVoteEvent(_, _, SessionAction(date, _, SessionVote(proposalId, voteKey))) =>
      Some(VoteAction(proposalId, date, voteKey))
    case LogSessionUnvoteEvent(_, _, SessionAction(date, _, SessionUnvote(proposalId, voteKey))) =>
      Some(UnvoteAction(proposalId, date, voteKey))
    case LogSessionQualificationEvent(
        _,
        _,
        SessionAction(date, _, SessionQualification(proposalId, qualificationKey))
        ) =>
      Some(QualificationAction(proposalId, date, qualificationKey))
    case LogSessionUnqualificationEvent(
        _,
        _,
        SessionAction(date, _, SessionUnqualification(proposalId, qualificationKey))
        ) =>
      Some(UnqualificationAction(proposalId, date, qualificationKey))
    case _ => None
  }

  private def voteByProposalId(actions: Seq[VoteRelatedAction]) =
    actions.filter {
      case _: GenericVoteAction => true
      case _                    => false
    }.groupBy(_.proposalId)
      .map {
        case (proposalId, voteActions) => proposalId -> voteActions.maxBy(_.date.toString)
      }
      .filter {
        case (_, _: VoteAction) => true
        case _                  => false
      }
      .map {
        case (proposalId, action) => proposalId -> action.asInstanceOf[VoteAction].key
      }

  private def qualifications(actions: Seq[VoteRelatedAction]): Map[ProposalId, Seq[QualificationKey]] =
    actions.filter {
      case _: GenericQualificationAction => true
      case _                             => false
    }.groupBy(action => (action.proposalId, action.asInstanceOf[GenericQualificationAction].key))
      .map {
        case (groupKey, qualificationAction) => groupKey -> qualificationAction.maxBy(_.date.toString)
      }
      .filter {
        case (_, _: QualificationAction) => true
        case _                           => false
      }
      .toList
      .map {
        case ((proposalId, key), _) => proposalId -> key
      }
      .groupBy {
        case (proposalId, _) => proposalId
      }
      .map {
        case (proposalId, qualificationList) =>
          proposalId -> qualificationList.map {
            case (_, key) => key
          }
      }

  override def persistenceId: String = sessionId.value

  private def persistEvent(event: SessionHistoryEvent[_])(andThen: SessionHistoryEvent[_] => Unit = { e =>
    sender() ! e
  }): Unit = {
    persist(event) { e: SessionHistoryEvent[_] =>
      state = applyEvent(e)
      andThen(e)
    }
  }

  private def applyEvent(event: SessionHistoryEvent[_]) = {
    state.copy(events = event :: state.events)
  }
}

object SessionHistoryActor {
  case class SessionHistory(events: List[SessionHistoryEvent[_]]) extends MakeSerializable

  object SessionHistory {
    implicit val persister: RootJsonFormat[SessionHistory] = DefaultJsonProtocol.jsonFormat1(SessionHistory.apply)
  }

}
