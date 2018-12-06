/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.proposal

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}

import akka.actor.{ActorLogging, ActorRef, PoisonPill}
import akka.pattern.ask
import akka.persistence.SnapshotOffer
import akka.util.Timeout
import org.make.api.proposal.ProposalActor.Lock._
import org.make.api.proposal.ProposalActor._
import org.make.api.proposal.ProposalEvent._
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.sessionhistory._
import org.make.api.technical.MakePersistentActor
import org.make.api.technical.MakePersistentActor.Snapshot
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

class ProposalActor(sessionHistoryActor: ActorRef)
    extends MakePersistentActor(classOf[ProposalState], classOf[ProposalEvent])
    with ActorLogging {

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, snapshot: Proposal) => state = Some(ProposalState(snapshot))
    case other                                => super.receiveRecover(other)
  }

  implicit val timeout: Timeout = defaultTimeout

  def proposalId: ProposalId = ProposalId(self.path.name)

  override def receiveCommand: Receive = {
    case _: GetProposal                    => sender() ! state.map(_.proposal)
    case command: ViewProposalCommand      => onViewProposalCommand(command)
    case command: ProposeCommand           => onProposeCommand(command)
    case command: UpdateProposalCommand    => onUpdateProposalCommand(command)
    case command: AcceptProposalCommand    => onAcceptProposalCommand(command)
    case command: RefuseProposalCommand    => onRefuseProposalCommand(command)
    case command: PostponeProposalCommand  => onPostponeProposalCommand(command)
    case command: VoteProposalCommand      => onVoteProposalCommand(command)
    case command: UnvoteProposalCommand    => onUnvoteProposalCommand(command)
    case command: QualifyVoteCommand       => onQualificationProposalCommand(command)
    case command: UnqualifyVoteCommand     => onUnqualificationProposalCommand(command)
    case command: LockProposalCommand      => onLockProposalCommand(command)
    case command: PatchProposalCommand     => onPatchProposalCommand(command)
    case command: AnonymizeProposalCommand => onAnonymizeProposalCommand(command)
    case Snapshot                          => saveSnapshot()
    case _: KillProposalShard              => self ! PoisonPill
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
            visitorId = contextChanges.visitorId.orElse(proposal.creationContext.visitorId),
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
          operation = changes.operation.orElse(proposal.operation),
          language = changes.language.orElse(proposal.language),
          country = changes.country.orElse(proposal.country),
          questionId = changes.questionId.orElse(proposal.questionId)
        )

      persistAndPublishEvent(
        ProposalPatched(
          id = command.proposalId,
          requestContext = command.requestContext,
          proposal = modifiedProposal,
          eventDate = DateHelper.now()
        )
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

  private def onVoteProposalCommand(command: VoteProposalCommand): Unit = {
    command.vote match {
      case None =>
        persistAndPublishEvent(
          ProposalVoted(
            id = proposalId,
            maybeUserId = command.maybeUserId,
            eventDate = DateHelper.now(),
            maybeOrganisationId = command.maybeOrganisationId,
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
          maybeOrganisationId = command.maybeOrganisationId,
          selectedQualifications = vote.qualificationKeys
        )
        val voteEvent = ProposalVoted(
          id = proposalId,
          maybeUserId = command.maybeUserId,
          eventDate = DateHelper.now(),
          maybeOrganisationId = command.maybeOrganisationId,
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

  private def logUnvoteEvent(event: ProposalUnvoted): Future[_] = {
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

  private def logQualifyVoteEvent(event: ProposalQualified): Future[_] = {
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

  private def logRemoveVoteQualificationEvent(event: ProposalUnqualified): Future[_] = {
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
            maybeOrganisationId = command.maybeOrganisationId,
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
        val maybeQualification = state
          .flatMap(_.proposal.votes.find(_.key == command.voteKey))
          .flatMap(_.qualifications.find(_.key == command.qualificationKey))
        if (maybeQualification.exists(_.count == 0)) {
          log.warning(s"Qualification returned a 0 value [1]. ${command.toString}")
        }
        sender() ! Right(maybeQualification)
      case Some(vote) if vote.qualificationKeys.contains(command.qualificationKey) =>
        val maybeQualification = state
          .flatMap(_.proposal.votes.find(_.key == command.voteKey))
          .flatMap(_.qualifications.find(_.key == command.qualificationKey))
        if (maybeQualification.exists(_.count == 0)) {
          log.warning(s"Qualification returned a 0 value [2]. ${command.toString}")
        }
        sender() ! Right(maybeQualification)
      case Some(vote) if !checkQualification(vote.voteKey, command.voteKey, command.qualificationKey) =>
        val maybeQualification = state
          .flatMap(_.proposal.votes.find(_.key == command.voteKey))
          .flatMap(_.qualifications.find(_.key == command.qualificationKey))
        if (maybeQualification.exists(_.count == 0)) {
          log.warning(s"Qualification returned a 0 value [3]. ${command.toString}")
        }
        sender() ! Right(maybeQualification)
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
              val maybeQualification = state
                .flatMap(_.proposal.votes.find(_.key == command.voteKey))
                .flatMap(_.qualifications.find(_.key == command.qualificationKey))
              originalSender ! Right(maybeQualification)
              if (maybeQualification.exists(_.count == 0)) {
                log.warning(s"Qualification returned a 0 value [4]. ${command.toString}")
              }
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
          user.organisationName.orElse(user.firstName),
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
        operation = command.question.operationId,
        theme = command.question.themeId,
        language = Some(command.question.language),
        country = Some(command.question.country),
        question = Some(command.question.questionId)
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
      ProposalAddedToOperation(
        proposalId,
        operation,
        moderatorId,
        requestContext = requestContext,
        eventDate = DateHelper.now()
      )
    ) { _ =>
      ()
    }
  }

  private def removeFromOperation(proposalId: ProposalId,
                                  operation: OperationId,
                                  requestContext: RequestContext,
                                  moderatorId: UserId): Unit = {
    persistAndPublishEvent(
      ProposalRemovedFromOperation(
        proposalId,
        operation,
        moderatorId,
        requestContext = requestContext,
        eventDate = DateHelper.now()
      )
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
          if (!proposal.flatMap(_.questionId).contains(command.question.questionId)) {
            changeSequence(
              command.proposalId,
              currentOperation = proposal.flatMap(_.operation),
              newOperation = command.question.operationId,
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
        persistAndPublishEvent(event) {
          case e: ProposalAccepted =>
            e.operation.foreach { operationId =>
              addToNewOperation(e.id, operationId, e.requestContext, e.moderator)
            }
            sender() ! state.map(_.proposal)
          case _ => sender() ! state.map(_.proposal)
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
      case ProposalState(proposal, _) =>
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
                theme = command.question.themeId,
                labels = command.labels,
                tags = command.tags,
                similarProposals = Seq.empty,
                idea = command.idea,
                operation = command.question.operationId.orElse(proposal.operation),
                question = Some(command.question.questionId)
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
                theme = command.question.themeId,
                labels = command.labels,
                tags = command.tags,
                similarProposals = Seq.empty,
                idea = command.idea,
                operation = command.question.operationId.orElse(proposal.operation),
                question = Some(command.question.questionId)
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
      case ProposalState(proposal, _) =>
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
                refusalReason = command.refusalReason,
                operation = proposal.operation
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
      case ProposalState(proposal, _) =>
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
                moderator = command.moderator,
                eventDate = DateHelper.now()
              )
            )
          )
        }

    }.getOrElse(Right(None))
  }

  def onAnonymizeProposalCommand(command: AnonymizeProposalCommand): Unit = {
    persistAndPublishEvent(ProposalAnonymized(command.proposalId, eventDate = DateHelper.now()))(_ => ())
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
            questionId = e.question,
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
            operation = e.operation,
            events = List(
              ProposalAction(
                date = e.eventDate,
                user = e.userId,
                actionType = ProposalProposeAction.name,
                arguments = Map("content" -> e.content)
              )
            ),
            language = e.language,
            country = e.country
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
    case _: ProposalAnonymized =>
      state.map(
        state =>
          state.copy(
            proposal = state.proposal
              .copy(
                content = "DELETE_REQUESTED",
                slug = "delete-requested",
                refusalReason = Some("other"),
                status = Refused
              )
        )
      )
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
      "question" -> event.question.map(_.value).getOrElse(""),
      "theme" -> event.theme.map(_.value).getOrElse(""),
      "tags" -> event.tags.map(_.value).mkString(", "),
      "labels" -> event.labels.map(_.value).mkString(", "),
      "idea" -> event.idea.map(_.value).getOrElse(""),
      "operation" -> event.operation.map(_.value).getOrElse("")
    ).filter {
      case (_, value) => !value.isEmpty
    }
    val moderator: UserId = event.moderator match {
      case Some(userId) => userId
      case _            => throw new IllegalStateException("moderator required")
    }
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
        operation = event.operation,
        questionId = event.question
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
      "question" -> event.question.map(_.value).getOrElse(""),
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
        operation = event.operation,
        questionId = event.question
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
    state.copy(
      proposal = state.proposal.copy(
        votes = state.proposal.votes.map {
          case vote if vote.key == event.voteKey =>
            vote.copy(count = vote.count + 1)
          case vote => vote
        },
        organisationIds = event.maybeOrganisationId match {
          case Some(organisationId)
              if !state.proposal.organisationIds
                .exists(_.value == organisationId.value) =>
            state.proposal.organisationIds :+ organisationId
          case _ => state.proposal.organisationIds
        }
      )
    )
  }

  def applyProposalUnvoted(state: ProposalState, event: ProposalUnvoted): ProposalState = {
    state.copy(
      proposal = state.proposal.copy(
        votes = state.proposal.votes.map {
          case vote if vote.key == event.voteKey =>
            vote.copy(
              count = vote.count - 1,
              qualifications =
                vote.qualifications.map(qualification => applyUnqualifVote(qualification, event.selectedQualifications))
            )
          case vote => vote
        },
        organisationIds = event.maybeOrganisationId match {
          case Some(organisationId) =>
            state.proposal.organisationIds.filterNot(_.value == organisationId.value)
          case _ => state.proposal.organisationIds
        }
      )
    )
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
