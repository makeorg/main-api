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
import java.time.{LocalDate, ZoneOffset}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.{PersistenceId, SnapshotAdapter}
import akka.persistence.typed.scaladsl.{Effect, EffectBuilder, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import grizzled.slf4j.Logging
import org.make.api.proposal.ProposalEvent._
import org.make.api.proposal.ProposalActorResponse._
import org.make.api.proposal.ProposalActorResponse.Error._
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.sessionhistory._
import org.make.core._
import org.make.core.history.HistoryActions.VoteTrust
import org.make.core.proposal.ProposalStatus.{Accepted, Postponed, Refused}
import org.make.core.proposal.ProposalActionType._
import org.make.core.proposal.QualificationKey._
import org.make.core.proposal.VoteKey._
import org.make.core.proposal.{Qualification, Vote, _}
import org.make.core.technical.IdGenerator
import org.make.core.user.UserId
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.util.{Failure, Success}

object ProposalActor extends Logging {

  val JournalPluginId: String = "make-api.event-sourcing.proposals.journal"
  val SnapshotPluginId: String = "make-api.event-sourcing.proposals.snapshot"
  val QueryJournalPluginId: String = "make-api.event-sourcing.proposals.query"

  final case class State(proposal: Option[Proposal]) extends MakeSerializable {

    def getProposal: ProposalActorResponse[ProposalNotFound, Proposal] = proposal match {
      case Some(proposal) => Envelope(proposal)
      case _              => ProposalNotFound
    }

    def getVote(key: VoteKey): ProposalActorResponse[VoteError, Vote] =
      getProposal.flatMap(_.votes.find(_.key == key) match {
        case Some(vote) => Envelope(vote)
        case _          => VoteNotFound
      })

    def getQualification(
      voteKey: VoteKey,
      qualificationKey: QualificationKey
    ): ProposalActorResponse[QualificationError, Qualification] =
      getVote(voteKey).flatMap(_.qualifications.find(_.key == qualificationKey) match {
        case Some(qualification) => Envelope(qualification)
        case _                   => QualificationNotFound
      })

  }

  object State {
    implicit val stateFormatter: RootJsonFormat[State] = DefaultJsonProtocol.jsonFormat1(State.apply)
  }

  final case class ProposalLock(moderatorId: UserId, moderatorName: String, deadline: Deadline)

  class LockHandler(duration: FiniteDuration) {
    var lock: Option[ProposalLock] = None
    def take(id: UserId, name: Option[String]): Unit = {
      lock = Some(ProposalLock(id, name.getOrElse("<unknown>"), duration.fromNow))
    }
  }

  def apply(
    sessionHistoryCoordinatorService: SessionHistoryCoordinatorService,
    lockDuration: FiniteDuration,
    idGenerator: IdGenerator
  ): Behavior[ProposalCommand] = {

    Behaviors.setup { implicit context =>
      def commandHandler(lockHandler: LockHandler): (State, ProposalCommand) => ReplyEffect[ProposalEvent, State] = {
        case (state, GetProposal(_, _, replyTo)) => Effect.reply(replyTo)(state.getProposal)
        case (_, ViewProposalCommand(proposalId, requestContext, replyTo)) =>
          onViewProposalCommand(proposalId, requestContext, replyTo)
        case (_, command: ProposeCommand)                 => onProposeCommand(command)
        case (state, command: UpdateProposalCommand)      => onUpdateProposalCommand(state, command)
        case (state, command: UpdateProposalVotesCommand) => onUpdateProposalVotesCommand(state, command)
        case (state, command: AcceptProposalCommand)      => onAcceptProposalCommand(state, command)
        case (state, command: RefuseProposalCommand)      => onRefuseProposalCommand(state, command)
        case (state, command: PostponeProposalCommand)    => onPostponeProposalCommand(state, command)
        case (state, command: VoteProposalCommand)        => onVoteProposalCommand(state, command)
        case (state, command: UnvoteProposalCommand)      => onUnvoteProposalCommand(state, command)
        case (state, command: QualifyVoteCommand)         => onQualificationProposalCommand(state, command)
        case (state, command: UnqualifyVoteCommand)       => onUnqualificationProposalCommand(state, command)
        case (state, command: LockProposalCommand)        => onLockProposalCommand(state, command, lockHandler)
        case (state, command: PatchProposalCommand)       => onPatchProposalCommand(state, command)
        case (_, command: SetKeywordsCommand)             => onSetKeywordsCommand(command)
        case (_, Stop(_, _, replyTo))                     => Effect.stop().thenReply(replyTo)(_ => Envelope(()))
      }

      def onPatchProposalCommand(state: State, command: PatchProposalCommand): ReplyEffect[ProposalEvent, State] = {
        state.proposal match {
          case None => Effect.reply(command.replyTo)(ProposalNotFound)
          case Some(proposal) =>
            val changes = command.changes
            val modifiedContext =
              changes.creationContext.map { contextChanges =>
                proposal.creationContext.copy(
                  requestId = contextChanges.requestId.getOrElse(proposal.creationContext.requestId),
                  sessionId = contextChanges.sessionId.getOrElse(proposal.creationContext.sessionId),
                  visitorId = contextChanges.visitorId.orElse(proposal.creationContext.visitorId),
                  visitorCreatedAt = contextChanges.visitorCreatedAt.orElse(proposal.creationContext.visitorCreatedAt),
                  externalId = contextChanges.externalId.getOrElse(proposal.creationContext.externalId),
                  country = contextChanges.country.orElse(proposal.creationContext.country),
                  detectedCountry = contextChanges.detectedCountry.orElse(proposal.creationContext.detectedCountry),
                  language = contextChanges.language.orElse(proposal.creationContext.language),
                  operationId = contextChanges.operation.orElse(proposal.creationContext.operationId),
                  source = contextChanges.source.orElse(proposal.creationContext.source),
                  location = contextChanges.location.orElse(proposal.creationContext.location),
                  question = contextChanges.question.orElse(proposal.creationContext.question),
                  hostname = contextChanges.hostname.orElse(proposal.creationContext.hostname),
                  ipAddress = contextChanges.ipAddress.orElse(proposal.creationContext.ipAddress),
                  ipAddressHash = contextChanges.ipAddressHash.orElse(proposal.creationContext.ipAddressHash),
                  getParameters = contextChanges.getParameters.orElse(proposal.creationContext.getParameters),
                  userAgent = contextChanges.userAgent.orElse(proposal.creationContext.userAgent),
                  questionId = contextChanges.questionId.orElse(proposal.creationContext.questionId),
                  applicationName = contextChanges.applicationName.orElse(proposal.creationContext.applicationName),
                  referrer = contextChanges.referrer.orElse(proposal.creationContext.referrer),
                  customData = contextChanges.customData.getOrElse(proposal.creationContext.customData)
                )
              }.getOrElse(proposal.creationContext)

            val modifiedProposal =
              proposal.copy(
                slug = changes.slug.getOrElse(proposal.slug),
                content = changes.content.getOrElse(proposal.content),
                author = changes.author.getOrElse(proposal.author),
                labels = changes.labels.getOrElse(proposal.labels),
                status = changes.status.getOrElse(proposal.status),
                refusalReason = changes.refusalReason.orElse(proposal.refusalReason),
                tags = changes.tags.getOrElse(proposal.tags),
                questionId = changes.questionId.orElse(proposal.questionId),
                creationContext = modifiedContext,
                idea = changes.ideaId.orElse(proposal.idea),
                operation = changes.operation.orElse(proposal.operation),
                updatedAt = Some(DateHelper.now()),
                initialProposal = changes.initialProposal.getOrElse(proposal.initialProposal),
                keywords = changes.keywords.getOrElse(proposal.keywords)
              )

            val event = ProposalPatched(
              id = command.proposalId,
              requestContext = command.requestContext,
              proposal = modifiedProposal,
              eventDate = DateHelper.now(),
              eventId = Some(idGenerator.nextEventId())
            )
            Effect
              .persist[ProposalPatched, State](event)
              .thenPublish(event)
              .thenReply(command.replyTo)(_.getProposal)
        }
      }

      def onVoteProposalCommand(state: State, command: VoteProposalCommand): ReplyEffect[ProposalEvent, State] = {
        state.proposal match {
          case None => Effect.reply(command.replyTo)(ProposalNotFound)
          case _ =>
            command.vote match {
              // User hasn't voted on proposal yet
              case None =>
                val event = ProposalVoted(
                  id = command.proposalId,
                  maybeUserId = command.maybeUserId,
                  eventDate = DateHelper.now(),
                  maybeOrganisationId = command.maybeOrganisationId,
                  requestContext = command.requestContext,
                  voteKey = command.voteKey,
                  voteTrust = command.voteTrust,
                  eventId = Some(idGenerator.nextEventId())
                )
                Effect
                  .persist[ProposalVoted, State](event)
                  .thenPublish(event)
                  .thenRun { s =>
                    logVoteEvent(event).onComplete {
                      case Success(_) => command.replyTo ! s.getVote(command.voteKey)
                      case Failure(e) => command.replyTo ! HistoryError(e)
                    }
                  }
                  .thenNoReply()
              // User has already voted on this proposal with this vote key (e.g.: double click)
              case Some(vote) if vote.voteKey == command.voteKey =>
                Effect.reply(command.replyTo)(state.getVote(command.voteKey))
              // User has already voted with another key. Behaviour: first, unvote previous vote then, revote with new key
              case Some(vote) =>
                val unvoteEvent = ProposalUnvoted(
                  id = command.proposalId,
                  maybeUserId = command.maybeUserId,
                  eventDate = DateHelper.now(),
                  requestContext = command.requestContext,
                  voteKey = vote.voteKey,
                  maybeOrganisationId = command.maybeOrganisationId,
                  voteTrust = vote.trust,
                  selectedQualifications = vote.qualificationKeys.keys.toSeq,
                  eventId = Some(idGenerator.nextEventId())
                )
                val voteEvent = ProposalVoted(
                  id = command.proposalId,
                  maybeUserId = command.maybeUserId,
                  eventDate = DateHelper.now(),
                  maybeOrganisationId = command.maybeOrganisationId,
                  requestContext = command.requestContext,
                  voteKey = command.voteKey,
                  voteTrust = command.voteTrust,
                  eventId = Some(idGenerator.nextEventId())
                )
                Effect
                  .persist[ProposalEvent, State](Seq(unvoteEvent, voteEvent))
                  .thenPublish(unvoteEvent)
                  .thenPublish(voteEvent)
                  .thenRun { s =>
                    logUnvoteEvent(unvoteEvent).flatMap(_ => logVoteEvent(voteEvent)).onComplete {
                      case Success(_) => command.replyTo ! s.getVote(command.voteKey)
                      case Failure(e) => command.replyTo ! HistoryError(e)
                    }
                  }
                  .thenNoReply()
            }
        }
      }

      def logVoteEvent(event: ProposalVoted): Future[_] = {
        sessionHistoryCoordinatorService.logTransactionalHistory(
          LogSessionVoteEvent(
            sessionId = event.requestContext.sessionId,
            requestContext = event.requestContext,
            action = SessionAction(
              date = event.eventDate,
              actionType = ProposalVoteAction.value,
              SessionVote(proposalId = event.id, voteKey = event.voteKey, trust = event.voteTrust)
            )
          )
        )
      }

      def logUnvoteEvent(event: ProposalUnvoted): Future[_] = {
        sessionHistoryCoordinatorService.logTransactionalHistory(
          LogSessionUnvoteEvent(
            sessionId = event.requestContext.sessionId,
            requestContext = event.requestContext,
            action = SessionAction(
              date = event.eventDate,
              actionType = ProposalUnvoteAction.value,
              SessionUnvote(proposalId = event.id, voteKey = event.voteKey, trust = event.voteTrust)
            )
          )
        )
      }

      def logQualifyVoteEvent(event: ProposalQualified): Future[_] = {
        sessionHistoryCoordinatorService.logTransactionalHistory(
          LogSessionQualificationEvent(
            sessionId = event.requestContext.sessionId,
            requestContext = event.requestContext,
            action = SessionAction(
              date = event.eventDate,
              actionType = ProposalQualifyAction.value,
              SessionQualification(
                proposalId = event.id,
                qualificationKey = event.qualificationKey,
                trust = event.voteTrust
              )
            )
          )
        )
      }

      def logRemoveVoteQualificationEvent(event: ProposalUnqualified): Future[_] = {
        sessionHistoryCoordinatorService.logTransactionalHistory(
          LogSessionUnqualificationEvent(
            sessionId = event.requestContext.sessionId,
            requestContext = event.requestContext,
            action = SessionAction(
              date = event.eventDate,
              actionType = ProposalUnqualifyAction.value,
              SessionUnqualification(
                proposalId = event.id,
                qualificationKey = event.qualificationKey,
                trust = event.voteTrust
              )
            )
          )
        )
      }

      def onUnvoteProposalCommand(state: State, command: UnvoteProposalCommand): ReplyEffect[ProposalEvent, State] = {
        state.proposal match {
          case None => Effect.reply(command.replyTo)(ProposalNotFound)
          case _ =>
            command.vote match {
              // User hasn't voted on proposal yet
              case None =>
                Effect.reply(command.replyTo)(state.getVote(command.voteKey))
              // User voted on this proposal so we unvote whatever voted key
              case Some(vote) =>
                val event = ProposalUnvoted(
                  id = command.proposalId,
                  maybeUserId = command.maybeUserId,
                  eventDate = DateHelper.now(),
                  requestContext = command.requestContext,
                  voteKey = vote.voteKey,
                  maybeOrganisationId = command.maybeOrganisationId,
                  selectedQualifications = vote.qualificationKeys.keys.toSeq,
                  voteTrust = vote.trust,
                  eventId = Some(idGenerator.nextEventId())
                )
                Effect
                  .persist[ProposalUnvoted, State](event)
                  .thenPublish(event)
                  .thenRun { s =>
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
                            voteTrust = vote.qualificationKeys.getOrElse(qualification, event.voteTrust),
                            eventId = Some(idGenerator.nextEventId())
                          )
                        )
                      }
                    }.onComplete { result =>
                      command.replyTo ! (result match {
                        case Success(_) => s.getVote(command.voteKey)
                        case Failure(e) => HistoryError(e)
                      })
                    }
                  }
                  .thenNoReply()
            }
        }
      }

      def checkQualification(voteKey: VoteKey, commandVoteKey: VoteKey, qualificationKey: QualificationKey): Boolean = {
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

      def onQualificationProposalCommand(
        state: State,
        command: QualifyVoteCommand
      ): ReplyEffect[ProposalEvent, State] = {
        state.proposal match {
          case None => Effect.reply(command.replyTo)(ProposalNotFound)
          case _ =>
            command.vote match {
              // User hasn't voted on proposal yet
              case None =>
                Effect.reply(command.replyTo)(state.getQualification(command.voteKey, command.qualificationKey))
              // User already qualified this qualification key on this proposal (e.g.: double click)
              case Some(vote) if vote.qualificationKeys.contains(command.qualificationKey) =>
                Effect.reply(command.replyTo)(state.getQualification(command.voteKey, command.qualificationKey))
              // Qualificication doesn't belong to user's vote.
              case Some(vote) if !checkQualification(vote.voteKey, command.voteKey, command.qualificationKey) =>
                Effect.reply(command.replyTo)(state.getQualification(command.voteKey, command.qualificationKey))
              // User qualifies correctly
              case Some(vote) =>
                val resolvedTrust = if (!vote.trust.isTrusted) {
                  vote.trust
                } else {
                  command.voteTrust
                }
                val event = ProposalQualified(
                  id = command.proposalId,
                  maybeUserId = command.maybeUserId,
                  eventDate = DateHelper.now(),
                  requestContext = command.requestContext,
                  voteKey = command.voteKey,
                  qualificationKey = command.qualificationKey,
                  voteTrust = resolvedTrust,
                  eventId = Some(idGenerator.nextEventId())
                )
                Effect
                  .persist[ProposalQualified, State](event)
                  .thenPublish(event)
                  .thenRun { s =>
                    logQualifyVoteEvent(event).onComplete { result =>
                      command.replyTo ! (result match {
                        case Success(_) =>
                          s.getQualification(command.voteKey, command.qualificationKey)
                        case Failure(e) => HistoryError(e)
                      })
                    }
                  }
                  .thenNoReply()
            }
        }
      }

      def onUnqualificationProposalCommand(
        state: State,
        command: UnqualifyVoteCommand
      ): ReplyEffect[ProposalEvent, State] = {
        state.proposal match {
          case None => Effect.reply(command.replyTo)(ProposalNotFound)
          case _ =>
            command.vote match {
              // User hasn't voted on proposal yet
              case None =>
                Effect.reply(command.replyTo)(state.getQualification(command.voteKey, command.qualificationKey))

              // User has voted and qualified this proposal
              case Some(vote) if vote.qualificationKeys.contains(command.qualificationKey) =>
                val event = ProposalUnqualified(
                  id = command.proposalId,
                  maybeUserId = command.maybeUserId,
                  eventDate = DateHelper.now(),
                  requestContext = command.requestContext,
                  voteKey = command.voteKey,
                  qualificationKey = command.qualificationKey,
                  voteTrust = vote.qualificationKeys.getOrElse(command.qualificationKey, command.voteTrust),
                  eventId = Some(idGenerator.nextEventId())
                )
                Effect
                  .persist[ProposalUnqualified, State](event)
                  .thenPublish(event)
                  .thenRun { s =>
                    logRemoveVoteQualificationEvent(event).onComplete { result =>
                      command.replyTo ! (result match {
                        case Success(_) => s.getQualification(command.voteKey, command.qualificationKey)
                        case Failure(e) => HistoryError(e)
                      })
                    }
                  }
                  .thenNoReply()
              // User has voted on this proposal but hasn't qualified with the unqualify key
              case _ =>
                Effect.reply(command.replyTo)(state.getQualification(command.voteKey, command.qualificationKey))

            }
        }

      }

      def onViewProposalCommand(
        proposalId: ProposalId,
        requestContext: RequestContext,
        replyTo: ActorRef[ProposalActorResponse[ProposalNotFound, Proposal]]
      ): ReplyEffect[ProposalEvent, State] = {
        Effect
          .persist(
            ProposalViewed(
              id = proposalId,
              eventDate = DateHelper.now(),
              requestContext = requestContext,
              eventId = Some(idGenerator.nextEventId())
            )
          )
          .thenReply(replyTo)(_.getProposal)
      }

      def onProposeCommand(command: ProposeCommand): ReplyEffect[ProposalEvent, State] = {
        val user = command.user
        val event = ProposalProposed(
          id = command.proposalId,
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
          country = command.requestContext.country,
          question = Some(command.question.questionId),
          initialProposal = command.initialProposal,
          eventId = Some(idGenerator.nextEventId())
        )
        Effect
          .persist[ProposalProposed, State](event)
          .thenPublish(event)
          .thenReply(command.replyTo)(_ => Envelope(command.proposalId))

      }

      def onUpdateProposalCommand(state: State, command: UpdateProposalCommand): ReplyEffect[ProposalEvent, State] = {
        state.proposal match {
          case None => Effect.reply(command.replyTo)(ProposalNotFound)
          case Some(proposal) if proposal.status != ProposalStatus.Accepted =>
            Effect.reply(command.replyTo)(
              InvalidStateError(s"Proposal ${command.proposalId.value} is not accepted and cannot be updated")
            )
          case Some(proposal) =>
            val event =
              ProposalUpdated(
                id = proposal.proposalId,
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
                question = Some(command.question.questionId),
                eventId = Some(idGenerator.nextEventId())
              )
            Effect
              .persist[ProposalUpdated, State](event)
              .thenPublish(event)
              .thenReply(command.replyTo)(_.getProposal)
        }
      }

      def onUpdateProposalVotesCommand(
        state: State,
        command: UpdateProposalVotesCommand
      ): ReplyEffect[ProposalEvent, State] = {
        state.proposal match {
          case None => Effect.reply(command.replyTo)(ProposalNotFound)
          case Some(proposal) if proposal.status != ProposalStatus.Accepted =>
            Effect.reply(command.replyTo)(
              InvalidStateError(s"Proposal ${command.proposalId.value} is not accepted and cannot be updated")
            )
          case Some(proposal) =>
            val event = ProposalVotesUpdated(
              id = command.proposalId,
              eventDate = DateHelper.now(),
              requestContext = command.requestContext,
              updatedAt = command.updatedAt,
              moderator = Some(command.moderator),
              newVotes = mergeVotes(proposal.votes, command.votes),
              eventId = Some(idGenerator.nextEventId())
            )
            Effect
              .persist[ProposalVotesUpdated, State](event)
              .thenPublish(event)
              .thenReply(command.replyTo)(_.getProposal)
        }
      }

      def onAcceptProposalCommand(state: State, command: AcceptProposalCommand): ReplyEffect[ProposalEvent, State] = {
        state.proposal match {
          case None => Effect.reply(command.replyTo)(ProposalNotFound)
          case Some(proposal) if proposal.status == ProposalStatus.Archived =>
            Effect.reply(command.replyTo)(
              InvalidStateError(s"Proposal ${command.proposalId.value} is archived and cannot be validated")
            )
          case Some(proposal) if proposal.status == ProposalStatus.Accepted =>
            // possible double request, ignore.
            // other modifications should use the proposal update method
            Effect.reply(command.replyTo)(
              InvalidStateError(s"Proposal ${command.proposalId.value} is already validated")
            )
          case Some(proposal) =>
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
              question = Some(command.question.questionId),
              eventId = Some(idGenerator.nextEventId())
            )
            Effect
              .persist[ProposalAccepted, State](event)
              .thenPublish(event)
              .thenReply(command.replyTo)(_.getProposal)
        }
      }

      def onRefuseProposalCommand(state: State, command: RefuseProposalCommand): ReplyEffect[ProposalEvent, State] = {
        state.proposal match {
          case None => Effect.reply(command.replyTo)(ProposalNotFound)
          case Some(proposal) if proposal.status == ProposalStatus.Archived =>
            Effect.reply(command.replyTo)(
              InvalidStateError(s"Proposal ${command.proposalId.value} is archived and cannot be refused")
            )
          case Some(proposal) if proposal.status == ProposalStatus.Refused =>
            // possible double request, ignore.
            // other modifications should use the proposal update command
            Effect.reply(command.replyTo)(InvalidStateError(s"Proposal ${command.proposalId.value} is already refused"))
          case Some(proposal) =>
            val event = ProposalRefused(
              id = command.proposalId,
              eventDate = DateHelper.now(),
              requestContext = command.requestContext,
              moderator = command.moderator,
              sendRefuseEmail = command.sendNotificationEmail,
              refusalReason = command.refusalReason,
              operation = proposal.operation,
              eventId = Some(idGenerator.nextEventId())
            )
            Effect
              .persist[ProposalRefused, State](event)
              .thenPublish(event)
              .thenReply(command.replyTo)(_.getProposal)
        }
      }

      def onPostponeProposalCommand(
        state: State,
        command: PostponeProposalCommand
      ): ReplyEffect[ProposalEvent, State] = {
        state.proposal match {
          case None => Effect.reply(command.replyTo)(ProposalNotFound)
          case Some(proposal) if proposal.status == ProposalStatus.Archived =>
            Effect.reply(command.replyTo)(
              InvalidStateError(s"Proposal ${command.proposalId.value} is archived and cannot be postponed")
            )
          case Some(proposal)
              if proposal.status == ProposalStatus.Accepted || proposal.status == ProposalStatus.Refused =>
            Effect.reply(command.replyTo)(
              InvalidStateError(s"Proposal ${command.proposalId.value} is already moderated and cannot be postponed")
            )
          case Some(proposal) if proposal.status == ProposalStatus.Postponed =>
            Effect.reply(command.replyTo)(
              InvalidStateError(s"Proposal ${command.proposalId.value} is already postponed")
            )
          case Some(_) =>
            val event =
              ProposalPostponed(
                id = command.proposalId,
                requestContext = command.requestContext,
                moderator = command.moderator,
                eventDate = DateHelper.now(),
                eventId = Some(idGenerator.nextEventId())
              )
            Effect
              .persist[ProposalPostponed, State](event)
              .thenPublish(event)
              .thenReply(command.replyTo)(_.getProposal)
        }
      }

      def onLockProposalCommand(
        state: State,
        command: LockProposalCommand,
        lockHandler: LockHandler
      ): ReplyEffect[ProposalEvent, State] = {
        state.proposal match {
          case None => Effect.reply(command.replyTo)(ProposalNotFound)
          case _ =>
            lockHandler.lock match {
              case Some(ProposalLock(moderatorId, moderatorName, deadline))
                  if moderatorId != command.moderatorId && deadline.hasTimeLeft() =>
                Effect.reply(command.replyTo)(AlreadyLockedBy(moderatorName))

              case maybeLock if maybeLock.forall(_.deadline.isOverdue()) =>
                val event = ProposalLocked(
                  id = command.proposalId,
                  moderatorId = command.moderatorId,
                  moderatorName = command.moderatorName,
                  eventDate = DateHelper.now(),
                  requestContext = command.requestContext,
                  eventId = Some(idGenerator.nextEventId())
                )
                lockHandler.take(event.moderatorId, event.moderatorName)
                Effect
                  .persist[ProposalLocked, State](event)
                  .thenPublish(event)
                  .thenReply(command.replyTo)(_ => Envelope(event.moderatorId))
              case _ =>
                lockHandler.take(command.moderatorId, command.moderatorName)
                Effect.reply(command.replyTo)(Envelope(command.moderatorId))
            }
        }
      }

      def mergeQualifications(
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

      def mergeVotes(proposalVotes: Seq[Vote], commandVotes: Seq[UpdateVoteRequest]): Seq[Vote] = {
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

      def onSetKeywordsCommand(command: SetKeywordsCommand): ReplyEffect[ProposalEvent, State] = {
        val event = ProposalKeywordsSet(
          command.proposalId,
          eventDate = DateHelper.now(),
          keywords = command.keywords,
          requestContext = command.requestContext,
          eventId = Some(idGenerator.nextEventId())
        )
        Effect.persist(event).thenPublish(event).thenReply(command.replyTo)(_.getProposal)
      }

      def applyProposalUpdated(state: Proposal, event: ProposalUpdated): Proposal = {

        val arguments: Map[String, String] = Map(
          "question" -> event.question.map(_.value).getOrElse(""),
          "tags" -> event.tags.map(_.value).mkString(", "),
          "labels" -> event.labels.map(_.value).mkString(", "),
          "idea" -> event.idea.map(_.value).getOrElse(""),
          "operation" -> event.operation.map(_.value).getOrElse("")
        ).filter {
          case (_, value) => value.nonEmpty
        }
        @SuppressWarnings(Array("org.wartremover.warts.Throw"))
        val moderator: UserId = event.moderator match {
          case Some(userId) => userId
          case _            => throw new IllegalStateException("moderator required")
        }
        val action =
          ProposalAction(
            date = event.eventDate,
            user = moderator,
            actionType = ProposalUpdateAction.value,
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
          case None => proposal
          case Some(ProposalEdition(_, newVersion)) =>
            proposal.copy(content = newVersion, slug = SlugHelper(newVersion))
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
          case (_, value) => value.nonEmpty
        }
        val action =
          ProposalAction(
            date = event.eventDate,
            user = event.moderator,
            actionType = ProposalAcceptAction.value,
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
          case None => proposal
          case Some(ProposalEdition(_, newVersion)) =>
            proposal.copy(content = newVersion, slug = SlugHelper(newVersion))
        }
      }

      def applyProposalRefused(state: Proposal, event: ProposalRefused): Proposal = {
        val arguments: Map[String, String] =
          Map("refusalReason" -> event.refusalReason.getOrElse("")).filter {
            case (_, value) => value.nonEmpty
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

      val increaseCountIf: (Int, Boolean) => Int = {
        case (newCount, true) => newCount + 1
        case (newCount, _)    => newCount
      }

      def decreaseCountIf(logError: () => Unit): (Int, Boolean) => Int = {
        case (0, true) =>
          logError()
          0
        case (newCount, true) => newCount - 1
        case (newCount, _)    => newCount
      }

      def applyProposalVoted(state: Proposal, event: ProposalVoted): Proposal = {
        state.copy(
          votes = state.votes.map { vote =>
            if (vote.key == event.voteKey) {
              Vote(
                key = event.voteKey,
                count = increaseCountIf(vote.count, true),
                countSegment = increaseCountIf(vote.countSegment, event.voteTrust.isInSegment),
                countSequence = increaseCountIf(vote.countSequence, event.voteTrust.isInSequence),
                countVerified = increaseCountIf(vote.countVerified, event.voteTrust.isTrusted),
                qualifications = vote.qualifications
              )
            } else {
              vote
            }
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
            case Vote(event.voteKey, count, countVerified, countSequence, countSegment, qualifications) =>
              def logError(voteType: String): () => Unit =
                () =>
                  logger.error(
                    s"Prevented $voteType [${event.voteKey}] count to be set to -1 for proposal: $state. Caused by event: $event"
                  )
              Vote(
                key = event.voteKey,
                count = decreaseCountIf(logError("count"))(count, true),
                countSegment = decreaseCountIf(logError("countSegment"))(countSegment, event.voteTrust.isInSegment),
                countSequence = decreaseCountIf(logError("countSequence"))(countSequence, event.voteTrust.isInSequence),
                countVerified = decreaseCountIf(logError("countVerified"))(countVerified, event.voteTrust.isTrusted),
                qualifications = qualifications.map(qualification => applyUnqualifVote(state, qualification, event))
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

      def applyUnqualifVote[T: Unvoted](state: Proposal, qualification: Qualification, event: T): Qualification = {
        if (event.selectedQualifications.contains(qualification.key)) {
          def logError(qualifType: String): () => Unit =
            () =>
              logger.error(
                s"Prevented $qualifType [${qualification.key}] count to be set to -1 for proposal: $state. Caused by event: $event"
              )
          Qualification(
            key = qualification.key,
            count = decreaseCountIf(logError("count"))(qualification.count, true),
            countVerified =
              decreaseCountIf(logError("countVerified"))(qualification.countVerified, event.voteTrust.isTrusted),
            countSequence =
              decreaseCountIf(logError("countSequence"))(qualification.countSequence, event.voteTrust.isInSequence),
            countSegment =
              decreaseCountIf(logError("countSegment"))(qualification.countSegment, event.voteTrust.isInSegment)
          )
        } else {
          qualification
        }
      }

      def applyProposalQualified(state: Proposal, event: ProposalQualified): Proposal = {
        state.copy(votes = state.votes.map { vote =>
          if (vote.key == event.voteKey) {
            vote.copy(qualifications = vote.qualifications.map {
              qualification =>
                if (qualification.key == event.qualificationKey) {
                  Qualification(
                    key = qualification.key,
                    count = increaseCountIf(qualification.count, true),
                    countVerified = increaseCountIf(qualification.countVerified, event.voteTrust.isTrusted),
                    countSequence = increaseCountIf(qualification.countSequence, event.voteTrust.isInSequence),
                    countSegment = increaseCountIf(qualification.countSegment, event.voteTrust.isInSegment)
                  )
                } else {
                  qualification
                }
            })
          } else {
            vote
          }
        })
      }

      def applyProposalUnqualified(state: Proposal, event: ProposalUnqualified): Proposal = {
        state.copy(votes = state.votes.map {
          case vote @ Vote(event.voteKey, _, _, _, _, _) =>
            vote.copy(qualifications = vote.qualifications.map(applyUnqualifVote(state, _, event)))
          case vote => vote
        })
      }

      def applyProposalLocked(state: Proposal, event: ProposalLocked): Proposal = {
        val arguments: Map[String, String] =
          Map("moderatorName" -> event.moderatorName.getOrElse("<unknown>")).filter {
            case (_, value) => value.nonEmpty
          }
        val action =
          ProposalAction(date = event.eventDate, user = event.moderatorId, actionType = "lock", arguments = arguments)
        state.copy(events = action :: state.events, updatedAt = Some(event.eventDate))
      }

      def applyProposalKeywordsSet(state: Proposal, event: ProposalKeywordsSet): Proposal = {
        state.copy(keywords = event.keywords, updatedAt = Some(event.eventDate))
      }

      val eventHandler: (State, ProposalEvent) => State = {
        case (_, e: ProposalProposed) =>
          State(
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
                      Qualification(
                        key = PlatitudeAgree,
                        count = 0,
                        countVerified = 0,
                        countSequence = 0,
                        countSegment = 0
                      )
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
                      Qualification(
                        key = Impossible,
                        count = 0,
                        countVerified = 0,
                        countSequence = 0,
                        countSegment = 0
                      ),
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
                      Qualification(
                        key = DoNotUnderstand,
                        count = 0,
                        countVerified = 0,
                        countSequence = 0,
                        countSegment = 0
                      ),
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
                    actionType = ProposalProposeAction.value,
                    arguments = Map("content" -> e.content)
                  )
                ),
                initialProposal = e.initialProposal,
                keywords = Seq.empty
              )
            )
          )
        case (state, e: ProposalUpdated) => State(state.proposal.map(applyProposalUpdated(_, e)))
        case (state, e: ProposalVotesVerifiedUpdated) =>
          State(state.proposal.map(applyProposalVotesVerifiedUpdated(_, e)))
        case (state, e: ProposalVotesUpdated) => State(state.proposal.map(applyProposalVotesUpdated(_, e)))
        case (state, e: ProposalAccepted)     => State(state.proposal.map(applyProposalAccepted(_, e)))
        case (state, e: ProposalRefused)      => State(state.proposal.map(applyProposalRefused(_, e)))
        case (state, e: ProposalPostponed)    => State(state.proposal.map(applyProposalPostponed(_, e)))
        case (state, e: ProposalVoted)        => State(state.proposal.map(applyProposalVoted(_, e)))
        case (state, e: ProposalUnvoted)      => State(state.proposal.map(applyProposalUnvoted(_, e)))
        case (state, e: ProposalQualified)    => State(state.proposal.map(applyProposalQualified(_, e)))
        case (state, e: ProposalUnqualified)  => State(state.proposal.map(applyProposalUnqualified(_, e)))
        case (state, e: ProposalLocked)       => State(state.proposal.map(applyProposalLocked(_, e)))
        case (_, e: ProposalPatched)          => State(Some(e.proposal))
        case (state, e: ProposalKeywordsSet)  => State(state.proposal.map(applyProposalKeywordsSet(_, e)))
        case (state, _: DeprecatedEvent)      => state
        case (state, _)                       => state
      }

      val snapshotAdapter = new SnapshotAdapter[State] {
        override def toJournal(state: State): Any = state

        @SuppressWarnings(Array("org.wartremover.warts.Throw"))
        override def fromJournal(from: Any): State = from match {
          case proposal: Proposal => State(Some(proposal))
          case state: State       => state
          case other =>
            throw new IllegalStateException(s"$other with class ${other.getClass} is not a recoverable state")
        }
      }

      EventSourcedBehavior
        .withEnforcedReplies[ProposalCommand, ProposalEvent, State](
          persistenceId = PersistenceId.ofUniqueId(context.self.path.name),
          emptyState = State(proposal = None),
          commandHandler(new LockHandler(lockDuration)),
          eventHandler
        )
        .withJournalPluginId(JournalPluginId)
        .withSnapshotPluginId(SnapshotPluginId)
        .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 10, keepNSnapshots = 50))
        .snapshotAdapter(snapshotAdapter)
    }
  }

  implicit class PublishOps[E, S](val effect: EffectBuilder[E, S]) extends AnyVal {
    def thenPublish(event: E)(implicit context: ActorContext[_]): EffectBuilder[E, S] =
      effect.thenRun(_ => context.system.eventStream ! EventStream.Publish(event))
  }

  implicit class UnvotedOps[T](val event: T) extends AnyVal {
    def selectedQualifications(implicit unvoted: Unvoted[T]): Seq[QualificationKey] = unvoted.qualifications(event)
    def voteTrust(implicit unvoted: Unvoted[T]): VoteTrust = unvoted.trust(event)
  }
}

trait Unvoted[T] {
  def qualifications(event: T): Seq[QualificationKey]
  def trust(event: T): VoteTrust
}

object Unvoted {
  implicit val qualification: Unvoted[ProposalUnqualified] = new Unvoted[ProposalUnqualified] {
    def qualifications(event: ProposalUnqualified): Seq[QualificationKey] =
      Seq(event.qualificationKey)
    def trust(event: ProposalUnqualified): VoteTrust =
      event.voteTrust
  }

  implicit val vote: Unvoted[ProposalUnvoted] = new Unvoted[ProposalUnvoted] {
    def qualifications(event: ProposalUnvoted): Seq[QualificationKey] =
      event.selectedQualifications
    def trust(event: ProposalUnvoted): VoteTrust =
      event.voteTrust
  }
}
