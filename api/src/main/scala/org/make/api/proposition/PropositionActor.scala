package org.make.api.proposition

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
    case _: RecoveryCompleted => _
  }

  override def receiveCommand: Receive = {
    case GetProposition(_) => sender ! state.map(_.toProposition)
    case _: ViewPropositionCommand =>
      persistAndPublishEvent(PropositionViewed(id = propositionId))
    case propose: ProposeCommand =>
      persistAndPublishEvent(PropositionProposed(
        id = propositionId,
        citizenId = propose.citizenId,
        content = propose.content
      ))
      Patterns.pipe((self ? GetProposition(propositionId)) (1.second), Implicits.global).to(sender)
      self ! Snapshot
    case update: UpdatePropositionCommand =>
      persistAndPublishEvent(PropositionUpdated(
        id = propositionId,
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
      content = Option(e.content)
    ))
    case e: PropositionUpdated => state.foreach(_.content = Option(e.content))
    case _ =>
  }

  private def persistAndPublishEvent(event: PropositionEvent): Unit = {
    persist(event)(applyEvent)
    context.system.eventStream.publish(event)
  }

  case class PropositionState(
                               propositionId: PropositionId,
                               citizenId: Option[CitizenId],
                               var content: Option[String]
                             ) {
    def toProposition: Proposition = {
      Proposition(
        this.propositionId,
        this.citizenId.orNull,
        this.content.orNull
      )
    }
  }
}

object PropositionActor {
  val props: Props = Props[PropositionActor]

  case object Snapshot
}