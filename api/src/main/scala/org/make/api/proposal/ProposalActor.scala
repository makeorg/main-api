package org.make.api.proposal

import java.net.URLEncoder
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZoneOffset}

import akka.actor.{ActorLogging, PoisonPill, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import org.make.api.proposal.ProposalActor.{applyProposalAccepted, Snapshot}
import org.make.core.{DateHelper, ValidationError, ValidationFailedError}
import org.make.core.proposal.ProposalEvent._
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal._

class ProposalActor extends PersistentActor with ActorLogging {
  def proposalId: ProposalId = ProposalId(self.path.name)

  private[this] var state: Option[Proposal] = None

  override def receiveRecover: Receive = {
    case e: ProposalEvent                     => state = applyEvent(e)
    case SnapshotOffer(_, snapshot: Proposal) => state = Some(snapshot)
    case _: RecoveryCompleted                 =>
  }

  override def receiveCommand: Receive = {
    case GetProposal(_, _)              => sender() ! state
    case command: ViewProposalCommand   => onViewProposalCommand(command)
    case command: ProposeCommand        => onProposeCommand(command)
    case command: UpdateProposalCommand => onUpdateProposalCommand(command)
    case command: AcceptProposalCommand => onAcceptProposalCommand(command)
    case Snapshot                       => state.foreach(saveSnapshot)
    case _: KillProposalShard           => self ! PoisonPill
  }

  private def onViewProposalCommand(command: ViewProposalCommand): Unit = {
    persistAndPublishEvent(ProposalViewed(id = proposalId, eventDate = DateHelper.now(), context = command.context)) {
      sender() ! state
    }
  }

  private def onProposeCommand(command: ProposeCommand): Unit = {
    val user = command.user
    persistAndPublishEvent(
      ProposalProposed(
        id = proposalId,
        author = ProposalAuthorInfo(
          user.userId,
          user.firstName,
          user.profile.flatMap(_.postalCode),
          user.profile.flatMap(_.dateOfBirth).map { date =>
            ChronoUnit.YEARS.between(date, LocalDate.now(ZoneOffset.UTC)).toInt
          }
        ),
        slug = ProposalActor.createSlug(command.content),
        context = command.context,
        userId = user.userId,
        eventDate = command.createdAt,
        content = command.content
      )
    ) {
      sender() ! proposalId
      self ! Snapshot
    }

  }

  private def onUpdateProposalCommand(command: UpdateProposalCommand): Unit = {
    persistAndPublishEvent(
      ProposalUpdated(
        id = proposalId,
        eventDate = DateHelper.now(),
        context = command.context,
        updatedAt = command.updatedAt,
        content = command.content
      )
    ) {
      sender() ! state
      self ! Snapshot
    }
  }

  private def onAcceptProposalCommand(command: AcceptProposalCommand): Unit = {
    val maybeEvent = validateAcceptCommand(command)
    maybeEvent match {
      case Right(event) =>
        persistAndPublishEvent(event) {
          sender() ! state
        }
      case Left(error) => sender() ! error
    }
  }

  private def validateAcceptCommand(command: AcceptProposalCommand): Either[ValidationFailedError, ProposalEvent] = {
    state.map { proposal =>
      if (proposal.status == ProposalStatus.Archived) {
        Left(
          ValidationFailedError(
            errors = Seq(
              ValidationError(
                "unknown",
                Some(s"Proposal ${command.proposalId.value} is archived and cannot be validated")
              )
            )
          )
        )
      } else if (proposal.status == ProposalStatus.Accepted) {
        // possible double request, ignore.
        // other modifications should use the proposal update method
        Left(
          ValidationFailedError(
            errors = Seq(ValidationError("unknown", Some(s"Proposal ${command.proposalId.value} is already validated")))
          )
        )
      } else {
        Right(
          ProposalAccepted(
            id = command.proposalId,
            eventDate = DateHelper.now(),
            context = command.context,
            moderator = command.moderator,
            edition = command.newContent.map { newContent =>
              ProposalEdition(proposal.content, newContent)
            },
            sendValidationEmail = command.sendNotificationEmail,
            theme = command.theme,
            labels = command.labels,
            tags = command.tags,
            similarProposals = command.similarProposals
          )
        )
      }
    }.getOrElse(
      Left(
        ValidationFailedError(
          errors = Seq(ValidationError("unknown", Some(s"Proposal ${command.proposalId.value} doesn't exist")))
        )
      )
    )
  }

  override def persistenceId: String = proposalId.value

  private val applyEvent: PartialFunction[ProposalEvent, Option[Proposal]] = {
    case e: ProposalProposed =>
      Some(
        Proposal(
          proposalId = e.id,
          slug = e.slug,
          author = e.userId,
          createdAt = Some(e.eventDate),
          updatedAt = None,
          content = e.content,
          status = ProposalStatus.Pending,
          theme = e.context.currentTheme,
          creationContext = e.context,
          labels = Seq(),
          events = List(
            ProposalAction(
              date = e.eventDate,
              user = e.userId,
              actionType = "propose",
              arguments = Map("content" -> e.content)
            )
          )
        )
      )
    case e: ProposalUpdated =>
      state.map(
        _.copy(content = e.content, slug = ProposalActor.createSlug(e.content), updatedAt = Option(e.updatedAt))
      )
    case e: ProposalAccepted => state.map(proposal => applyProposalAccepted(proposal, e))
    case _                   => state
  }

  private def persistAndPublishEvent(event: ProposalEvent)(andThen: => Unit): Unit = {
    persist(event) { e: ProposalEvent =>
      state = applyEvent(e)
      context.system.eventStream.publish(e)
      andThen
    }
  }

}

object ProposalActor {
  val props: Props = Props[ProposalActor]

  def createSlug(content: String): String = {
    URLEncoder.encode(content.toLowerCase().replaceAll("\\s", "-"), "UTF-8")
  }

  def applyProposalAccepted(state: Proposal, event: ProposalAccepted): Proposal = {
    val action =
      ProposalAction(date = event.eventDate, user = event.moderator, actionType = "accept", arguments = Map())
    var result =
      state.copy(
        tags = event.tags,
        labels = event.labels,
        events = action :: state.events,
        status = Accepted,
        updatedAt = Some(event.eventDate)
      )

    result = event.edition match {
      case None                                 => result
      case Some(ProposalEdition(_, newVersion)) => result.copy(content = newVersion, slug = createSlug(newVersion))
    }
    result = event.theme match {
      case None  => result
      case theme => result.copy(theme = theme)
    }
    result
  }

  case object Snapshot
}
