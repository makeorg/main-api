package org.make.api.proposition

import java.time.ZonedDateTime

import akka.actor.Props
import akka.pattern.{Patterns, ask}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import org.make.api.proposition.PropositionActor.Snapshot
import org.make.core.citizen.CitizenId
import org.make.core.proposition.PropositionEvent._
import org.make.core.proposition._

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.duration._

class PropositionActor extends PersistentActor {
  def propositionId = PropositionId(self.path.name)

  private[this] var state: Option[PropositionState] = None

  override def receiveRecover: Receive = {
    case e: PropositionEvent =>
      println(s"Recovering event $e")
      applyEvent(e)
    case SnapshotOffer(_, snapshot: PropositionState) =>
      println(s"Recovering from snapshot $snapshot")
      state = Some(snapshot)
    case _: RecoveryCompleted =>
  }

  override def receiveCommand: Receive = {
    case GetProposition(_) => sender ! state.map(_.toProposition)
    case _: ViewPropositionCommand =>
      persistAndPublishEvent(PropositionViewed(id = propositionId))
    case propose: ProposeCommand =>
      persistAndPublishEvent(PropositionProposed(
        id = propositionId,
        citizenId = propose.citizenId,
        createdAt = propose.createdAt,
        content = propose.content
      ))
      Patterns.pipe((self ? GetProposition(propositionId)) (1.second), Implicits.global).to(sender)
      self ! Snapshot
    case update: UpdatePropositionCommand =>
      persistAndPublishEvent(PropositionUpdated(
        id = propositionId,
        updatedAt = update.updatedAt,
        content = update.content
      ))
      Patterns.pipe((self ? GetProposition(propositionId)) (1.second), Implicits.global).to(sender)
      self ! Snapshot
    case Snapshot => state.foreach(state => saveSnapshot(state.toProposition))
  }

  override def persistenceId: String = propositionId.value

  private val applyEvent: PartialFunction[PropositionEvent, Unit] = {
    case e: PropositionProposed => state = Some(PropositionState(
      propositionId = propositionId,
      citizenId = Option(e.citizenId),
      createdAt = Option(e.createdAt),
      updatedAt = Option(e.createdAt),
      content = Option(e.content)
    ))
    case e: PropositionUpdated => state.foreach(p => {
      p.content = Option(e.content)
      p.updatedAt = Option(e.updatedAt)
    })
    case _ =>
  }

  private def persistAndPublishEvent(event: PropositionEvent): Unit = {
    persist(event)(applyEvent)
    context.system.eventStream.publish(event)
  }

  case class PropositionState(
                               propositionId: PropositionId,
                               citizenId: Option[CitizenId],
                               createdAt: Option[ZonedDateTime],
                               var updatedAt: Option[ZonedDateTime],
                               var content: Option[String]
                             ) {
    def toProposition: Proposition = {
      Proposition(
        this.propositionId,
        this.citizenId.orNull,
        this.createdAt.orNull,
        this.updatedAt.orNull,
        this.content.orNull
      )
    }
  }
}

object PropositionActor {
  val props: Props = Props[PropositionActor]

  case object Snapshot
}