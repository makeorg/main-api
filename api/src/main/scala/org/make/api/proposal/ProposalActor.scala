package org.make.api.proposal

import java.net.URLEncoder
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZoneOffset}

import akka.actor.{ActorLogging, PoisonPill, Props}
import akka.pattern.{ask, Patterns}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import org.make.api.proposal.ProposalActor.Snapshot
import org.make.api.technical.DateHelper
import org.make.core.proposal.ProposalEvent._
import org.make.core.proposal._

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.duration._

class ProposalActor extends PersistentActor with ActorLogging {
  def proposalId: ProposalId = ProposalId(self.path.name)

  private[this] var state: Option[Proposal] = None

  override def receiveRecover: Receive = {
    case e: ProposalEvent =>
      log.info(s"Recovering event $e")
      applyEvent(e)
    case SnapshotOffer(_, snapshot: Proposal) =>
      log.info(s"Recovering from snapshot $snapshot")
      state = Some(snapshot)
    case _: RecoveryCompleted =>
  }

  override def receiveCommand: Receive = {
    case GetProposal(_, _) => sender() ! state
    case ViewProposalCommand(proposalId, requestContext) =>
      persistAndPublishEvent(ProposalViewed(id = proposalId, eventDate = DateHelper.now(), context = requestContext))
      Patterns
        .pipe((self ? GetProposal(proposalId, requestContext))(1.second), Implicits.global)
        .to(sender())
    case ProposeCommand(proposalId, requestContext, user, createdAt, content) =>
      persistAndPublishEvent(
        ProposalProposed(
          id = proposalId,
          author = ProposalAuthorInfo(
            user.userId,
            user.firstName,
            user.profile.flatMap(_.departmentNumber),
            user.profile.flatMap(_.dateOfBirth).map { date =>
              ChronoUnit.YEARS.between(date, LocalDate.now(ZoneOffset.UTC)).toInt
            }
          ),
          slug = ProposalActor.createSlug(content),
          context = requestContext,
          userId = user.userId,
          eventDate = createdAt,
          content = content
        )
      )
      sender() ! proposalId
      self ! Snapshot
    case UpdateProposalCommand(proposalId, requestContext, updatedAt, content) =>
      persistAndPublishEvent(
        ProposalUpdated(
          id = proposalId,
          eventDate = DateHelper.now(),
          context = requestContext,
          updatedAt = updatedAt,
          content = content
        )
      )
      Patterns
        .pipe((self ? GetProposal(proposalId, requestContext))(1.second), Implicits.global)
        .to(sender())
      self ! Snapshot
    case Snapshot             => state.foreach(saveSnapshot)
    case _: KillProposalShard => self ! PoisonPill
  }

  override def persistenceId: String = proposalId.value

  private val applyEvent: PartialFunction[ProposalEvent, Unit] = {
    case e: ProposalProposed =>
      state = Some(
        Proposal(
          proposalId = e.id,
          slug = e.slug,
          author = e.userId,
          createdAt = Some(e.eventDate),
          updatedAt = None,
          content = e.content,
          theme = e.context.currentTheme,
          creationContext = e.context
        )
      )
    case e: ProposalUpdated =>
      state = state.map(
        _.copy(content = e.content, slug = ProposalActor.createSlug(e.content), updatedAt = Option(e.updatedAt))
      )
    case _ =>
  }

  private def persistAndPublishEvent(event: ProposalEvent): Unit = {
    persist(event) { (e: ProposalEvent) =>
      applyEvent(e)
      context.system.eventStream.publish(e)
    }
  }

}

object ProposalActor {
  val props: Props = Props[ProposalActor]

  def createSlug(content: String): String = {
    URLEncoder.encode(content.toLowerCase().replaceAll("\\s", "-"), "UTF-8")

  }

  case object Snapshot
}
