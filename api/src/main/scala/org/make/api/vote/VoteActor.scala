package org.make.api.vote

import java.time.ZonedDateTime

import akka.actor.{ActorLogging, Props}
import akka.pattern.{Patterns, ask}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import org.make.api.vote.VoteActor.Snapshot
import org.make.core.citizen.CitizenId
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
    case GetVote(voteId) => sender ! state.map(_.filter(_.voteId == voteId))
    case e: ViewVoteCommand =>
      persistAndPublishEvent(
        VoteViewed(id = e.voteId, propositionId = e.propositionId)
      )
    case agree: PutVoteCommand if agree.status == VoteStatus.AGREE =>
      if (citizenCanVote(agree.citizenId))
        persistAndPublishEvent(
          VotedAgree(
            id = agree.voteId,
            propositionId = agree.propositionId,
            citizenId = agree.citizenId,
            createdAt = agree.createdAt,
            status = agree.status
          )
        )
      Patterns
        .pipe((self ? GetVote(agree.voteId))(1.second), Implicits.global)
        .to(sender)
      self ! Snapshot
    case disagree: PutVoteCommand if disagree.status == VoteStatus.DISAGREE =>
      if (citizenCanVote(disagree.citizenId))
        persistAndPublishEvent(
          VotedDisagree(
            id = disagree.voteId,
            propositionId = disagree.propositionId,
            citizenId = disagree.citizenId,
            createdAt = disagree.createdAt,
            status = disagree.status
          )
        )
      Patterns
        .pipe((self ? GetVote(disagree.voteId))(1.second), Implicits.global)
        .to(sender)
      self ! Snapshot
    case unsure: PutVoteCommand if unsure.status == VoteStatus.UNSURE =>
      if (citizenCanVote(unsure.citizenId))
        persistAndPublishEvent(
          VotedUnsure(
            id = unsure.voteId,
            propositionId = unsure.propositionId,
            citizenId = unsure.citizenId,
            createdAt = unsure.createdAt,
            status = unsure.status
          )
        )
      Patterns
        .pipe((self ? GetVote(unsure.voteId))(1.second), Implicits.global)
        .to(sender)
      self ! Snapshot
    case Snapshot => saveSnapshot(state.get.map(_.toVote))
  }

  override def persistenceId: String = "Vote-" + self.path.name

  private val applyEvent: PartialFunction[VoteEvent, Unit] = {
    case e: VotedAgree =>
      state = Option(
        state.getOrElse(Nil) :+ VoteState(
          voteId = e.id,
          citizenId = Option(e.citizenId),
          propositionId = Option(e.propositionId),
          createdAt = Option(e.createdAt),
          voteStatus = Option(VoteStatus.AGREE)
        )
      )
    case e: VotedDisagree =>
      state = Option(
        state.getOrElse(Nil) :+ VoteState(
          voteId = e.id,
          citizenId = Option(e.citizenId),
          propositionId = Option(e.propositionId),
          createdAt = Option(e.createdAt),
          voteStatus = Option(VoteStatus.DISAGREE)
        )
      )
    case e: VotedUnsure =>
      state = Option(
        state.getOrElse(Nil) :+ VoteState(
          voteId = e.id,
          citizenId = Option(e.citizenId),
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

  private def citizenCanVote(citizenId: CitizenId): Boolean = {
    state.map(_.filter(_.citizenId.contains(citizenId))).isEmpty
  }

  case class VoteState(voteId: VoteId,
                       citizenId: Option[CitizenId],
                       propositionId: Option[PropositionId],
                       createdAt: Option[ZonedDateTime],
                       voteStatus: Option[VoteStatus]) {
    def toVote: Vote = {
      Vote(
        voteId,
        citizenId.orNull,
        propositionId.orNull,
        createdAt.orNull,
        voteStatus.orNull
      )
    }
  }
}

object VoteActor {
  val props: Props = Props[VoteActor]

  case object Snapshot
}
