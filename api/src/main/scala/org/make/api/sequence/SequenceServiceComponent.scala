package org.make.api.sequence

import java.time.ZonedDateTime

import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal._
import org.make.api.sessionhistory.{RequestSessionVoteValues, SessionHistoryCoordinatorServiceComponent}
import org.make.api.technical.businessconfig.BackofficeConfiguration
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent}
import org.make.api.user.{UserResponse, UserServiceComponent}
import org.make.api.userhistory.UserHistoryActor.RequestVoteValues
import org.make.api.userhistory._
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.{Proposal, ProposalId}
import org.make.core.reference.{TagId, ThemeId}
import org.make.core.sequence._
import org.make.core.sequence.indexed.{IndexedSequence, SequencesSearchResult}
import org.make.core.user._
import org.make.core.{DateHelper, RequestContext, SlugHelper}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

trait SequenceServiceComponent {
  def sequenceService: SequenceService
}

trait SequenceService {
  def search(maybeUserId: Option[UserId],
             query: SearchQuery,
             requestContext: RequestContext): Future[SequencesSearchResult]
  def startNewSequence(maybeUserId: Option[UserId],
                       slug: String,
                       includedProposals: Seq[ProposalId] = Seq.empty,
                       requestContext: RequestContext): Future[Option[SequenceResult]]
  def getSequenceById(sequenceId: SequenceId, requestContext: RequestContext): Future[Option[Sequence]]
  def create(userId: UserId,
             requestContext: RequestContext,
             createdAt: ZonedDateTime,
             title: String,
             tagIds: Seq[TagId] = Seq.empty,
             themeIds: Seq[ThemeId] = Seq.empty,
             searchable: Boolean): Future[Option[SequenceResponse]]
  def update(sequenceId: SequenceId,
             moderatorId: UserId,
             requestContext: RequestContext,
             title: Option[String],
             status: Option[SequenceStatus],
             operation: Option[String],
             themeIds: Seq[ThemeId],
             tagIds: Seq[TagId]): Future[Option[SequenceResponse]]
  def addProposals(sequenceId: SequenceId,
                   moderatorId: UserId,
                   requestContext: RequestContext,
                   proposalIds: Seq[ProposalId]): Future[Option[SequenceResponse]]
  def removeProposals(sequenceId: SequenceId,
                      moderatorId: UserId,
                      requestContext: RequestContext,
                      proposalIds: Seq[ProposalId]): Future[Option[SequenceResponse]]
  def getModerationSequenceById(sequenceId: SequenceId): Future[Option[SequenceResponse]]
}

trait DefaultSequenceServiceComponent extends SequenceServiceComponent {
  this: IdGeneratorComponent
    with ProposalServiceComponent
    with ProposalCoordinatorServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with SessionHistoryCoordinatorServiceComponent
    with SequenceServiceComponent
    with SequenceSearchEngineComponent
    with ProposalSearchEngineComponent
    with SequenceCoordinatorServiceComponent
    with EventBusServiceComponent
    with UserServiceComponent
    with MakeSettingsComponent
    with StrictLogging =>

  override lazy val sequenceService: SequenceService = new SequenceService {

    implicit private val defaultTimeout: Timeout = new Timeout(5.seconds)

    override def search(maybeUserId: Option[UserId],
                        query: SearchQuery,
                        requestContext: RequestContext): Future[SequencesSearchResult] = {
      maybeUserId.foreach { userId =>
        userHistoryCoordinatorService.logHistory(
          LogUserSearchSequencesEvent(
            userId,
            requestContext,
            UserAction(DateHelper.now(), LogUserSearchSequencesEvent.action, SearchSequenceParameters(query))
          )
        )
      }
      elasticsearchSequenceAPI.searchSequences(query)
    }

    override def startNewSequence(maybeUserId: Option[UserId],
                                  slug: String,
                                  includedProposals: Seq[ProposalId] = Seq.empty,
                                  requestContext: RequestContext): Future[Option[SequenceResult]] = {

      val futureMaybeSequence: Future[Option[IndexedSequence]] = elasticsearchSequenceAPI.findSequenceBySlug(slug)
      maybeUserId.foreach { userId =>
        userHistoryCoordinatorService.logHistory(
          LogUserStartSequenceEvent(
            userId,
            requestContext,
            UserAction(DateHelper.now(), LogUserStartSequenceEvent.action, StartSequenceParameters(slug))
          )
        )
      }

      val futureSequenceWithProposals = futureMaybeSequence.flatMap {
        case None => Future.successful(None)
        case Some(sequence) =>
          val allProposals: Future[Seq[Proposal]] = Future
            .traverse(sequence.proposals) { id =>
              proposalCoordinatorService.getProposal(id.proposalId)
            }
            .map(_.flatten)

          for {
            allProposals   <- allProposals
            votedProposals <- futureVotedProposals(maybeUserId, requestContext, allProposals.map(_.proposalId))
          } yield {
            Some(
              (
                sequence,
                SelectionAlgorithm.newProposalsForSequence(
                  targetLength = BackofficeConfiguration.defaultMaxProposalsPerSequence,
                  proposals = allProposals,
                  votedProposals = votedProposals.keys.toSeq,
                  newProposalVoteCount = BackofficeConfiguration.defaultProposalVotesThreshold,
                  includeList = includedProposals
                ),
                votedProposals
              )
            )
          }
      }

      futureSequenceWithProposals.flatMap {
        case None => Future.successful(None)
        case Some((sequence, proposals, votes)) =>
          elasticsearchProposalAPI.findProposalsByIds(proposals, random = false).map { indexedProposals =>
            Some(
              SequenceResult(
                id = sequence.id,
                title = sequence.title,
                slug = sequence.slug,
                proposals = indexedProposals
                  .map(indexed => ProposalResult(indexed, maybeUserId.contains(indexed.userId), votes.get(indexed.id)))
              )
            )
          }
      }
    }

    private def futureVotedProposals(maybeUserId: Option[UserId],
                                     requestContext: RequestContext,
                                     proposals: Seq[ProposalId]): Future[Map[ProposalId, VoteAndQualifications]] =
      maybeUserId.map { userId =>
        userHistoryCoordinatorService.retrieveVoteAndQualifications(RequestVoteValues(userId, proposals))
      }.getOrElse {
        sessionHistoryCoordinatorService.retrieveVoteAndQualifications(
          RequestSessionVoteValues(requestContext.sessionId, proposals)
        )
      }

    override def create(userId: UserId,
                        requestContext: RequestContext,
                        createdAt: ZonedDateTime,
                        title: String,
                        tagIds: Seq[TagId],
                        themeIds: Seq[ThemeId],
                        searchable: Boolean): Future[Option[SequenceResponse]] = {
      sequenceCoordinatorService
        .create(
          CreateSequenceCommand(
            sequenceId = idGenerator.nextSequenceId(),
            slug = SlugHelper.apply(title),
            title = title,
            requestContext = requestContext,
            moderatorId = userId,
            tagIds = tagIds,
            themeIds = themeIds,
            status = SequenceStatus.Published,
            searchable = searchable
          )
        )
        .flatMap(getModerationSequenceById)
    }

    override def addProposals(sequenceId: SequenceId,
                              userId: UserId,
                              requestContext: RequestContext,
                              proposalIds: Seq[ProposalId]): Future[Option[SequenceResponse]] = {
      sequenceCoordinatorService
        .addProposals(
          AddProposalsSequenceCommand(
            sequenceId = sequenceId,
            requestContext = requestContext,
            proposalIds = proposalIds,
            moderatorId = userId
          )
        )
        .flatMap {
          case Some(sequence) => getSequenceResponse(sequence)
          case _              => Future.successful(None)
        }
    }

    override def removeProposals(sequenceId: SequenceId,
                                 userId: UserId,
                                 requestContext: RequestContext,
                                 proposalIds: Seq[ProposalId]): Future[Option[SequenceResponse]] = {
      sequenceCoordinatorService
        .removeProposals(
          RemoveProposalsSequenceCommand(
            moderatorId = userId,
            sequenceId = sequenceId,
            proposalIds = proposalIds,
            requestContext = requestContext
          )
        )
        .flatMap {
          case Some(sequence) => getSequenceResponse(sequence)
          case _              => Future.successful(None)
        }
    }

    override def update(sequenceId: SequenceId,
                        userId: UserId,
                        requestContext: RequestContext,
                        title: Option[String],
                        status: Option[SequenceStatus],
                        operation: Option[String],
                        themeIds: Seq[ThemeId],
                        tagIds: Seq[TagId]): Future[Option[SequenceResponse]] = {

      sequenceCoordinatorService
        .update(
          UpdateSequenceCommand(
            moderatorId = userId,
            sequenceId = sequenceId,
            requestContext = requestContext,
            title = title,
            status = status,
            operation = operation,
            themeIds = themeIds,
            tagIds = tagIds
          )
        )
        .flatMap {
          case Some(sequence) => getSequenceResponse(sequence)
          case _              => Future.successful(None)
        }
    }

    override def getSequenceById(sequenceId: SequenceId, requestContext: RequestContext): Future[Option[Sequence]] = {
      sequenceCoordinatorService.viewSequence(sequenceId, requestContext)
    }

    override def getModerationSequenceById(sequenceId: SequenceId): Future[Option[SequenceResponse]] = {
      sequenceCoordinatorService.getSequence(sequenceId).flatMap {
        case Some(sequence) => getSequenceResponse(sequence)
        case _              => Future.successful(None)
      }
    }

    private def getSequenceResponse(sequence: Sequence): Future[Option[SequenceResponse]] = {
      val eventsUserIds: Seq[UserId] = sequence.events.map(_.user).distinct
      val futureEventsUsers: Future[Seq[UserResponse]] =
        userService.getUsersByUserIds(eventsUserIds).map(_.map(UserResponse.apply))

      futureEventsUsers.map { eventsUsers =>
        val events: Seq[SequenceActionResponse] = sequence.events.map { action =>
          SequenceActionResponse(
            date = action.date,
            user = eventsUsers.find(_.userId.value == action.user.value),
            actionType = action.actionType,
            arguments = action.arguments
          )
        }
        Some(
          SequenceResponse(
            sequenceId = sequence.sequenceId,
            slug = sequence.slug,
            title = sequence.title,
            status = sequence.status,
            creationContext = sequence.creationContext,
            createdAt = sequence.createdAt,
            updatedAt = sequence.updatedAt,
            themeIds = sequence.themeIds,
            tagIds = sequence.tagIds,
            proposalIds = sequence.proposalIds,
            sequenceTranslation = sequence.sequenceTranslation,
            events = events
          )
        )
      }
    }
  }
}
