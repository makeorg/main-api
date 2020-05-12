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
import akka.persistence.SnapshotOffer
import akka.util.Timeout
import org.make.api.proposal.ProposalActor._
import org.make.api.proposal.ProposalEvent._
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.sessionhistory._
import org.make.api.technical.MakePersistentActor
import org.make.api.technical.MakePersistentActor.Snapshot
import org.make.core.SprayJsonFormatters._
import org.make.core._
import org.make.core.history.HistoryActions.VoteTrust
import org.make.core.proposal.ProposalStatus.{Accepted, Postponed, Refused}
import org.make.core.proposal.QualificationKey._
import org.make.core.proposal.VoteKey._
import org.make.core.proposal.{Qualification, Vote, _}
import org.make.core.user.UserId
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.util.{Failure, Success}

class ProposalActor(sessionHistoryCoordinatorService: SessionHistoryCoordinatorService, lockDuration: FiniteDuration)
    extends MakePersistentActor(classOf[Proposal], classOf[ProposalEvent])
    with ActorLogging {

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, snapshot: ProposalState) => state = Some(snapshot.proposal)
    case other                                     => super.receiveRecover(other)
  }

  implicit val timeout: Timeout = defaultTimeout

  var lock: Option[ProposalLock] = None

  def proposalId: ProposalId = ProposalId(self.path.name)

  override def receiveCommand: Receive = {
    case _: GetProposal                      => onGetProposal()
    case command: ViewProposalCommand        => onViewProposalCommand(command)
    case command: ProposeCommand             => onProposeCommand(command)
    case command: UpdateProposalCommand      => onUpdateProposalCommand(command)
    case command: UpdateProposalVotesCommand => onUpdateProposalVotesCommand(command)
    case command: AcceptProposalCommand      => onAcceptProposalCommand(command)
    case command: RefuseProposalCommand      => onRefuseProposalCommand(command)
    case command: PostponeProposalCommand    => onPostponeProposalCommand(command)
    case command: VoteProposalCommand        => onVoteProposalCommand(command)
    case command: UnvoteProposalCommand      => onUnvoteProposalCommand(command)
    case command: QualifyVoteCommand         => onQualificationProposalCommand(command)
    case command: UnqualifyVoteCommand       => onUnqualificationProposalCommand(command)
    case command: LockProposalCommand        => onLockProposalCommand(command)
    case command: PatchProposalCommand       => onPatchProposalCommand(command)
    case command: AnonymizeProposalCommand   => onAnonymizeProposalCommand(command)
    case Snapshot                            => saveSnapshot()
    case _: SnapshotProposal                 => saveSnapshot()
    case _: KillProposalShard                => self ! PoisonPill
  }

  def getStateOrSendProposalNotFound(senderRef: ActorRef = sender())(work: Proposal => Unit): Unit = {
    state match {
      case None           => senderRef ! ProposalNotFound
      case Some(proposal) => work(proposal)
    }
  }

  def getStateOrSendVoteNotFound(voteKey: VoteKey, senderRef: ActorRef = sender())(work: Vote => Unit): Unit = {
    state.flatMap(_.votes.find(_.key == voteKey)) match {
      case None       => senderRef ! VoteNotFound
      case Some(vote) => work(vote)
    }
  }

  def getStateOrSendQualificationNotFound(
    voteKey: VoteKey,
    qualificationKey: QualificationKey,
    senderRef: ActorRef = sender()
  )(work: Qualification => Unit): Unit = {
    state.flatMap(_.votes.find(_.key == voteKey).flatMap(_.qualifications.find(_.key == qualificationKey))) match {
      case None                => senderRef ! QualificationNotFound
      case Some(qualification) => work(qualification)
    }
  }

  def onGetProposal(): Unit = getStateOrSendProposalNotFound()(sender() ! ProposalEnveloppe(_))

  def onPatchProposalCommand(command: PatchProposalCommand): Unit = {
    getStateOrSendProposalNotFound() { proposal =>
      val changes = command.changes
      val modifiedContext =
        changes.creationContext.map { contextChanges =>
          proposal.creationContext.copy(
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
      ) { _ =>
        getStateOrSendProposalNotFound()(sender() ! UpdatedProposal(_))
      }
    }
  }

  private def onVoteProposalCommand(command: VoteProposalCommand): Unit = {
    getStateOrSendProposalNotFound() { _ =>
      command.vote match {
        // User hasn't voted on proposal yet
        case None =>
          persistAndPublishEventAsync(
            ProposalVoted(
              id = proposalId,
              maybeUserId = command.maybeUserId,
              eventDate = DateHelper.now(),
              maybeOrganisationId = command.maybeOrganisationId,
              requestContext = command.requestContext,
              voteKey = command.voteKey,
              voteTrust = command.voteTrust
            )
          ) { event =>
            val originalSender = sender()
            logVoteEvent(event).onComplete {
              case Success(_) =>
                getStateOrSendVoteNotFound(command.voteKey, originalSender)(originalSender ! ProposalVote(_))
              case Failure(e) => originalSender ! VoteError(e)
            }
          }
        // User has already voted on this proposal with this vote key (e.g.: double click)
        case Some(vote) if vote.voteKey == command.voteKey =>
          getStateOrSendVoteNotFound(command.voteKey)(sender() ! ProposalVote(_))
        // User has already voted with another key. Behaviour: first, unvote previous vote then, revote with new key
        case Some(vote) =>
          val unvoteEvent = ProposalUnvoted(
            id = proposalId,
            maybeUserId = command.maybeUserId,
            eventDate = DateHelper.now(),
            requestContext = command.requestContext,
            voteKey = vote.voteKey,
            maybeOrganisationId = command.maybeOrganisationId,
            voteTrust = vote.trust,
            selectedQualifications = vote.qualificationKeys.keys.toSeq
          )
          val voteEvent = ProposalVoted(
            id = proposalId,
            maybeUserId = command.maybeUserId,
            eventDate = DateHelper.now(),
            maybeOrganisationId = command.maybeOrganisationId,
            requestContext = command.requestContext,
            voteKey = command.voteKey,
            voteTrust = command.voteTrust
          )
          persistAndPublishEventsAsync(Seq(unvoteEvent, voteEvent)) {
            case _: ProposalVoted =>
              val originalSender = sender()
              logUnvoteEvent(unvoteEvent).flatMap(_ => logVoteEvent(voteEvent)).onComplete {
                case Success(_) =>
                  getStateOrSendVoteNotFound(command.voteKey, originalSender)(originalSender ! ProposalVote(_))
                case Failure(e) => originalSender ! VoteError(e)
              }
            case _ =>
          }
      }
    }
  }

  private def logVoteEvent(event: ProposalVoted): Future[_] = {
    sessionHistoryCoordinatorService.logTransactionalHistory(
      LogSessionVoteEvent(
        sessionId = event.requestContext.sessionId,
        requestContext = event.requestContext,
        action = SessionAction(
          date = event.eventDate,
          actionType = ProposalVoteAction.name,
          SessionVote(proposalId = event.id, voteKey = event.voteKey, trust = event.voteTrust)
        )
      )
    )
  }

  private def logUnvoteEvent(event: ProposalUnvoted): Future[_] = {
    sessionHistoryCoordinatorService.logTransactionalHistory(
      LogSessionUnvoteEvent(
        sessionId = event.requestContext.sessionId,
        requestContext = event.requestContext,
        action = SessionAction(
          date = event.eventDate,
          actionType = ProposalUnvoteAction.name,
          SessionUnvote(proposalId = event.id, voteKey = event.voteKey, trust = event.voteTrust)
        )
      )
    )
  }

  private def logQualifyVoteEvent(event: ProposalQualified): Future[_] = {
    sessionHistoryCoordinatorService.logTransactionalHistory(
      LogSessionQualificationEvent(
        sessionId = event.requestContext.sessionId,
        requestContext = event.requestContext,
        action = SessionAction(
          date = event.eventDate,
          actionType = ProposalQualifyAction.name,
          SessionQualification(
            proposalId = event.id,
            qualificationKey = event.qualificationKey,
            trust = event.voteTrust
          )
        )
      )
    )
  }

  private def logRemoveVoteQualificationEvent(event: ProposalUnqualified): Future[_] = {
    sessionHistoryCoordinatorService.logTransactionalHistory(
      LogSessionUnqualificationEvent(
        sessionId = event.requestContext.sessionId,
        requestContext = event.requestContext,
        action = SessionAction(
          date = event.eventDate,
          actionType = ProposalUnqualifyAction.name,
          SessionUnqualification(
            proposalId = event.id,
            qualificationKey = event.qualificationKey,
            trust = event.voteTrust
          )
        )
      )
    )
  }

  private def onUnvoteProposalCommand(command: UnvoteProposalCommand): Unit = {
    getStateOrSendProposalNotFound() { _ =>
      command.vote match {
        // User hasn't voted on proposal yet
        case None =>
          getStateOrSendVoteNotFound(command.voteKey)(sender() ! ProposalVote(_))
        // User voted on this proposal so we unvote whatever voted key
        case Some(vote) =>
          persistAndPublishEventAsync(
            ProposalUnvoted(
              id = proposalId,
              maybeUserId = command.maybeUserId,
              eventDate = DateHelper.now(),
              requestContext = command.requestContext,
              voteKey = vote.voteKey,
              maybeOrganisationId = command.maybeOrganisationId,
              selectedQualifications = vote.qualificationKeys.keys.toSeq,
              voteTrust = vote.trust
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
                    qualificationKey = qualification,
                    voteTrust = vote.qualificationKeys.getOrElse(qualification, event.voteTrust)
                  )
                )
              }
            }.onComplete {
              case Success(_) =>
                getStateOrSendVoteNotFound(command.voteKey, originalSender)(originalSender ! ProposalVote(_))
              case Failure(e) => originalSender ! VoteError(e)
            }
          }
      }
    }
  }

  private def checkQualification(
    voteKey: VoteKey,
    commandVoteKey: VoteKey,
    qualificationKey: QualificationKey
  ): Boolean = {
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
    getStateOrSendProposalNotFound() { _ =>
      command.vote match {
        // User hasn't voted on proposal yet
        case None =>
          log.warning(s"Qualification was not increased: user hasn't voted on proposal. ${command.toString}")
          getStateOrSendQualificationNotFound(command.voteKey, command.qualificationKey) {
            sender() ! ProposalQualification(_)
          }
        // User already qualified this qualification key on this proposal (e.g.: double click)
        case Some(vote) if vote.qualificationKeys.contains(command.qualificationKey) =>
          log.warning(
            s"Qualification was not increased: user has already qualified with this value. ${command.toString}"
          )
          getStateOrSendQualificationNotFound(command.voteKey, command.qualificationKey) {
            sender() ! ProposalQualification(_)
          }
        // Qualificication doesn't belong to user's vote.
        case Some(vote) if !checkQualification(vote.voteKey, command.voteKey, command.qualificationKey) =>
          log.warning(
            s"Qualification was not increased: qualification doesn't belong to vote type. ${command.toString}"
          )
          getStateOrSendQualificationNotFound(command.voteKey, command.qualificationKey) {
            sender() ! ProposalQualification(_)
          }
        // User qualifies correctly
        case Some(vote) =>
          val resolvedTrust = if (!vote.trust.isTrusted) {
            vote.trust
          } else {
            command.voteTrust
          }
          persistAndPublishEventAsync(
            ProposalQualified(
              id = proposalId,
              maybeUserId = command.maybeUserId,
              eventDate = DateHelper.now(),
              requestContext = command.requestContext,
              voteKey = command.voteKey,
              qualificationKey = command.qualificationKey,
              voteTrust = resolvedTrust
            )
          ) { event =>
            val originalSender = sender()
            logQualifyVoteEvent(event).onComplete {
              case Success(_) =>
                getStateOrSendQualificationNotFound(command.voteKey, command.qualificationKey, originalSender) {
                  qualification =>
                    if (qualification.count == 0) {
                      log.warning(s"Qualification returned a 0 value for an unknown reason. ${command.toString}")
                    }
                    originalSender ! ProposalQualification(qualification)
                }
              case Failure(e) => QualificationError(e)
            }
          }
      }
    }
  }

  private def onUnqualificationProposalCommand(command: UnqualifyVoteCommand): Unit = {
    getStateOrSendProposalNotFound() { _ =>
      command.vote match {
        // User hasn't voted on proposal yet
        case None =>
          getStateOrSendQualificationNotFound(command.voteKey, command.qualificationKey) {
            sender() ! ProposalQualification(_)
          }
        // User has voted and qualified this proposal
        case Some(vote) if vote.qualificationKeys.contains(command.qualificationKey) =>
          persistAndPublishEventAsync(
            ProposalUnqualified(
              id = proposalId,
              maybeUserId = command.maybeUserId,
              eventDate = DateHelper.now(),
              requestContext = command.requestContext,
              voteKey = command.voteKey,
              qualificationKey = command.qualificationKey,
              voteTrust = vote.qualificationKeys.getOrElse(command.qualificationKey, command.voteTrust)
            )
          ) { event =>
            val originalSender = sender()
            logRemoveVoteQualificationEvent(event).onComplete {
              case Success(_) =>
                getStateOrSendQualificationNotFound(command.voteKey, command.qualificationKey, originalSender) {
                  originalSender ! ProposalQualification(_)
                }
              case Failure(e) => originalSender ! QualificationError(e)
            }
          }
        // User has voted on this proposal but hasn't qualified with the unqualify key
        case _ =>
          getStateOrSendQualificationNotFound(command.voteKey, command.qualificationKey) {
            sender() ! ProposalQualification(_)
          }
      }
    }

  }

  private def onViewProposalCommand(command: ViewProposalCommand): Unit = {
    persistAndPublishEvent(
      ProposalViewed(id = proposalId, eventDate = DateHelper.now(), requestContext = command.requestContext)
    ) { _ =>
      getStateOrSendProposalNotFound()(sender() ! ProposalEnveloppe(_))
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
        language = Some(command.question.language),
        country = Some(command.question.country),
        question = Some(command.question.questionId),
        initialProposal = command.initialProposal
      )
    ) { _ =>
      sender() ! CreatedProposalId(proposalId)
    }

  }

  private def onUpdateProposalCommand(command: UpdateProposalCommand): Unit = {
    getStateOrSendProposalNotFound() {
      case proposal if proposal.status != ProposalStatus.Accepted =>
        sender() ! InvalidStateError(s"Proposal ${command.proposalId.value} is not accepted and cannot be updated")
      case proposal =>
        val event =
          ProposalUpdated(
            id = proposalId,
            eventDate = DateHelper.now(),
            requestContext = command.requestContext,
            updatedAt = command.updatedAt,
            moderator = Some(command.moderator),
            edition = command.newContent.map { newContent =>
              ProposalEdition(proposal.content, newContent)
            },
            labels = command.labels,
            tags = command.tags,
            similarProposals = Seq.empty,
            idea = command.idea,
            operation = command.question.operationId.orElse(proposal.operation),
            question = Some(command.question.questionId)
          )
        persistAndPublishEvent(event) { _ =>
          getStateOrSendProposalNotFound()(sender() ! UpdatedProposal(_))
        }
    }
  }

  private def onUpdateProposalVotesCommand(command: UpdateProposalVotesCommand): Unit = {
    getStateOrSendProposalNotFound() {
      case proposal if proposal.status != ProposalStatus.Accepted =>
        sender() ! InvalidStateError(s"Proposal ${command.proposalId.value} is not accepted and cannot be updated")
      case proposal =>
        val event = ProposalVotesUpdated(
          id = proposalId,
          eventDate = DateHelper.now(),
          requestContext = command.requestContext,
          updatedAt = command.updatedAt,
          moderator = Some(command.moderator),
          newVotes = mergeVotes(proposal.votes, command.votes)
        )
        persistAndPublishEvent(event) { _ =>
          getStateOrSendProposalNotFound()(sender() ! UpdatedProposal(_))
        }
    }
  }

  private def onAcceptProposalCommand(command: AcceptProposalCommand): Unit = {
    getStateOrSendProposalNotFound() {
      case proposal if proposal.status == ProposalStatus.Archived =>
        sender() ! InvalidStateError(s"Proposal ${command.proposalId.value} is archived and cannot be validated")
      case proposal if proposal.status == ProposalStatus.Accepted =>
        // possible double request, ignore.
        // other modifications should use the proposal update method
        sender() ! InvalidStateError(s"Proposal ${command.proposalId.value} is already validated")
      case proposal =>
        val event = ProposalAccepted(
          id = command.proposalId,
          eventDate = DateHelper.now(),
          requestContext = command.requestContext,
          moderator = command.moderator,
          edition = command.newContent.map { newContent =>
            ProposalEdition(proposal.content, newContent)
          },
          sendValidationEmail = command.sendNotificationEmail,
          theme = None,
          labels = command.labels,
          tags = command.tags,
          similarProposals = Seq.empty,
          idea = command.idea,
          operation = command.question.operationId.orElse(proposal.operation),
          question = Some(command.question.questionId)
        )
        persistAndPublishEvent(event) { _ =>
          getStateOrSendProposalNotFound()(sender() ! ModeratedProposal(_))
        }
    }
  }

  private def onRefuseProposalCommand(command: RefuseProposalCommand): Unit = {
    getStateOrSendProposalNotFound() {
      case proposal if proposal.status == ProposalStatus.Archived =>
        sender() ! InvalidStateError(s"Proposal ${command.proposalId.value} is archived and cannot be refused")
      case proposal if proposal.status == ProposalStatus.Refused =>
        // possible double request, ignore.
        // other modifications should use the proposal update command
        sender() ! InvalidStateError(s"Proposal ${command.proposalId.value} is already refused")
      case proposal =>
        val event = ProposalRefused(
          id = command.proposalId,
          eventDate = DateHelper.now(),
          requestContext = command.requestContext,
          moderator = command.moderator,
          sendRefuseEmail = command.sendNotificationEmail,
          refusalReason = command.refusalReason,
          operation = proposal.operation
        )
        persistAndPublishEvent(event) { _ =>
          getStateOrSendProposalNotFound()(sender() ! ModeratedProposal(_))
        }
    }
  }

  private def onPostponeProposalCommand(command: PostponeProposalCommand): Unit = {
    getStateOrSendProposalNotFound() {
      case proposal if proposal.status == ProposalStatus.Archived =>
        sender() ! InvalidStateError(s"Proposal ${command.proposalId.value} is archived and cannot be postponed")
      case proposal if proposal.status == ProposalStatus.Accepted || proposal.status == ProposalStatus.Refused =>
        sender() ! InvalidStateError(
          s"Proposal ${command.proposalId.value} is already moderated and cannot be postponed"
        )
      case proposal if proposal.status == ProposalStatus.Postponed =>
        sender() ! InvalidStateError(s"Proposal ${command.proposalId.value} is already postponed")
      case _ =>
        val event =
          ProposalPostponed(
            id = command.proposalId,
            requestContext = command.requestContext,
            moderator = command.moderator,
            eventDate = DateHelper.now()
          )
        persistAndPublishEvent(event) { _ =>
          getStateOrSendProposalNotFound()(sender() ! ModeratedProposal(_))
        }
    }
  }

  private def onLockProposalCommand(command: LockProposalCommand): Unit = {
    getStateOrSendProposalNotFound() { _ =>
      lock match {
        case Some(ProposalLock(moderatorId, moderatorName, deadline))
            if moderatorId != command.moderatorId && deadline.hasTimeLeft =>
          sender() ! AlreadyLockedBy(moderatorName)

        case maybeLock if maybeLock.forall(_.deadline.isOverdue()) =>
          persistAndPublishEvent(
            ProposalLocked(
              id = proposalId,
              moderatorId = command.moderatorId,
              moderatorName = command.moderatorName,
              eventDate = DateHelper.now(),
              requestContext = command.requestContext
            )
          ) { event =>
            lock =
              Some(ProposalLock(event.moderatorId, event.moderatorName.getOrElse("<unknown>"), lockDuration.fromNow))
            sender() ! Locked(event.moderatorId)
          }
        case _ =>
          lock =
            Some(ProposalLock(command.moderatorId, command.moderatorName.getOrElse("<unknown>"), lockDuration.fromNow))
          sender() ! Locked(command.moderatorId)
      }
    }
  }

  private def mergeQualifications(
    proposalQualifications: Seq[Qualification],
    commandQualification: Seq[UpdateQualificationRequest]
  ): Seq[Qualification] = {
    val commandQualificationsAsMap: Map[QualificationKey, UpdateQualificationRequest] =
      commandQualification.map(qualification => qualification.key -> qualification).toMap

    proposalQualifications.map { qualification =>
      commandQualificationsAsMap.get(qualification.key) match {
        case None => qualification
        case Some(updatedQualification) =>
          qualification.copy(
            count = updatedQualification.count.getOrElse(qualification.count),
            countVerified = updatedQualification.countVerified.getOrElse(qualification.countVerified),
            countSequence = updatedQualification.countSequence.getOrElse(qualification.countSequence),
            countSegment = updatedQualification.countSegment.getOrElse(qualification.countSegment)
          )
      }

    }
  }

  private def mergeVotes(proposalVotes: Seq[Vote], commandVotes: Seq[UpdateVoteRequest]): Seq[Vote] = {
    val commandVotesAsMap: Map[VoteKey, UpdateVoteRequest] = commandVotes.map(vote => vote.key -> vote).toMap
    proposalVotes.map { vote =>
      commandVotesAsMap.get(vote.key) match {
        case None => vote
        case Some(updatedVote) =>
          vote.copy(
            count = updatedVote.count.getOrElse(vote.count),
            countVerified = updatedVote.countVerified.getOrElse(vote.countVerified),
            countSequence = updatedVote.countSequence.getOrElse(vote.countSequence),
            countSegment = updatedVote.countSegment.getOrElse(vote.countSegment),
            qualifications = mergeQualifications(vote.qualifications, updatedVote.qualifications)
          )
      }
    }
  }

  def onAnonymizeProposalCommand(command: AnonymizeProposalCommand): Unit = {
    persistAndPublishEvent(
      ProposalAnonymized(command.proposalId, eventDate = DateHelper.now(), requestContext = command.requestContext)
    )(_ => ())
  }

  override def persistenceId: String = proposalId.value

  override val applyEvent: PartialFunction[ProposalEvent, Option[Proposal]] = {
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
          questionId = e.question,
          creationContext = e.requestContext,
          labels = Seq.empty,
          votes = Seq(
            Vote(
              key = Agree,
              count = 0,
              countVerified = 0,
              countSequence = 0,
              countSegment = 0,
              qualifications = Seq(
                Qualification(key = LikeIt, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
                Qualification(key = Doable, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
                Qualification(key = PlatitudeAgree, count = 0, countVerified = 0, countSequence = 0, countSegment = 0)
              )
            ),
            Vote(
              key = Disagree,
              count = 0,
              countVerified = 0,
              countSequence = 0,
              countSegment = 0,
              qualifications = Seq(
                Qualification(key = NoWay, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
                Qualification(key = Impossible, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
                Qualification(
                  key = PlatitudeDisagree,
                  count = 0,
                  countVerified = 0,
                  countSequence = 0,
                  countSegment = 0
                )
              )
            ),
            Vote(
              key = Neutral,
              count = 0,
              countVerified = 0,
              countSequence = 0,
              countSegment = 0,
              qualifications = Seq(
                Qualification(key = DoNotUnderstand, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
                Qualification(key = NoOpinion, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
                Qualification(key = DoNotCare, count = 0, countVerified = 0, countSequence = 0, countSegment = 0)
              )
            )
          ),
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
          country = e.country,
          initialProposal = e.initialProposal
        )
      )
    case e: ProposalUpdated              => state.map(applyProposalUpdated(_, e))
    case e: ProposalVotesVerifiedUpdated => state.map(applyProposalVotesVerifiedUpdated(_, e))
    case e: ProposalVotesUpdated         => state.map(applyProposalVotesUpdated(_, e))
    case e: ProposalAccepted             => state.map(applyProposalAccepted(_, e))
    case e: ProposalRefused              => state.map(applyProposalRefused(_, e))
    case e: ProposalPostponed            => state.map(applyProposalPostponed(_, e))
    case e: ProposalVoted                => state.map(applyProposalVoted(_, e))
    case e: ProposalUnvoted              => state.map(applyProposalUnvoted(_, e))
    case e: ProposalQualified            => state.map(applyProposalQualified(_, e))
    case e: ProposalUnqualified          => state.map(applyProposalUnqualified(_, e))
    case e: ProposalLocked               => state.map(applyProposalLocked(_, e))
    case e: ProposalPatched              => Some(e.proposal)
    case _: DeprecatedEvent              => state
    case _: ProposalAnonymized =>
      state.map(
        _.copy(content = "DELETE_REQUESTED", slug = "delete-requested", refusalReason = Some("other"), status = Refused)
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

  @Deprecated
  case class Lock(moderatorId: UserId, moderatorName: String, expirationDate: ZonedDateTime)

  object Lock {
    implicit val lockFormatter: RootJsonFormat[Lock] =
      DefaultJsonProtocol.jsonFormat3(Lock.apply)
  }

  case class ProposalLock(moderatorId: UserId, moderatorName: String, deadline: Deadline)

  def applyProposalUpdated(state: Proposal, event: ProposalUpdated): Proposal = {

    val arguments: Map[String, String] = Map(
      "question" -> event.question.map(_.value).getOrElse(""),
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
    val proposal =
      state.copy(
        tags = event.tags,
        labels = event.labels,
        events = action :: state.events,
        updatedAt = Some(event.eventDate),
        idea = event.idea,
        operation = event.operation,
        questionId = event.question
      )

    event.edition match {
      case None                                 => proposal
      case Some(ProposalEdition(_, newVersion)) => proposal.copy(content = newVersion, slug = SlugHelper(newVersion))
    }
  }

  def applyProposalVotesVerifiedUpdated(state: Proposal, event: ProposalVotesVerifiedUpdated): Proposal = {
    val votes = state.votes.map { vote =>
      val qualifications = vote.qualifications.map { qualification =>
        Qualification(
          qualification.key,
          count = qualification.count,
          countSequence = qualification.countSequence,
          countSegment = qualification.countSegment,
          countVerified = event.votesVerified
            .filter(_.key == vote.key)
            .flatMap(_.qualifications.filter(_.key == qualification.key).map(_.countVerified))
            .sum
        )
      }
      Vote(
        key = vote.key,
        count = vote.count,
        countSequence = vote.countSequence,
        countSegment = vote.countSegment,
        countVerified = event.votesVerified.filter(_.key == vote.key).map(_.countVerified).sum,
        qualifications = qualifications
      )
    }
    state.copy(votes = votes)
  }

  def applyProposalVotesUpdated(state: Proposal, event: ProposalVotesUpdated): Proposal = {
    state.copy(votes = event.newVotes)
  }

  def applyProposalAccepted(state: Proposal, event: ProposalAccepted): Proposal = {
    val arguments: Map[String, String] = Map(
      "question" -> event.question.map(_.value).getOrElse(""),
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
    val proposal =
      state.copy(
        tags = event.tags,
        labels = event.labels,
        events = action :: state.events,
        status = Accepted,
        updatedAt = Some(event.eventDate),
        idea = event.idea,
        operation = event.operation,
        questionId = event.question
      )

    event.edition match {
      case None                                 => proposal
      case Some(ProposalEdition(_, newVersion)) => proposal.copy(content = newVersion, slug = SlugHelper(newVersion))
    }
  }

  def applyProposalRefused(state: Proposal, event: ProposalRefused): Proposal = {
    val arguments: Map[String, String] =
      Map("refusalReason" -> event.refusalReason.getOrElse("")).filter {
        case (_, value) => !value.isEmpty
      }
    val action =
      ProposalAction(date = event.eventDate, user = event.moderator, actionType = "refuse", arguments = arguments)
    state.copy(
      events = action :: state.events,
      status = Refused,
      refusalReason = event.refusalReason,
      updatedAt = Some(event.eventDate)
    )
  }

  def applyProposalPostponed(state: Proposal, event: ProposalPostponed): Proposal = {
    val action =
      ProposalAction(date = event.eventDate, user = event.moderator, actionType = "postpone", arguments = Map.empty)
    state.copy(events = action :: state.events, status = Postponed, updatedAt = Some(event.eventDate))
  }

  def applyProposalVoted(state: Proposal, event: ProposalVoted): Proposal = {
    state.copy(
      votes = state.votes.map {
        case vote if vote.key == event.voteKey =>
          var voteIncreased = vote.copy(count = vote.count + 1)
          if (event.voteTrust.isInSegment) {
            voteIncreased = voteIncreased.copy(countSegment = voteIncreased.countSegment + 1)
          }
          if (event.voteTrust.isInSequence) {
            voteIncreased = voteIncreased.copy(countSequence = voteIncreased.countSequence + 1)
          }
          if (event.voteTrust.isTrusted) {
            voteIncreased = voteIncreased.copy(countVerified = vote.countVerified + 1)
          }
          voteIncreased

        case vote => vote
      },
      organisationIds = event.maybeOrganisationId match {
        case Some(organisationId)
            if !state.organisationIds
              .exists(_.value == organisationId.value) =>
          state.organisationIds :+ organisationId
        case _ => state.organisationIds
      }
    )
  }

  def applyProposalUnvoted(state: Proposal, event: ProposalUnvoted): Proposal = {
    state.copy(
      votes = state.votes.map {
        case vote if vote.key == event.voteKey =>
          var voteDecreased = vote.copy(count = vote.count - 1)
          if (event.voteTrust.isInSegment) {
            voteDecreased = voteDecreased.copy(countSegment = voteDecreased.countSegment - 1)
          }
          if (event.voteTrust.isInSequence) {
            voteDecreased = voteDecreased.copy(countSequence = voteDecreased.countSequence - 1)
          }
          if (event.voteTrust.isTrusted) {
            voteDecreased = voteDecreased.copy(countVerified = vote.countVerified - 1)
          }

          voteDecreased.copy(qualifications = voteDecreased.qualifications
            .map(qualification => applyUnqualifVote(qualification, event.selectedQualifications, event.voteTrust))
          )
        case vote => vote
      },
      organisationIds = event.maybeOrganisationId match {
        case Some(organisationId) =>
          state.organisationIds.filterNot(_.value == organisationId.value)
        case _ => state.organisationIds
      }
    )
  }

  def applyUnqualifVote(
    qualification: Qualification,
    selectedQualifications: Seq[QualificationKey],
    voteTrust: VoteTrust
  ): Qualification = {
    if (selectedQualifications.contains(qualification.key)) {
      var qualificationDecreased = qualification.copy(count = qualification.count - 1)
      if (voteTrust.isTrusted) {
        qualificationDecreased = qualificationDecreased.copy(countVerified = qualification.countVerified - 1)
      }
      if (voteTrust.isInSequence) {
        qualificationDecreased = qualificationDecreased.copy(countSequence = qualification.countSequence - 1)
      }
      if (voteTrust.isInSegment) {
        qualificationDecreased = qualificationDecreased.copy(countSegment = qualification.countSegment - 1)
      }
      qualificationDecreased

    } else {
      qualification
    }
  }

  def applyProposalQualified(state: Proposal, event: ProposalQualified): Proposal = {
    state.copy(votes = state.votes.map {
      case vote if vote.key == event.voteKey =>
        vote.copy(qualifications = vote.qualifications.map {
          case qualification if qualification.key == event.qualificationKey =>
            var qualificationIncreased = qualification.copy(count = qualification.count + 1)
            if (event.voteTrust.isTrusted) {
              qualificationIncreased = qualificationIncreased.copy(countVerified = qualification.countVerified + 1)
            }
            if (event.voteTrust.isInSequence) {
              qualificationIncreased = qualificationIncreased.copy(countSequence = qualification.countSequence + 1)
            }
            if (event.voteTrust.isInSegment) {
              qualificationIncreased = qualificationIncreased.copy(countSegment = qualification.countSegment + 1)
            }
            qualificationIncreased

          case qualification => qualification
        })
      case vote => vote
    })
  }

  def applyProposalUnqualified(state: Proposal, event: ProposalUnqualified): Proposal = {
    state.copy(votes = state.votes.map {
      case vote if vote.key == event.voteKey =>
        vote.copy(qualifications =
          vote.qualifications.map(applyUnqualifVote(_, Seq(event.qualificationKey), event.voteTrust))
        )
      case vote => vote
    })
  }

  def applyProposalLocked(state: Proposal, event: ProposalLocked): Proposal = {
    val arguments: Map[String, String] =
      Map("moderatorName" -> event.moderatorName.getOrElse("<unknown>")).filter {
        case (_, value) => !value.isEmpty
      }
    val action =
      ProposalAction(date = event.eventDate, user = event.moderatorId, actionType = "lock", arguments = arguments)
    state.copy(events = action :: state.events, updatedAt = Some(event.eventDate))
  }

}
