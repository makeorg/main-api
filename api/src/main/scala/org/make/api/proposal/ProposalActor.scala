package org.make.api.proposal

import java.time.ZonedDateTime

import akka.actor.{ActorLogging, PoisonPill, Props}
import akka.pattern.{ask, Patterns}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import org.make.api.proposal.ProposalActor.Snapshot
import org.make.core.user.UserId
import org.make.core.proposal.ProposalEvent._
import org.make.core.proposal._

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.duration._

class ProposalActor extends PersistentActor with ActorLogging {
  def proposalId: ProposalId = ProposalId(self.path.name)

  private[this] var state: Option[ProposalState] = None

  override def receiveRecover: Receive = {
    case e: ProposalEvent =>
      log.info(s"Recovering event $e")
      applyEvent(e)
    case SnapshotOffer(_, snapshot: Proposal) =>
      log.info(s"Recovering from snapshot $snapshot")
      state = Some(
        ProposalState(
          proposalId = snapshot.proposalId,
          userId = Option(snapshot.userId),
          createdAt = snapshot.createdAt,
          updatedAt = snapshot.updatedAt,
          content = Option(snapshot.content)
        )
      )
    case _: RecoveryCompleted =>
  }

  override def receiveCommand: Receive = {
    case GetProposal(_) => sender ! state.map(_.toProposal)
    case v: ViewProposalCommand =>
      persistAndPublishEvent(ProposalViewed(id = v.proposalId))
      Patterns
        .pipe((self ? GetProposal(v.proposalId))(1.second), Implicits.global)
        .to(sender)
    case propose: ProposeCommand =>
      persistAndPublishEvent(
        ProposalProposed(
          id = propose.proposalId,
          userId = propose.userId,
          createdAt = propose.createdAt,
          content = propose.content
        )
      )
      Patterns
        .pipe((self ? GetProposal(propose.proposalId))(1.second), Implicits.global)
        .to(sender)
      self ! Snapshot
    case update: UpdateProposalCommand =>
      persistAndPublishEvent(
        ProposalUpdated(id = update.proposalId, updatedAt = update.updatedAt, content = update.content)
      )
      Patterns
        .pipe((self ? GetProposal(update.proposalId))(1.second), Implicits.global)
        .to(sender)
      self ! Snapshot
    case Snapshot             => state.foreach(state => saveSnapshot(state.toProposal))
    case KillProposalShard(_) => self ! PoisonPill
  }

  override def persistenceId: String = proposalId.value

  private val applyEvent: PartialFunction[ProposalEvent, Unit] = {
    case e: ProposalProposed =>
      state = Some(
        ProposalState(
          proposalId = e.id,
          userId = Option(e.userId),
          createdAt = Option(e.createdAt),
          updatedAt = Option(e.createdAt),
          content = Option(e.content)
        )
      )
    case e: ProposalUpdated =>
      state.foreach(p => {
        p.content = Option(e.content)
        p.updatedAt = Option(e.updatedAt)
      })
    case _ =>
  }

  private def persistAndPublishEvent(event: ProposalEvent): Unit = {
    persist(event) { (e: ProposalEvent) =>
      applyEvent(e)
      context.system.eventStream.publish(e)
    }
  }

  case class ProposalState(proposalId: ProposalId,
                           userId: Option[UserId],
                           createdAt: Option[ZonedDateTime],
                           var updatedAt: Option[ZonedDateTime],
                           var content: Option[String]) {
    def toProposal: Proposal = {
      Proposal(this.proposalId, this.userId.orNull, this.content.orNull, this.createdAt, this.updatedAt)
    }
  }
}

object ProposalActor {
  val props: Props = Props[ProposalActor]

  case object Snapshot
}
