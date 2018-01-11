package org.make.api.proposal

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}

import akka.actor.{ActorLogging, ActorRef, PoisonPill}
import akka.pattern.ask
import akka.persistence.SnapshotOffer
import akka.util.Timeout
import org.make.api.operation.OperationService
import org.make.api.proposal.ProposalActor.Lock._
import org.make.api.proposal.ProposalActor._
import org.make.api.proposal.ProposalEvent._
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.sessionhistory._
import org.make.api.technical.MakePersistentActor
import org.make.api.technical.MakePersistentActor.Snapshot
import org.make.api.userhistory._
import org.make.core.SprayJsonFormatters._
import org.make.core._
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalStatus.{Accepted, Postponed, Refused}
import org.make.core.proposal.QualificationKey._
import org.make.core.proposal.VoteKey._
import org.make.core.proposal.{Qualification, Vote, _}
import org.make.core.user.UserId
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class ProposalActor(userHistoryActor: ActorRef, sessionHistoryActor: ActorRef, operationService: OperationService)
    extends MakePersistentActor(classOf[ProposalState], classOf[ProposalEvent])
    with ActorLogging {

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, snapshot: Proposal) => state = Some(ProposalState(snapshot))
    case other                                => super.receiveRecover(other)
  }

  implicit val timeout: Timeout = defaultTimeout

  def proposalId: ProposalId = ProposalId(self.path.name)

  override def receiveCommand: Receive = {
    case GetProposal(_, _)                         => sender() ! state.map(_.proposal)
    case command: ViewProposalCommand              => onViewProposalCommand(command)
    case command: ProposeCommand                   => onProposeCommand(command)
    case command: UpdateProposalCommand            => onUpdateProposalCommand(command)
    case command: AcceptProposalCommand            => onAcceptProposalCommand(command)
    case command: RefuseProposalCommand            => onRefuseProposalCommand(command)
    case command: PostponeProposalCommand          => onPostponeProposalCommand(command)
    case command: VoteProposalCommand              => onVoteProposalCommand(command)
    case command: UnvoteProposalCommand            => onUnvoteProposalCommand(command)
    case command: QualifyVoteCommand               => onQualificationProposalCommand(command)
    case command: UnqualifyVoteCommand             => onUnqualificationProposalCommand(command)
    case command: LockProposalCommand              => onLockProposalCommand(command)
    case command: UpdateDuplicatedProposalsCommand => onUpdateDuplicatedProposalsCommand(command)
    case command: RemoveSimilarProposalCommand     => onRemoveSimilarProposalCommand(command)
    case command: ClearSimilarProposalsCommand     => onClearSimilarProposalsCommand(command)
    case command: ReplaceProposalCommand           => onReplaceProposalCommand(command)
    case command: PatchProposalCommand             => onPatchProposalCommand(command)
    case Snapshot                                  => saveSnapshot()
    case _: KillProposalShard                      => self ! PoisonPill
  }

  private def onRemoveSimilarProposalCommand(command: RemoveSimilarProposalCommand): Unit = {
    if (state.exists(_.proposal.similarProposals.contains(command.similarToRemove))) {
      persist(
        SimilarProposalRemoved(
          id = command.proposalId,
          proposalToRemove = command.similarToRemove,
          requestContext = command.requestContext
        )
      ) { event =>
        newEventAdded(event)
      }
    } else if (command.similarToRemove == this.proposalId) {
      persist(SimilarProposalsCleared(id = command.proposalId, requestContext = command.requestContext))(
        event => newEventAdded(event)
      )
    }
  }

  private def onClearSimilarProposalsCommand(command: ClearSimilarProposalsCommand): Unit = {
    if (state.exists(_.proposal.similarProposals.nonEmpty)) {
      persist(SimilarProposalsCleared(id = command.proposalId, requestContext = command.requestContext)) { event =>
        newEventAdded(event)
      }
    }
  }

  private def onReplaceProposalCommand(command: ReplaceProposalCommand): Unit = {
    state.foreach { _ =>
      persistAndPublishEvent(
        ProposalPatched(id = command.proposalId, requestContext = command.requestContext, proposal = command.proposal)
      ) { _ =>
        {}
      }
    }
  }

  def onPatchProposalCommand(command: PatchProposalCommand): Unit = {
    state.map(_.proposal).foreach { proposal =>
      val changes = command.changes
      val modifiedContext =
        changes.creationContext.map { contextChanges =>
          proposal.creationContext.copy(
            currentTheme = contextChanges.currentTheme.map(Some(_)).getOrElse(proposal.creationContext.currentTheme),
            requestId = contextChanges.requestId.getOrElse(proposal.creationContext.requestId),
            sessionId = contextChanges.sessionId.getOrElse(proposal.creationContext.sessionId),
            externalId = contextChanges.externalId.getOrElse(proposal.creationContext.externalId),
            country = contextChanges.country.map(Some(_)).getOrElse(proposal.creationContext.country),
            language = contextChanges.language.map(Some(_)).getOrElse(proposal.creationContext.language),
            operationId = contextChanges.operation.map(Some(_)).getOrElse(proposal.creationContext.operationId),
            source = contextChanges.source.map(Some(_)).getOrElse(proposal.creationContext.source),
            location = contextChanges.location.map(Some(_)).getOrElse(proposal.creationContext.location),
            question = contextChanges.question.map(Some(_)).getOrElse(proposal.creationContext.question),
            hostname = contextChanges.hostname.map(Some(_)).getOrElse(proposal.creationContext.hostname),
            ipAddress = contextChanges.ipAddress.map(Some(_)).getOrElse(proposal.creationContext.ipAddress),
            getParameters = contextChanges.getParameters.map(Some(_)).getOrElse(proposal.creationContext.getParameters),
            userAgent = contextChanges.userAgent.map(Some(_)).getOrElse(proposal.creationContext.userAgent)
          )
        }.getOrElse(proposal.creationContext)

      val modifiedProposal =
        proposal.copy(
          creationContext = modifiedContext,
          slug = changes.slug.getOrElse(proposal.slug),
          content = changes.content.getOrElse(proposal.content),
          idea = changes.ideaId.map(Some(_)).getOrElse(proposal.idea),
          author = changes.author.getOrElse(proposal.author),
          labels = changes.labels.getOrElse(proposal.labels),
          theme = changes.theme.map(Some(_)).getOrElse(proposal.theme),
          status = changes.status.getOrElse(proposal.status),
          refusalReason = changes.refusalReason.map(Some(_)).getOrElse(proposal.refusalReason),
          tags = changes.tags.getOrElse(proposal.tags),
          updatedAt = Some(DateHelper.now()),
          operation = changes.operation.orElse(proposal.operation)
        )

      persistAndPublishEvent(
        ProposalPatched(id = command.proposalId, requestContext = command.requestContext, proposal = modifiedProposal)
      ) { event =>
        state = applyEvent(event)
        if (command.changes.operation.isDefined && command.changes.operation != proposal.operation) {
          changeSequence(
            command.proposalId,
            currentOperation = proposal.operation,
            newOperation = command.changes.operation,
            requestContext = command.requestContext,
            moderatorId = command.userId
          )
        }
        sender() ! state.map(_.proposal)
      }
    }
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
        ) { _ =>
          {}
        }
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
            actionType = ProposalVoteAction.name,
            UserVote(proposalId = event.id, voteKey = event.voteKey)
          )
        )
      case None =>
        sessionHistoryActor ? LogSessionVoteEvent(
          sessionId = event.requestContext.sessionId,
          requestContext = event.requestContext,
          action = SessionAction(
            date = event.eventDate,
            actionType = ProposalVoteAction.name,
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
            actionType = ProposalUnvoteAction.name,
            UserUnvote(proposalId = event.id, voteKey = event.voteKey)
          )
        )
      case None =>
        sessionHistoryActor ? LogSessionUnvoteEvent(
          sessionId = event.requestContext.sessionId,
          requestContext = event.requestContext,
          action = SessionAction(
            date = event.eventDate,
            actionType = ProposalUnvoteAction.name,
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
            actionType = ProposalQualifyAction.name,
            UserQualification(proposalId = event.id, qualificationKey = event.qualificationKey)
          )
        )
      case None =>
        sessionHistoryActor ? LogSessionQualificationEvent(
          sessionId = event.requestContext.sessionId,
          requestContext = event.requestContext,
          action = SessionAction(
            date = event.eventDate,
            actionType = ProposalQualifyAction.name,
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
            actionType = ProposalUnqualifyAction.name,
            UserUnqualification(proposalId = event.id, qualificationKey = event.qualificationKey)
          )
        )
      case None =>
        sessionHistoryActor ? LogSessionUnqualificationEvent(
          sessionId = event.requestContext.sessionId,
          requestContext = event.requestContext,
          action = SessionAction(
            date = event.eventDate,
            actionType = ProposalUnqualifyAction.name,
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
        operation = command.operation,
        theme = command.theme
      )
    ) { _ =>
      sender() ! proposalId
    }

  }

  private def addToNewOperation(proposalId: ProposalId,
                                operation: OperationId,
                                requestContext: RequestContext,
                                moderatorId: UserId): Unit = {
    persistAndPublishEvent(
      ProposalAddedToOperation(proposalId, operation, moderatorId, requestContext = requestContext)
    ) { _ =>
      ()
    }
  }

  private def removeFromOperation(proposalId: ProposalId,
                                  operation: OperationId,
                                  requestContext: RequestContext,
                                  moderatorId: UserId): Unit = {
    persistAndPublishEvent(
      ProposalRemovedFromOperation(proposalId, operation, moderatorId, requestContext = requestContext)
    ) { _ =>
      ()
    }
  }

  private def changeSequence(proposalId: ProposalId,
                             currentOperation: Option[OperationId],
                             newOperation: Option[OperationId],
                             requestContext: RequestContext,
                             moderatorId: UserId): Unit = {
    // Remove from previous operation sequence if any
    currentOperation.foreach { operation =>
      removeFromOperation(proposalId, operation, requestContext, moderatorId)
    }

    // Add to new operation sequence if any
    newOperation.foreach { operation =>
      addToNewOperation(proposalId, operation, requestContext, moderatorId)
    }
  }

  private def onUpdateProposalCommand(command: UpdateProposalCommand): Unit = {
    val maybeEvent = validateUpdateCommand(command)
    val proposal: Option[Proposal] = state.map(_.proposal)
    maybeEvent match {
      case Right(Some(event)) =>
        persistAndPublishEvent(event) { _ =>
          if (command.operation != proposal.flatMap(_.operation)) {
            changeSequence(
              command.proposalId,
              currentOperation = proposal.flatMap(_.operation),
              newOperation = command.operation,
              requestContext = command.requestContext,
              moderatorId = command.moderator
            )
          }
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
          command.operation.foreach { operationId =>
            addToNewOperation(command.proposalId, operationId, command.requestContext, command.moderator)
          }
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

  private def onPostponeProposalCommand(command: PostponeProposalCommand): Unit = {
    val maybeEvent = validatePostponeCommand(command)
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
                  "proposalId",
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
                similarProposals = command.similarProposals,
                idea = command.idea,
                operation = command.operation
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
      case ProposalState(proposal, _) =>
        if (proposal.status == ProposalStatus.Archived) {
          Left(
            ValidationFailedError(
              errors = Seq(
                ValidationError(
                  "status",
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
                Seq(ValidationError("status", Some(s"Proposal ${command.proposalId.value} is already validated")))
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
                similarProposals = command.similarProposals,
                idea = command.idea,
                operation = command.operation
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
                  "status",
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
              errors = Seq(ValidationError("status", Some(s"Proposal ${command.proposalId.value} is already refused")))
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

  private def validatePostponeCommand(
    command: PostponeProposalCommand
  ): Either[ValidationFailedError, Option[ProposalEvent]] = {
    state.map {
      case ProposalState(proposal, lock) =>
        if (proposal.status == ProposalStatus.Archived) {
          Left(
            ValidationFailedError(
              errors = Seq(
                ValidationError(
                  "status",
                  Some(s"Proposal ${command.proposalId.value} is archived and cannot be postponed")
                )
              )
            )
          )
        } else if (proposal.status == ProposalStatus.Accepted || proposal.status == ProposalStatus.Refused) {
          Left(
            ValidationFailedError(
              errors = Seq(
                ValidationError(
                  "status",
                  Some(s"Proposal ${command.proposalId.value} is already moderated and cannot be postponed")
                )
              )
            )
          )
        } else if (proposal.status == ProposalStatus.Postponed) {
          Left(
            ValidationFailedError(
              errors =
                Seq(ValidationError("status", Some(s"Proposal ${command.proposalId.value} is already postponed")))
            )
          )
        } else {
          Right(
            Some(
              ProposalPostponed(
                id = command.proposalId,
                requestContext = command.requestContext,
                moderator = command.moderator
              )
            )
          )
        }

    }.getOrElse(Right(None))
  }

  override def persistenceId: String = proposalId.value

  override val applyEvent: PartialFunction[ProposalEvent, Option[ProposalState]] = {
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
            theme = e.theme.orElse(e.requestContext.currentTheme),
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
            operation = e.operation.orElse(e.requestContext.operationId),
            events = List(
              ProposalAction(
                date = e.eventDate,
                user = e.userId,
                actionType = ProposalProposeAction.name,
                arguments = Map("content" -> e.content)
              )
            )
          )
        )
      )
    case e: ProposalUpdated       => state.map(proposalState => applyProposalUpdated(proposalState, e))
    case e: ProposalAccepted      => state.map(proposalState => applyProposalAccepted(proposalState, e))
    case e: ProposalRefused       => state.map(proposalState => applyProposalRefused(proposalState, e))
    case e: ProposalPostponed     => state.map(proposalState => applyProposalPostponed(proposalState, e))
    case e: ProposalVoted         => state.map(proposalState => applyProposalVoted(proposalState, e))
    case e: ProposalUnvoted       => state.map(proposalState => applyProposalUnvoted(proposalState, e))
    case e: ProposalQualified     => state.map(proposalState => applyProposalQualified(proposalState, e))
    case e: ProposalUnqualified   => state.map(proposalState => applyProposalUnqualified(proposalState, e))
    case e: ProposalLocked        => state.map(proposalState => applyProposalLocked(proposalState, e))
    case e: SimilarProposalsAdded => state.map(proposalState => applySimilarProposalsAdded(proposalState, e))
    case _: SimilarProposalsCleared =>
      state.map(
        proposalState => proposalState.copy(proposal = proposalState.proposal.copy(similarProposals = Seq.empty))
      )
    case e: SimilarProposalRemoved =>
      state.map(
        proposalState =>
          proposalState.copy(
            proposal = proposalState.proposal
              .copy(similarProposals = proposalState.proposal.similarProposals.filter(_ != e.proposalToRemove))
        )
      )

    case e: ProposalPatched =>
      state.map(_.copy(proposal = e.proposal))
    case _ => state
  }

}

object ProposalActor {

  case class ProposalState(proposal: Proposal, lock: Option[Lock] = None) extends MakeSerializable

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

    val arguments: Map[String, String] = Map(
      "theme" -> event.theme.map(_.value).getOrElse(""),
      "tags" -> event.tags.map(_.value).mkString(", "),
      "labels" -> event.labels.map(_.value).mkString(", "),
      "idea" -> event.idea.map(_.value).getOrElse(""),
      "operation" -> event.operation.map(_.value).getOrElse("")
    ).filter {
      case (_, value) => !value.isEmpty
    }
    val moderator: UserId = event.moderator.get
    val action =
      ProposalAction(
        date = event.eventDate,
        user = moderator,
        actionType = ProposalUpdateAction.name,
        arguments = arguments
      )
    var proposal =
      state.proposal.copy(
        tags = event.tags,
        labels = event.labels,
        events = action :: state.proposal.events,
        updatedAt = Some(event.eventDate),
        similarProposals = event.similarProposals,
        idea = event.idea,
        operation = event.operation.orElse(state.proposal.creationContext.operationId)
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
    val arguments: Map[String, String] = Map(
      "theme" -> event.theme.map(_.value).getOrElse(""),
      "tags" -> event.tags.map(_.value).mkString(", "),
      "labels" -> event.labels.map(_.value).mkString(", "),
      "idea" -> event.idea.map(_.value).getOrElse(""),
      "operation" -> event.operation.map(_.value).getOrElse("")
    ).filter {
      case (_, value) => !value.isEmpty
    }
    val action =
      ProposalAction(
        date = event.eventDate,
        user = event.moderator,
        actionType = ProposalAcceptAction.name,
        arguments = arguments
      )
    var proposal =
      state.proposal.copy(
        tags = event.tags,
        labels = event.labels,
        events = action :: state.proposal.events,
        status = Accepted,
        updatedAt = Some(event.eventDate),
        similarProposals = event.similarProposals,
        idea = event.idea,
        operation = event.operation.orElse(state.proposal.creationContext.operationId)
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
    val arguments: Map[String, String] =
      Map("refusalReason" -> event.refusalReason.getOrElse("")).filter {
        case (_, value) => !value.isEmpty
      }
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

  def applyProposalPostponed(state: ProposalState, event: ProposalPostponed): ProposalState = {
    val action =
      ProposalAction(date = event.eventDate, user = event.moderator, actionType = "postpone", arguments = Map.empty)
    state.copy(
      proposal = state.proposal
        .copy(events = action :: state.proposal.events, status = Postponed, updatedAt = Some(event.eventDate)),
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
      case Some(Lock(moderatorId, _, _)) =>
        val arguments: Map[String, String] =
          Map("moderatorName" -> event.moderatorName.getOrElse("<unknown>")).filter {
            case (_, value) => !value.isEmpty
          }
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
          Map("moderatorName" -> event.moderatorName.getOrElse("<unknown>")).filter {
            case (_, value) => !value.isEmpty
          }
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

}
