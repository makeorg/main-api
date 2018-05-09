package org.make.api.sequence

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal._
import org.make.api.sessionhistory.{
  LogSessionStartSequenceEvent,
  RequestSessionVoteValues,
  SessionAction,
  SessionHistoryCoordinatorServiceComponent
}
import org.make.api.technical.businessconfig.BackofficeConfiguration
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent}
import org.make.api.user.{UserResponse, UserServiceComponent}
import org.make.api.userhistory.UserHistoryActor.RequestVoteValues
import org.make.api.userhistory._
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.operation.OperationId
import org.make.core.proposal.{Proposal, ProposalId}
import org.make.core.reference.{TagId, ThemeId}
import org.make.core.sequence._
import org.make.core.sequence.indexed.{IndexedSequence, SequencesSearchResult}
import org.make.core.user._
import org.make.core.{DateHelper, RequestContext, SlugHelper}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SequenceServiceComponent {
  def sequenceService: SequenceService
}

trait SequenceService {
  def search(maybeUserId: Option[UserId],
             query: SearchQuery,
             requestContext: RequestContext): Future[SequencesSearchResult]
  def startNewSequence(maybeUserId: Option[UserId],
                       slug: String,
                       includedProposals: Seq[ProposalId],
                       requestContext: RequestContext): Future[Option[SequenceResult]]
  def startNewSequence(maybeUserId: Option[UserId],
                       sequenceId: SequenceId,
                       includedProposals: Seq[ProposalId],
                       requestContext: RequestContext): Future[Option[SequenceResult]]
  def getSequenceById(sequenceId: SequenceId, requestContext: RequestContext): Future[Option[Sequence]]
  def create(userId: UserId,
             requestContext: RequestContext,
             createdAt: ZonedDateTime,
             title: String,
             tagIds: Seq[TagId] = Seq.empty,
             themeIds: Seq[ThemeId] = Seq.empty,
             operationId: Option[OperationId],
             searchable: Boolean): Future[Option[SequenceResponse]]
  def update(sequenceId: SequenceId,
             moderatorId: UserId,
             requestContext: RequestContext,
             title: Option[String],
             status: Option[SequenceStatus],
             operationId: Option[OperationId],
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
    with SelectionAlgorithmComponent
    with SequenceConfigurationComponent
    with StrictLogging =>

  override lazy val sequenceService: SequenceService = new SequenceService {

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
                                  includedProposals: Seq[ProposalId],
                                  requestContext: RequestContext): Future[Option[SequenceResult]] = {
      logStartSequenceUserHisory(Some(slug), None, maybeUserId, includedProposals, requestContext)

      elasticsearchSequenceAPI.findSequenceBySlug(slug).flatMap {
        case None => Future.successful(None)
        case Some(sequence) =>
          startSequence(maybeUserId, sequence, includedProposals, requestContext)
      }
    }

    override def startNewSequence(maybeUserId: Option[UserId],
                                  sequenceId: SequenceId,
                                  includedProposals: Seq[ProposalId],
                                  requestContext: RequestContext): Future[Option[SequenceResult]] = {
      logStartSequenceUserHisory(None, Some(sequenceId), maybeUserId, includedProposals, requestContext)

      elasticsearchSequenceAPI.findSequenceById(sequenceId).flatMap {
        case None => Future.successful(None)
        case Some(sequence) =>
          startSequence(maybeUserId, sequence, includedProposals, requestContext)
      }
    }

    private def startSequence(maybeUserId: Option[UserId],
                              sequence: IndexedSequence,
                              includedProposals: Seq[ProposalId],
                              requestContext: RequestContext): Future[Option[SequenceResult]] = {
      val allProposals: Future[Seq[Proposal]] = Future
        .traverse(sequence.proposals) { id =>
          proposalCoordinatorService.getProposal(id.proposalId)
        }
        .map(_.flatten)

      for {
        allProposals          <- allProposals
        votedProposals        <- futureVotedProposals(maybeUserId, requestContext, allProposals.map(_.proposalId))
        sequenceConfiguration <- sequenceConfigurationService.getSequenceConfiguration(sequence.id)
        selectedProposals: Seq[ProposalId] = selectionAlgorithm
          .selectProposalsForSequence(
            targetLength = BackofficeConfiguration.defaultMaxProposalsPerSequence,
            sequenceConfiguration = sequenceConfiguration,
            proposals = allProposals,
            votedProposals = votedProposals.keys.toSeq,
            includeList = includedProposals
          )
        indexedProposals <- elasticsearchProposalAPI.findProposalsByIds(selectedProposals, random = false)
      } yield {
        val indexedProposalsSorted = indexedProposals.sortBy(proposal => selectedProposals.indexOf(proposal.id))
        Some(
          SequenceResult(
            id = sequence.id,
            title = sequence.title,
            slug = sequence.slug,
            proposals = indexedProposalsSorted
              .map(
                indexed => ProposalResult(indexed, maybeUserId.contains(indexed.userId), votedProposals.get(indexed.id))
              )
          )
        )
      }
    }

    private def logStartSequenceUserHisory(sequenceSlug: Option[String],
                                           sequenceId: Option[SequenceId],
                                           maybeUserId: Option[UserId],
                                           includedProposals: Seq[ProposalId],
                                           requestContext: RequestContext): Unit = {

      (maybeUserId, requestContext.sessionId) match {
        case (Some(userId), _) =>
          userHistoryCoordinatorService.logHistory(
            LogUserStartSequenceEvent(
              userId,
              requestContext,
              UserAction(
                date = DateHelper.now(),
                actionType = LogUserStartSequenceEvent.action,
                arguments = StartSequenceParameters(sequenceSlug, sequenceId, includedProposals)
              )
            )
          )
        case (None, sessionId) =>
          sessionHistoryCoordinatorService.logHistory(
            LogSessionStartSequenceEvent(
              sessionId,
              requestContext,
              SessionAction(
                date = DateHelper.now(),
                actionType = LogSessionStartSequenceEvent.action,
                arguments = StartSequenceParameters(sequenceSlug, sequenceId, includedProposals)
              )
            )
          )
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
                        operationId: Option[OperationId],
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
            operationId = operationId,
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
                        operationId: Option[OperationId],
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
            operationId = operationId,
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
