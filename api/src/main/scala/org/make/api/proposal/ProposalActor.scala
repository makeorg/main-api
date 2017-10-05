package org.make.api.proposal

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZoneOffset}

import akka.actor.{ActorLogging, PoisonPill, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import org.make.api.proposal.ProposalActor._
import org.make.core._
import org.make.core.proposal.ProposalEvent._
import org.make.core.proposal.ProposalStatus.{Accepted, Refused}
import org.make.core.proposal.QualificationKey._
import org.make.core.proposal.VoteKey._
import org.make.core.proposal.{Qualification, Vote, _}

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
    case command: RefuseProposalCommand => onRefuseProposalCommand(command)
    case command: VoteProposalCommand   => onVoteProposalCommand(command)
    case command: UnvoteProposalCommand => onUnvoteProposalCommand(command)
    case command: QualifyVoteCommand    => onQualificationProposalCommand(command)
    case command: UnqualifyVoteCommand  => onUnqualificationProposalCommand(command)
    case Snapshot                       => state.foreach(saveSnapshot)
    case _: KillProposalShard           => self ! PoisonPill
  }

  private def onVoteProposalCommand(command: VoteProposalCommand): Unit = {
    persistAndPublishEvent(
      ProposalVoted(
        id = proposalId,
        maybeUserId = command.maybeUserId,
        eventDate = DateHelper.now(),
        requestContext = command.requestContext,
        voteKey = command.voteKey
      )
    ) {
      sender() ! state.flatMap(_.votes.find(_.key == command.voteKey))
      self ! Snapshot
    }
  }

  private def onUnvoteProposalCommand(command: UnvoteProposalCommand): Unit = {
    persistAndPublishEvent(
      ProposalUnvoted(
        id = proposalId,
        maybeUserId = command.maybeUserId,
        eventDate = DateHelper.now(),
        requestContext = command.requestContext,
        voteKey = command.voteKey
      )
    ) {
      sender() ! state.flatMap(_.votes.find(_.key == command.voteKey))
      self ! Snapshot
    }
  }

  private def onQualificationProposalCommand(command: QualifyVoteCommand): Unit = {
    persistAndPublishEvent(
      ProposalQualified(
        id = proposalId,
        maybeUserId = command.maybeUserId,
        eventDate = DateHelper.now(),
        requestContext = command.requestContext,
        voteKey = command.voteKey,
        qualificationKey = command.qualificationKey
      )
    ) {
      sender() ! state
        .flatMap(_.votes.find(_.key == command.voteKey))
        .flatMap(_.qualifications.find(_.key == command.qualificationKey))
      self ! Snapshot
    }
  }

  private def onUnqualificationProposalCommand(command: UnqualifyVoteCommand): Unit = {
    persistAndPublishEvent(
      ProposalUnqualified(
        id = proposalId,
        maybeUserId = command.maybeUserId,
        eventDate = DateHelper.now(),
        requestContext = command.requestContext,
        voteKey = command.voteKey,
        qualificationKey = command.qualificationKey
      )
    ) {
      sender() ! state
        .flatMap(_.votes.find(_.key == command.voteKey))
        .flatMap(_.qualifications.find(_.key == command.qualificationKey))
      self ! Snapshot
    }
  }

  private def onViewProposalCommand(command: ViewProposalCommand): Unit = {
    persistAndPublishEvent(
      ProposalViewed(id = proposalId, eventDate = DateHelper.now(), requestContext = command.requestContext)
    ) {
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
        slug = SlugHelper(command.content),
        requestContext = command.requestContext,
        userId = user.userId,
        eventDate = command.createdAt,
        content = command.content,
        theme = command.theme
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
        requestContext = command.requestContext,
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

  private def onRefuseProposalCommand(command: RefuseProposalCommand): Unit = {
    val maybeEvent = validateRefuseCommand(command)
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
            requestContext = command.requestContext,
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

  private def validateRefuseCommand(command: RefuseProposalCommand): Either[ValidationFailedError, ProposalEvent] = {
    state.map { proposal =>
      if (proposal.status == ProposalStatus.Archived) {
        Left(
          ValidationFailedError(
            errors = Seq(
              ValidationError(
                "unknown",
                Some(s"Proposal ${command.proposalId.value} is archived and cannot be refused")
              )
            )
          )
        )
      } else if (proposal.status == ProposalStatus.Refused) {
        // possible double request, ignore.
        // other modifications should use the proposal update command
        Left(
          ValidationFailedError(
            errors = Seq(ValidationError("unknown", Some(s"Proposal ${command.proposalId.value} is already refused")))
          )
        )
      } else {
        Right(
          ProposalRefused(
            id = command.proposalId,
            eventDate = DateHelper.now(),
            requestContext = command.requestContext,
            moderator = command.moderator,
            sendRefuseEmail = command.sendNotificationEmail,
            refusalReason = command.refusalReason
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
          theme = if (e.theme.isEmpty) e.requestContext.currentTheme else e.theme,
          creationContext = e.requestContext,
          labels = Seq.empty,
          votes = Seq(
            Vote(
              key = Agree,
              qualifications =
                Seq(Qualification(key = LikeIt), Qualification(key = Doable), Qualification(key = PlatitudeAgree))
            ),
            Vote(
              key = Disagree,
              qualifications =
                Seq(Qualification(key = NoWay), Qualification(key = Impossible), Qualification(key = PlatitudeDisagree))
            ),
            Vote(
              key = Neutral,
              qualifications = Seq(
                Qualification(key = DoNotUnderstand),
                Qualification(key = NoOpinion),
                Qualification(key = DoNotCare)
              )
            )
          ),
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
      state.map(_.copy(content = e.content, slug = SlugHelper(e.content), updatedAt = Option(e.updatedAt)))
    case e: ProposalAccepted    => state.map(proposal => applyProposalAccepted(proposal, e))
    case e: ProposalRefused     => state.map(proposal => applyProposalRefused(proposal, e))
    case e: ProposalVoted       => state.map(proposal => applyProposalVoted(proposal, e))
    case e: ProposalUnvoted     => state.map(proposal => applyProposalUnvoted(proposal, e))
    case e: ProposalQualified   => state.map(proposal => applyProposalQualified(proposal, e))
    case e: ProposalUnqualified => state.map(proposal => applyProposalUnqualified(proposal, e))
    case _                      => state
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
      case Some(ProposalEdition(_, newVersion)) => result.copy(content = newVersion, slug = SlugHelper(newVersion))
    }
    result = event.theme match {
      case None  => result
      case theme => result.copy(theme = theme)
    }
    result
  }

  def applyProposalRefused(state: Proposal, event: ProposalRefused): Proposal = {
    val action =
      ProposalAction(date = event.eventDate, user = event.moderator, actionType = "refuse", arguments = Map())
    state.copy(
      events = action :: state.events,
      status = Refused,
      refusalReason = event.refusalReason,
      updatedAt = Some(event.eventDate)
    )
  }

  def applyProposalVoted(state: Proposal, event: ProposalVoted): Proposal = {
    state.copy(votes = state.votes.map {
      case vote if vote.key == event.voteKey =>
        vote.copy(count = vote.count + 1)
      case vote => vote
    })
  }

  def applyProposalUnvoted(state: Proposal, event: ProposalUnvoted): Proposal = {
    state.copy(votes = state.votes.map {
      case vote if vote.key == event.voteKey =>
        vote.copy(
          count = vote.count - 1,
          qualifications = vote.qualifications.map(qualification => applyUnqualifVote(qualification))
        )
      case vote => vote
    })
  }

  def applyUnqualifVote(qualification: Qualification): Qualification = {
    qualification.copy(count = qualification.count - 1)
  }

  def applyProposalQualified(state: Proposal, event: ProposalQualified): Proposal = {
    state.copy(votes = state.votes.map {
      case vote if vote.key == event.voteKey =>
        vote.copy(qualifications = vote.qualifications.map {
          case qualification if qualification.key == event.qualificationKey =>
            qualification.copy(count = qualification.count + 1)
          case qualification => qualification
        })
      case vote => vote
    })
  }

  def applyProposalUnqualified(state: Proposal, event: ProposalUnqualified): Proposal = {
    state.copy(votes = state.votes.map {
      case vote if vote.key == event.voteKey =>
        vote.copy(qualifications = vote.qualifications.map {
          case qualification if qualification.key == event.qualificationKey =>
            qualification.copy(count = qualification.count - 1)
          case qualification => qualification
        })
      case vote => vote
    })
  }

  case object Snapshot
}
