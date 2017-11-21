package org.make.api.proposal

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}

import akka.actor.{ActorLogging, ActorRef, PoisonPill}
import akka.pattern.ask
import akka.persistence.{PersistentActor, SnapshotOffer}
import akka.util.Timeout
import org.make.api.proposal.ProposalActor.Lock._
import org.make.api.proposal.ProposalActor._
import org.make.api.proposal.ProposalEvent._
import org.make.api.sessionhistory._
import org.make.api.userhistory._
import org.make.core.SprayJsonFormatters._
import org.make.core._
import org.make.core.proposal.ProposalStatus.{Accepted, Refused}
import org.make.core.proposal.QualificationKey._
import org.make.core.proposal.VoteKey._
import org.make.core.proposal.{Qualification, Vote, _}
import org.make.core.user.UserId
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class ProposalActor(userHistoryActor: ActorRef, sessionHistoryActor: ActorRef)
    extends PersistentActor
    with ActorLogging {

  implicit val timeout: Timeout = Timeout(3.seconds)

  def proposalId: ProposalId = ProposalId(self.path.name)

  private[this] var state: Option[ProposalState] = None

  override def receiveRecover: Receive = {
    case e: ProposalEvent                          => state = applyEvent(e)
    case SnapshotOffer(_, snapshot: Proposal)      => state = Some(ProposalState(snapshot))
    case SnapshotOffer(_, snapshot: ProposalState) => state = Some(snapshot)
    case _                                         =>
  }

  override def receiveCommand: Receive = {
    case GetProposal(_, _)                         => sender() ! state.map(_.proposal)
    case command: ViewProposalCommand              => onViewProposalCommand(command)
    case command: ProposeCommand                   => onProposeCommand(command)
    case command: UpdateProposalCommand            => onUpdateProposalCommand(command)
    case command: AcceptProposalCommand            => onAcceptProposalCommand(command)
    case command: RefuseProposalCommand            => onRefuseProposalCommand(command)
    case command: VoteProposalCommand              => onVoteProposalCommand(command)
    case command: UnvoteProposalCommand            => onUnvoteProposalCommand(command)
    case command: QualifyVoteCommand               => onQualificationProposalCommand(command)
    case command: UnqualifyVoteCommand             => onUnqualificationProposalCommand(command)
    case command: LockProposalCommand              => onLockProposalCommand(command)
    case command: UpdateDuplicatedProposalsCommand => onUpdateDuplicatedProposalsCommand(command)
    case Snapshot                                  => state.foreach(saveSnapshot)
    case _: KillProposalShard                      => self ! PoisonPill
  }

  private def onUpdateDuplicatedProposalsCommand(command: UpdateDuplicatedProposalsCommand): Unit = {
    state.foreach { proposalState =>
      val newDuplicates =
        command.duplicates.filter(
          id => id != proposalState.proposal.proposalId && !proposalState.proposal.similarProposals.contains(id)
        )

      if (newDuplicates.nonEmpty) {
        persistAndPublishEvent(
          SimilarProposalsAdded(
            command.proposalId,
            proposalState.proposal.similarProposals.toSet ++ command.duplicates.toSet,
            command.requestContext,
            DateHelper.now()
          )
        )(_ => {})
      }
    }
  }

  private def onVoteProposalCommand(command: VoteProposalCommand): Unit = {
    command.vote match {
      case None =>
        persistAndPublishEvent(
          ProposalVoted(
            id = proposalId,
            maybeUserId = command.maybeUserId,
            eventDate = DateHelper.now(),
            requestContext = command.requestContext,
            voteKey = command.voteKey
          )
        ) { event =>
          val originalSender = sender()
          logVoteEvent(event).onComplete {
            case Success(_) =>
              originalSender ! Right(state.flatMap(_.proposal.votes.find(_.key == command.voteKey)))
            case Failure(e) => originalSender ! Left(e)
          }
        }
      case Some(vote) if vote.voteKey == command.voteKey =>
        sender() ! Right(state.flatMap(_.proposal.votes.find(_.key == command.voteKey)))
      case Some(vote) =>
        val unvoteEvent = ProposalUnvoted(
          id = proposalId,
          maybeUserId = command.maybeUserId,
          eventDate = DateHelper.now(),
          requestContext = command.requestContext,
          voteKey = vote.voteKey,
          selectedQualifications = vote.qualificationKeys
        )
        val voteEvent = ProposalVoted(
          id = proposalId,
          maybeUserId = command.maybeUserId,
          eventDate = DateHelper.now(),
          requestContext = command.requestContext,
          voteKey = command.voteKey
        )
        persistAndPublishEvents(immutable.Seq(unvoteEvent, voteEvent)) {
          case _: ProposalVoted =>
            val originalSender = sender()
            logUnvoteEvent(unvoteEvent).flatMap(_ => logVoteEvent(voteEvent)).onComplete {
              case Success(_) => originalSender ! Right(state.flatMap(_.proposal.votes.find(_.key == command.voteKey)))
              case Failure(e) => originalSender ! Left(e)
            }
          case _ =>
        }
    }
  }

  private def logVoteEvent(event: ProposalVoted): Future[_] = {
    event.maybeUserId match {
      case Some(userId) =>
        userHistoryActor ? LogUserVoteEvent(
          userId = userId,
          requestContext = event.requestContext,
          action = UserAction(
            date = event.eventDate,
            actionType = "vote",
            UserVote(proposalId = event.id, voteKey = event.voteKey)
          )
        )
      case None =>
        sessionHistoryActor ? LogSessionVoteEvent(
          sessionId = event.requestContext.sessionId,
          requestContext = event.requestContext,
          action = SessionAction(
            date = event.eventDate,
            actionType = "vote",
            SessionVote(proposalId = event.id, voteKey = event.voteKey)
          )
        )
    }
  }

  private def logUnvoteEvent(event: ProposalUnvoted): Future[_] = {
    event.maybeUserId match {
      case Some(userId) =>
        userHistoryActor ? LogUserUnvoteEvent(
          userId = userId,
          requestContext = event.requestContext,
          action = UserAction(
            date = event.eventDate,
            actionType = "unvote",
            UserUnvote(proposalId = event.id, voteKey = event.voteKey)
          )
        )
      case None =>
        sessionHistoryActor ? LogSessionUnvoteEvent(
          sessionId = event.requestContext.sessionId,
          requestContext = event.requestContext,
          action = SessionAction(
            date = event.eventDate,
            actionType = "unvote",
            SessionUnvote(proposalId = event.id, voteKey = event.voteKey)
          )
        )
    }
  }

  private def logQualifyVoteEvent(event: ProposalQualified): Future[_] = {
    event.maybeUserId match {
      case Some(userId) =>
        userHistoryActor ? LogUserQualificationEvent(
          userId = userId,
          requestContext = event.requestContext,
          action = UserAction(
            date = event.eventDate,
            actionType = "qualify",
            UserQualification(proposalId = event.id, qualificationKey = event.qualificationKey)
          )
        )
      case None =>
        sessionHistoryActor ? LogSessionQualificationEvent(
          sessionId = event.requestContext.sessionId,
          requestContext = event.requestContext,
          action = SessionAction(
            date = event.eventDate,
            actionType = "qualify",
            SessionQualification(proposalId = event.id, qualificationKey = event.qualificationKey)
          )
        )
    }
  }

  private def logRemoveVoteQualificationEvent(event: ProposalUnqualified): Future[_] = {
    event.maybeUserId match {
      case Some(userId) =>
        userHistoryActor ? LogUserUnqualificationEvent(
          userId = userId,
          requestContext = event.requestContext,
          action = UserAction(
            date = event.eventDate,
            actionType = "unqualify",
            UserUnqualification(proposalId = event.id, qualificationKey = event.qualificationKey)
          )
        )
      case None =>
        sessionHistoryActor ? LogSessionUnqualificationEvent(
          sessionId = event.requestContext.sessionId,
          requestContext = event.requestContext,
          action = SessionAction(
            date = event.eventDate,
            actionType = "unqualify",
            SessionUnqualification(proposalId = event.id, qualificationKey = event.qualificationKey)
          )
        )
    }
  }

  private def onUnvoteProposalCommand(command: UnvoteProposalCommand): Unit = {
    command.vote match {
      case None => sender() ! Right(state.flatMap(_.proposal.votes.find(_.key == command.voteKey)))
      case Some(vote) =>
        persistAndPublishEvent(
          ProposalUnvoted(
            id = proposalId,
            maybeUserId = command.maybeUserId,
            eventDate = DateHelper.now(),
            requestContext = command.requestContext,
            voteKey = vote.voteKey,
            selectedQualifications = vote.qualificationKeys
          )
        ) { event =>
          val originalSender = sender()
          logUnvoteEvent(event).flatMap { _ =>
            Future.traverse(event.selectedQualifications) { qualification =>
              logRemoveVoteQualificationEvent(
                ProposalUnqualified(
                  event.id,
                  maybeUserId = event.maybeUserId,
                  eventDate = event.eventDate,
                  requestContext = event.requestContext,
                  voteKey = event.voteKey,
                  qualificationKey = qualification
                )
              )
            }
          }.onComplete {
            case Success(_) => originalSender ! Right(state.flatMap(_.proposal.votes.find(_.key == command.voteKey)))
            case Failure(e) => originalSender ! Left(e)
          }

        }
    }

  }

  private def checkQualification(voteKey: VoteKey,
                                 commandVoteKey: VoteKey,
                                 qualificationKey: QualificationKey): Boolean = {
    voteKey match {
      case key if key != commandVoteKey => false
      case Agree =>
        qualificationKey == LikeIt || qualificationKey == Doable || qualificationKey == PlatitudeAgree
      case Disagree =>
        qualificationKey == NoWay || qualificationKey == Impossible || qualificationKey == PlatitudeDisagree
      case Neutral =>
        qualificationKey == DoNotCare || qualificationKey == DoNotUnderstand || qualificationKey == NoOpinion
      case _ => false
    }
  }

  private def onQualificationProposalCommand(command: QualifyVoteCommand): Unit = {
    command.vote match {
      case None =>
        sender() ! Right(
          state
            .flatMap(_.proposal.votes.find(_.key == command.voteKey))
            .flatMap(_.qualifications.find(_.key == command.qualificationKey))
        )
      case Some(vote) if vote.qualificationKeys.contains(command.qualificationKey) =>
        sender() ! Right(
          state
            .flatMap(_.proposal.votes.find(_.key == command.voteKey))
            .flatMap(_.qualifications.find(_.key == command.qualificationKey))
        )
      case Some(vote) if !checkQualification(vote.voteKey, command.voteKey, command.qualificationKey) =>
        sender() ! Right(
          state
            .flatMap(_.proposal.votes.find(_.key == command.voteKey))
            .flatMap(_.qualifications.find(_.key == command.qualificationKey))
        )
      case _ =>
        persistAndPublishEvent(
          ProposalQualified(
            id = proposalId,
            maybeUserId = command.maybeUserId,
            eventDate = DateHelper.now(),
            requestContext = command.requestContext,
            voteKey = command.voteKey,
            qualificationKey = command.qualificationKey
          )
        ) { event =>
          val originalSender = sender()
          logQualifyVoteEvent(event).onComplete {
            case Success(_) =>
              originalSender ! Right(
                state
                  .flatMap(_.proposal.votes.find(_.key == command.voteKey))
                  .flatMap(_.qualifications.find(_.key == command.qualificationKey))
              )
            case Failure(e) => Left(e)
          }
        }
    }
  }

  private def onUnqualificationProposalCommand(command: UnqualifyVoteCommand): Unit = {
    command.vote match {
      case None =>
        sender() ! Right(
          state
            .flatMap(_.proposal.votes.find(_.key == command.voteKey))
            .flatMap(_.qualifications.find(_.key == command.qualificationKey))
        )
      case Some(vote) if vote.qualificationKeys.contains(command.qualificationKey) =>
        persistAndPublishEvent(
          ProposalUnqualified(
            id = proposalId,
            maybeUserId = command.maybeUserId,
            eventDate = DateHelper.now(),
            requestContext = command.requestContext,
            voteKey = command.voteKey,
            qualificationKey = command.qualificationKey
          )
        ) { event =>
          val originalSender = sender()
          logRemoveVoteQualificationEvent(event).onComplete {
            case Success(_) =>
              originalSender ! Right(
                state
                  .flatMap(_.proposal.votes.find(_.key == command.voteKey))
                  .flatMap(_.qualifications.find(_.key == command.qualificationKey))
              )
            case Failure(e) => originalSender ! Left(e)
          }
        }
      case _ =>
        sender() ! Right(
          state
            .flatMap(_.proposal.votes.find(_.key == command.voteKey))
            .flatMap(_.qualifications.find(_.key == command.qualificationKey))
        )
    }

  }

  private def onViewProposalCommand(command: ViewProposalCommand): Unit = {
    persistAndPublishEvent(
      ProposalViewed(id = proposalId, eventDate = DateHelper.now(), requestContext = command.requestContext)
    ) { _ =>
      sender() ! state.map(_.proposal)
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
    ) { _ =>
      sender() ! proposalId
      self ! Snapshot
    }

  }

  private def onUpdateProposalCommand(command: UpdateProposalCommand): Unit = {
    val maybeEvent = validateUpdateCommand(command)
    maybeEvent match {
      case Right(Some(event)) =>
        persistAndPublishEvent(event) { _ =>
          sender() ! state.map(_.proposal)
        }
      case Right(None) => sender() ! None
      case Left(error) => sender() ! error
    }
  }

  private def onAcceptProposalCommand(command: AcceptProposalCommand): Unit = {
    val maybeEvent = validateAcceptCommand(command)
    maybeEvent match {
      case Right(Some(event)) =>
        persistAndPublishEvent(event) { _ =>
          sender() ! state.map(_.proposal)
        }
      case Right(None) => sender() ! None
      case Left(error) => sender() ! error
    }
  }

  private def onRefuseProposalCommand(command: RefuseProposalCommand): Unit = {
    val maybeEvent = validateRefuseCommand(command)
    maybeEvent match {
      case Right(Some(event)) =>
        persistAndPublishEvent(event) { _ =>
          sender() ! state.map(_.proposal)
        }
      case Right(None) => sender() ! None
      case Left(error) => sender() ! error
    }
  }

  private def onLockProposalCommand(command: LockProposalCommand): Unit = {
    state match {
      case None => sender() ! Right(None)
      case Some(ProposalState(_, lock)) =>
        lock match {
          case Some(Lock(moderatorId, moderatorName, expirationDate))
              if moderatorId != command.moderatorId && expirationDate.isAfter(DateHelper.now()) =>
            sender() ! Left(ValidationFailedError(errors = Seq(ValidationError("moderatorName", Some(moderatorName)))))
          case _ =>
            persistAndPublishEvent(
              ProposalLocked(
                id = proposalId,
                moderatorId = command.moderatorId,
                moderatorName = command.moderatorName,
                eventDate = DateHelper.now(),
                requestContext = command.requestContext
              )
            ) { event =>
              sender() ! Right(Some(event.moderatorId))
            }
        }
    }
  }

  private def validateUpdateCommand(
    command: UpdateProposalCommand
  ): Either[ValidationFailedError, Option[ProposalEvent]] = {
    state.map {
      case ProposalState(proposal, lock) =>
        if (proposal.status != ProposalStatus.Accepted) {
          Left(
            ValidationFailedError(
              errors = Seq(
                ValidationError(
                  "unknown",
                  Some(s"Proposal ${command.proposalId.value} is not accepted and cannot be updated")
                )
              )
            )
          )
        } else {
          Right(
            Some(
              ProposalUpdated(
                id = proposalId,
                eventDate = DateHelper.now(),
                requestContext = command.requestContext,
                updatedAt = command.updatedAt,
                moderator = Some(command.moderator),
                edition = command.newContent.map { newContent =>
                  ProposalEdition(proposal.content, newContent)
                },
                theme = command.theme,
                labels = command.labels,
                tags = command.tags,
                similarProposals = command.similarProposals
              )
            )
          )
        }
    }.getOrElse(Right(None))
  }

  private def validateAcceptCommand(
    command: AcceptProposalCommand
  ): Either[ValidationFailedError, Option[ProposalEvent]] = {
    state.map {
      case ProposalState(proposal, lock) =>
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
              errors =
                Seq(ValidationError("unknown", Some(s"Proposal ${command.proposalId.value} is already validated")))
            )
          )
        } else {
          Right(
            Some(
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
          )
        }
    }.getOrElse(Right(None))
  }

  private def validateRefuseCommand(
    command: RefuseProposalCommand
  ): Either[ValidationFailedError, Option[ProposalEvent]] = {
    state.map {
      case ProposalState(proposal, lock) =>
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
            Some(
              ProposalRefused(
                id = command.proposalId,
                eventDate = DateHelper.now(),
                requestContext = command.requestContext,
                moderator = command.moderator,
                sendRefuseEmail = command.sendNotificationEmail,
                refusalReason = command.refusalReason
              )
            )
          )
        }
    }.getOrElse(Right(None))
  }

  override def persistenceId: String = proposalId.value

  private val applyEvent: PartialFunction[ProposalEvent, Option[ProposalState]] = {
    case e: ProposalProposed =>
      Some(
        ProposalState(
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
                qualifications = Seq(
                  Qualification(key = NoWay),
                  Qualification(key = Impossible),
                  Qualification(key = PlatitudeDisagree)
                )
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
            similarProposals = Seq.empty,
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
      )
    case e: ProposalUpdated       => state.map(proposalState => applyProposalUpdated(proposalState, e))
    case e: ProposalAccepted      => state.map(proposalState => applyProposalAccepted(proposalState, e))
    case e: ProposalRefused       => state.map(proposalState => applyProposalRefused(proposalState, e))
    case e: ProposalVoted         => state.map(proposalState => applyProposalVoted(proposalState, e))
    case e: ProposalUnvoted       => state.map(proposalState => applyProposalUnvoted(proposalState, e))
    case e: ProposalQualified     => state.map(proposalState => applyProposalQualified(proposalState, e))
    case e: ProposalUnqualified   => state.map(proposalState => applyProposalUnqualified(proposalState, e))
    case e: ProposalLocked        => state.map(proposalState => applyProposalLocked(proposalState, e))
    case e: SimilarProposalsAdded => state.map(proposalState => applySimilarProposalsAdded(proposalState, e))
    case _                        => state
  }

  private def persistAndPublishEvent[T <: ProposalEvent](event: T)(andThen: T => Unit): Unit = {
    persist(event) { e: T =>
      state = applyEvent(e)
      context.system.eventStream.publish(e)
      andThen(e)
    }
  }

  private def persistAndPublishEvents(events: immutable.Seq[ProposalEvent])(andThen: ProposalEvent => Unit): Unit = {
    persistAll(events) { event: ProposalEvent =>
      state = applyEvent(event)
      context.system.eventStream.publish(event)
      andThen(event)
    }
  }

}

object ProposalActor {
  case class ProposalState(proposal: Proposal, lock: Option[Lock] = None)

  object ProposalState {
    implicit val proposalStateFormatter: RootJsonFormat[ProposalState] =
      DefaultJsonProtocol.jsonFormat2(ProposalState.apply)
  }
  case class Lock(moderatorId: UserId,
                  moderatorName: String,
                  expirationDate: ZonedDateTime = DateHelper.now().plus(15, ChronoUnit.SECONDS))

  object Lock {
    implicit val LockFormatter: RootJsonFormat[Lock] =
      DefaultJsonProtocol.jsonFormat3(Lock.apply)
  }

  def applyProposalUpdated(state: ProposalState, event: ProposalUpdated): ProposalState = {
    if (event.moderator.isEmpty) {
      throw new IllegalArgumentException("Update must be done by a moderator")
    }
    state.lock match {
      case Some(Lock(moderatorId, moderatorName, expirationDate))
          if moderatorId != event.moderator.get && expirationDate.isAfter(DateHelper.now()) =>
        throw new IllegalArgumentException(s"Invalid moderator. Currently locked by: $moderatorName")
      case _ =>
    }

    val arguments: Map[String, String] = Map(
      "theme" -> event.theme.map(_.value).mkString(", "),
      "tags" -> event.tags.map(_.value).mkString(", "),
      "labels" -> event.labels.map(_.value).mkString(", ")
    ).filter(!_._2.isEmpty)
    val moderator: UserId = event.moderator.get
    val action =
      ProposalAction(date = event.eventDate, user = moderator, actionType = "update", arguments = arguments)
    var proposal =
      state.proposal.copy(
        tags = event.tags,
        labels = event.labels,
        events = action :: state.proposal.events,
        updatedAt = Some(event.eventDate),
        similarProposals = event.similarProposals
      )

    proposal = event.edition match {
      case None                                 => proposal
      case Some(ProposalEdition(_, newVersion)) => proposal.copy(content = newVersion, slug = SlugHelper(newVersion))
    }
    proposal = event.theme match {
      case None  => proposal
      case theme => proposal.copy(theme = theme)
    }
    state.copy(proposal = proposal, None)
  }

  def applyProposalAccepted(state: ProposalState, event: ProposalAccepted): ProposalState = {
    state.lock match {
      case Some(Lock(moderatorId, moderatorName, expirationDate))
          if moderatorId != event.moderator && expirationDate.isAfter(DateHelper.now()) =>
        throw new IllegalArgumentException(s"Invalid moderator. Currently locked by: $moderatorName")
      case _ =>
    }
    val arguments: Map[String, String] = Map(
      "theme" -> event.theme.map(_.value).mkString(", "),
      "tags" -> event.tags.map(_.value).mkString(", "),
      "labels" -> event.labels.map(_.value).mkString(", ")
    ).filter(!_._2.isEmpty)
    val action =
      ProposalAction(date = event.eventDate, user = event.moderator, actionType = "accept", arguments = arguments)
    var proposal =
      state.proposal.copy(
        tags = event.tags,
        labels = event.labels,
        events = action :: state.proposal.events,
        status = Accepted,
        updatedAt = Some(event.eventDate),
        similarProposals = event.similarProposals
      )

    proposal = event.edition match {
      case None                                 => proposal
      case Some(ProposalEdition(_, newVersion)) => proposal.copy(content = newVersion, slug = SlugHelper(newVersion))
    }
    proposal = event.theme match {
      case None  => proposal
      case theme => proposal.copy(theme = theme)
    }
    state.copy(proposal = proposal, None)
  }

  def applyProposalRefused(state: ProposalState, event: ProposalRefused): ProposalState = {
    state.lock match {
      case Some(Lock(moderatorId, moderatorName, expirationDate))
          if moderatorId != event.moderator && expirationDate.isAfter(DateHelper.now()) =>
        throw new IllegalArgumentException(s"Invalid moderator. Currently locked by: $moderatorName")
      case _ =>
    }
    val arguments: Map[String, String] =
      Map("refusalReason" -> event.refusalReason.mkString).filter(!_._2.isEmpty)
    val action =
      ProposalAction(date = event.eventDate, user = event.moderator, actionType = "refuse", arguments = arguments)
    state.copy(
      proposal = state.proposal.copy(
        events = action :: state.proposal.events,
        status = Refused,
        refusalReason = event.refusalReason,
        updatedAt = Some(event.eventDate)
      ),
      None
    )
  }

  def applyProposalVoted(state: ProposalState, event: ProposalVoted): ProposalState = {
    state.copy(proposal = state.proposal.copy(votes = state.proposal.votes.map {
      case vote if vote.key == event.voteKey =>
        vote.copy(count = vote.count + 1)
      case vote => vote
    }))
  }

  def applyProposalUnvoted(state: ProposalState, event: ProposalUnvoted): ProposalState = {
    state.copy(proposal = state.proposal.copy(votes = state.proposal.votes.map {
      case vote if vote.key == event.voteKey =>
        vote.copy(
          count = vote.count - 1,
          qualifications =
            vote.qualifications.map(qualification => applyUnqualifVote(qualification, event.selectedQualifications))
        )
      case vote => vote
    }))
  }

  def applyUnqualifVote(qualification: Qualification, selectedQualifications: Seq[QualificationKey]): Qualification = {
    if (selectedQualifications.contains(qualification.key)) {
      qualification.copy(count = qualification.count - 1)
    } else {
      qualification
    }
  }

  def applyProposalQualified(state: ProposalState, event: ProposalQualified): ProposalState = {
    state.copy(proposal = state.proposal.copy(votes = state.proposal.votes.map {
      case vote if vote.key == event.voteKey =>
        vote.copy(qualifications = vote.qualifications.map {
          case qualification if qualification.key == event.qualificationKey =>
            qualification.copy(count = qualification.count + 1)
          case qualification => qualification
        })
      case vote => vote
    }))
  }

  def applyProposalUnqualified(state: ProposalState, event: ProposalUnqualified): ProposalState = {
    state.copy(proposal = state.proposal.copy(votes = state.proposal.votes.map {
      case vote if vote.key == event.voteKey =>
        vote.copy(qualifications = vote.qualifications.map {
          case qualification if qualification.key == event.qualificationKey =>
            qualification.copy(count = qualification.count - 1)
          case qualification => qualification
        })
      case vote => vote
    }))
  }

  def applyProposalLocked(state: ProposalState, event: ProposalLocked): ProposalState = {
    state.lock match {
      case Some(Lock(moderatorId, moderatorName, expirationDate))
          if moderatorId != event.moderatorId && expirationDate.isAfter(DateHelper.now()) =>
        throw new IllegalArgumentException(s"Invalid moderator. Currently locked by: $moderatorName")
      case Some(Lock(moderatorId, _, _)) =>
        val arguments: Map[String, String] =
          Map("moderatorName" -> event.moderatorName.getOrElse("<unknown>")).filter(!_._2.isEmpty)
        val action =
          ProposalAction(date = event.eventDate, user = event.moderatorId, actionType = "lock", arguments = arguments)
        val proposal = if (moderatorId != event.moderatorId) {
          state.proposal.copy(events = action :: state.proposal.events, updatedAt = Some(event.eventDate))
        } else {
          state.proposal
        }
        state.copy(
          proposal = proposal,
          lock = Some(
            Lock(
              event.moderatorId,
              event.moderatorName.getOrElse("<unknown>"),
              event.eventDate.plus(15, ChronoUnit.SECONDS)
            )
          )
        )
      case None =>
        val arguments: Map[String, String] =
          Map("moderatorName" -> event.moderatorName.getOrElse("<unknown>")).filter(!_._2.isEmpty)
        val action =
          ProposalAction(date = event.eventDate, user = event.moderatorId, actionType = "lock", arguments = arguments)
        state.copy(
          proposal = state.proposal.copy(events = action :: state.proposal.events, updatedAt = Some(event.eventDate)),
          lock = Some(Lock(event.moderatorId, event.moderatorName.getOrElse("<unknown>")))
        )
    }
  }

  def applySimilarProposalsAdded(state: ProposalState, event: SimilarProposalsAdded): ProposalState = {
    state.copy(proposal = state.proposal.copy(similarProposals = event.similarProposals.toSeq))
  }

  case object Snapshot
}
