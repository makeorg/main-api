package org.make.api.vote

import java.time.ZonedDateTime

import akka.actor.{ActorLogging, Props}
import akka.pattern.{ask, Patterns}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import org.make.api.vote.VoteActor.Snapshot
import org.make.core.user.UserId
import org.make.core.proposition.PropositionId
import org.make.core.vote.VoteEvent._
import org.make.core.vote.VoteStatus._
import org.make.core.vote._

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.duration._

class VoteActor extends PersistentActor with ActorLogging {

  private[this] var state: Option[List[VoteState]] = None

  override def receiveRecover: Receive = {
    case e: VoteEvent =>
      log.info(s"Recovering event $e")
      applyEvent(e)
    case SnapshotOffer(_, snapshot) =>
      log.info(s"Recovering from snapshot $snapshot")
      state = Some(snapshot.asInstanceOf[List[VoteState]])
    case _: RecoveryCompleted =>
  }

  override def receiveCommand: Receive = {
    case GetVote(voteId) =>
      sender ! state.map(_.filter(_.voteId == voteId))
    case e: ViewVoteCommand =>
      persistAndPublishEvent(VoteViewed(id = e.voteId, propositionId = e.propositionId))
    case event: PutVoteCommand =>
      event.status match {
        case VoteStatus.AGREE    => vote(event, VotedAgree.apply)
        case VoteStatus.DISAGREE => vote(event, VotedDisagree.apply)
        case VoteStatus.UNSURE   => vote(event, VotedUnsure.apply)
      }
    case Snapshot =>
      saveSnapshot(state.get.map(_.toVote))
  }

  def vote(vote: PutVoteCommand,
           createEvent: (VoteId, PropositionId, UserId, ZonedDateTime, VoteStatus) => VoteEvent): Unit = {
    if (userCanVote(vote.userId)) {
      persistAndPublishEvent(createEvent(vote.voteId, vote.propositionId, vote.userId, vote.createdAt, vote.status))
    }
    Patterns
      .pipe((self ? GetVote(vote.voteId))(1.second), Implicits.global)
      .to(sender)

    self ! Snapshot
  }

  override def persistenceId: String = "Vote-" + self.path.name

  private val applyEvent: PartialFunction[VoteEvent, Unit] = {
    case e: VotedAgree =>
      state = Option(
        state.getOrElse(Nil) :+ VoteState(
          voteId = e.id,
          userId = Option(e.userId),
          propositionId = Option(e.propositionId),
          createdAt = Option(e.createdAt),
          voteStatus = Option(VoteStatus.AGREE)
        )
      )
    case e: VotedDisagree =>
      state = Option(
        state.getOrElse(Nil) :+ VoteState(
          voteId = e.id,
          userId = Option(e.userId),
          propositionId = Option(e.propositionId),
          createdAt = Option(e.createdAt),
          voteStatus = Option(VoteStatus.DISAGREE)
        )
      )
    case e: VotedUnsure =>
      state = Option(
        state.getOrElse(Nil) :+ VoteState(
          voteId = e.id,
          userId = Option(e.userId),
          propositionId = Option(e.propositionId),
          createdAt = Option(e.createdAt),
          voteStatus = Option(VoteStatus.UNSURE)
        )
      )
    case _ =>
  }

  private def persistAndPublishEvent(event: VoteEvent): Unit = {
    persist(event)(applyEvent)
    context.system.eventStream.publish(event)
  }

  private def userCanVote(userId: UserId): Boolean = {
    state.flatMap(_.find(_.userId.contains(userId))).isEmpty
  }

  case class VoteState(voteId: VoteId,
                       userId: Option[UserId],
                       propositionId: Option[PropositionId],
                       createdAt: Option[ZonedDateTime],
                       voteStatus: Option[VoteStatus]) {
    def toVote: Vote = {
      Vote(voteId, userId.orNull, propositionId.orNull, createdAt.orNull, voteStatus.orNull)
    }
  }
}

object VoteActor {
  val props: Props = Props[VoteActor]

  case object Snapshot
}
