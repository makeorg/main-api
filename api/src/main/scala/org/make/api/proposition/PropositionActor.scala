package org.make.api.proposition

import java.time.ZonedDateTime

import akka.actor.{ActorLogging, PoisonPill, Props}
import akka.pattern.{ask, Patterns}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import org.make.api.proposition.PropositionActor.Snapshot
import org.make.core.user.UserId
import org.make.core.proposition.PropositionEvent._
import org.make.core.proposition._

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.duration._

class PropositionActor extends PersistentActor with ActorLogging {
  def propositionId: PropositionId = PropositionId(self.path.name)

  private[this] var state: Option[PropositionState] = None

  override def receiveRecover: Receive = {
    case e: PropositionEvent =>
      log.info(s"Recovering event $e")
      applyEvent(e)
    case SnapshotOffer(_, snapshot: Proposition) =>
      log.info(s"Recovering from snapshot $snapshot")
      state = Some(
        PropositionState(
          propositionId = snapshot.propositionId,
          userId = Option(snapshot.userId),
          createdAt = snapshot.createdAt,
          updatedAt = snapshot.updatedAt,
          content = Option(snapshot.content)
        )
      )
    case _: RecoveryCompleted =>
  }

  override def receiveCommand: Receive = {
    case GetProposition(_) => sender ! state.map(_.toProposition)
    case v: ViewPropositionCommand =>
      persistAndPublishEvent(PropositionViewed(id = v.propositionId))
      Patterns
        .pipe((self ? GetProposition(v.propositionId))(1.second), Implicits.global)
        .to(sender)
    case propose: ProposeCommand =>
      persistAndPublishEvent(
        PropositionProposed(
          id = propose.propositionId,
          userId = propose.userId,
          createdAt = propose.createdAt,
          content = propose.content
        )
      )
      Patterns
        .pipe((self ? GetProposition(propose.propositionId))(1.second), Implicits.global)
        .to(sender)
      self ! Snapshot
    case update: UpdatePropositionCommand =>
      persistAndPublishEvent(
        PropositionUpdated(id = update.propositionId, updatedAt = update.updatedAt, content = update.content)
      )
      Patterns
        .pipe((self ? GetProposition(update.propositionId))(1.second), Implicits.global)
        .to(sender)
      self ! Snapshot
    case Snapshot                => state.foreach(state => saveSnapshot(state.toProposition))
    case KillPropositionShard(_) => self ! PoisonPill
  }

  override def persistenceId: String = propositionId.value

  private val applyEvent: PartialFunction[PropositionEvent, Unit] = {
    case e: PropositionProposed =>
      state = Some(
        PropositionState(
          propositionId = e.id,
          userId = Option(e.userId),
          createdAt = Option(e.createdAt),
          updatedAt = Option(e.createdAt),
          content = Option(e.content)
        )
      )
    case e: PropositionUpdated =>
      state.foreach(p => {
        p.content = Option(e.content)
        p.updatedAt = Option(e.updatedAt)
      })
    case _ =>
  }

  private def persistAndPublishEvent(event: PropositionEvent): Unit = {
    persist(event) { (e: PropositionEvent) =>
      applyEvent(e)
      context.system.eventStream.publish(e)
    }
  }

  case class PropositionState(propositionId: PropositionId,
                              userId: Option[UserId],
                              createdAt: Option[ZonedDateTime],
                              var updatedAt: Option[ZonedDateTime],
                              var content: Option[String]) {
    def toProposition: Proposition = {
      Proposition(
        this.propositionId,
        this.userId.orNull,
        this.content.orNull,
        this.createdAt,
        this.updatedAt
      )
    }
  }
}

object PropositionActor {
  val props: Props = Props[PropositionActor]

  case object Snapshot
}
