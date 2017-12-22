package org.make.api.userhistory

import akka.actor.ActorLogging
import org.make.api.technical.MakePersistentActor
import org.make.api.technical.MakePersistentActor.Snapshot
import org.make.api.userhistory.UserHistoryActor._
import org.make.core.MakeSerializable
import org.make.core.history.HistoryActions._
import org.make.core.proposal.{ProposalId, QualificationKey}
import org.make.core.user._
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

class UserHistoryActor
    extends MakePersistentActor(classOf[UserHistory], classOf[UserHistoryEvent[_]], autoSnapshot = false)
    with ActorLogging {

  def userId: UserId = UserId(self.path.name)

  override def receiveCommand: Receive = {
    case GetUserHistory(_)                    => sender() ! state.getOrElse(UserHistory(Nil))
    case command: LogUserSearchProposalsEvent => persistEvent(command)
    case command: LogAcceptProposalEvent      => persistEvent(command)
    case command: LogRefuseProposalEvent      => persistEvent(command)
    case command: LogPostponeProposalEvent    => persistEvent(command)
    case command: LogLockProposalEvent =>
      if (!state.toSeq
            .flatMap(_.events)
            .exists {
              case LogLockProposalEvent(command.userId, _, _, _) => true
              case _                                             => false
            }) {
        persistEvent(command)
      }
    case command: LogRegisterCitizenEvent             => persistEvent(command)
    case command: LogUserProposalEvent                => persistEvent(command)
    case command: LogUserVoteEvent                    => persistEvent(command)
    case command: LogUserUnvoteEvent                  => persistEvent(command)
    case command: LogUserQualificationEvent           => persistEvent(command)
    case command: LogUserUnqualificationEvent         => persistEvent(command)
    case RequestVoteValues(_, values)                 => retrieveVoteValues(values)
    case RequestUserVotedProposals(_)                 => retrieveUserVotedProposals()
    case command: LogUserCreateSequenceEvent          => persistEvent(command)
    case command: LogUserUpdateSequenceEvent          => persistEvent(command)
    case command: LogUserAddProposalsSequenceEvent    => persistEvent(command)
    case command: LogUserRemoveProposalsSequenceEvent => persistEvent(command)
    case command: LogGetProposalDuplicatesEvent       => persistEvent(command)
    case command: LogUserSearchSequencesEvent         => persistEvent(command)
    case command: LogUserStartSequenceEvent           => persistEvent(command)
    case Snapshot                                     => saveSnapshot()
  }

  override def persistenceId: String = userId.value

  private def persistEvent(event: UserHistoryEvent[_]): Unit = {
    if (state.isEmpty) {
      state = Some(UserHistory(Nil))
    }
    persist(event) { e: UserHistoryEvent[_] =>
      log.debug("Persisted event {} for user {}", e.toString, persistenceId)
      newEventAdded(e)
      sender() ! event
    }
  }

  override val applyEvent: PartialFunction[UserHistoryEvent[_], Option[UserHistory]] = {
    case event => state.map(s => s.copy(events = event :: s.events))
  }

  private def actions(proposalIds: Seq[ProposalId]): Seq[VoteRelatedAction] = {
    state.toSeq.flatMap(_.events).flatMap {
      case LogUserVoteEvent(_, _, UserAction(date, _, UserVote(proposalId, voteKey)))
          if proposalIds.contains(proposalId) =>
        Some(VoteAction(proposalId, date, voteKey))
      case LogUserUnvoteEvent(_, _, UserAction(date, _, UserUnvote(proposalId, voteKey)))
          if proposalIds.contains(proposalId) =>
        Some(UnvoteAction(proposalId, date, voteKey))
      case LogUserQualificationEvent(_, _, UserAction(date, _, UserQualification(proposalId, qualificationKey)))
          if proposalIds.contains(proposalId) =>
        Some(QualificationAction(proposalId, date, qualificationKey))
      case LogUserUnqualificationEvent(_, _, UserAction(date, _, UserUnqualification(proposalId, qualificationKey)))
          if proposalIds.contains(proposalId) =>
        Some(UnqualificationAction(proposalId, date, qualificationKey))
      case _ => None
    }
  }

  private def voteActions(): Seq[VoteRelatedAction] = {
    state.toSeq.flatMap(_.events).flatMap {
      case LogUserVoteEvent(_, _, UserAction(date, _, UserVote(proposalId, voteKey))) =>
        Some(VoteAction(proposalId, date, voteKey))
      case LogUserUnvoteEvent(_, _, UserAction(date, _, UserUnvote(proposalId, voteKey))) =>
        Some(UnvoteAction(proposalId, date, voteKey))
      case LogUserQualificationEvent(_, _, UserAction(date, _, UserQualification(proposalId, qualificationKey))) =>
        Some(QualificationAction(proposalId, date, qualificationKey))
      case LogUserUnqualificationEvent(_, _, UserAction(date, _, UserUnqualification(proposalId, qualificationKey))) =>
        Some(UnqualificationAction(proposalId, date, qualificationKey))
      case _ => None
    }
  }

  private def voteByProposalId(actions: Seq[VoteRelatedAction]) = {
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
  }

  private def qualifications(actions: Seq[VoteRelatedAction]): Map[ProposalId, Seq[QualificationKey]] = {
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

  private def retrieveUserVotedProposals(): Unit = {
    sender ! voteByProposalId(voteActions()).keys.toSeq
  }
}

object UserHistoryActor {
  final case class UserHistory(events: List[UserHistoryEvent[_]]) extends MakeSerializable

  object UserHistory {
    implicit val formatter: RootJsonFormat[UserHistory] = DefaultJsonProtocol.jsonFormat1(UserHistory.apply)
  }

  final case class RequestVoteValues(userId: UserId, proposalIds: Seq[ProposalId]) extends UserRelatedEvent
  final case class RequestUserVotedProposals(userId: UserId) extends UserRelatedEvent

}
