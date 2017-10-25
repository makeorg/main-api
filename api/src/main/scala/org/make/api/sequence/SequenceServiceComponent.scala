package org.make.api.sequence

import java.time.ZonedDateTime

import akka.util.Timeout
import org.make.api.proposal.ProposalServiceComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent}
import org.make.api.user.{UserResponse, UserServiceComponent}
import org.make.api.userhistory._
import org.make.core.proposal.ProposalId
import org.make.core.reference.{TagId, ThemeId}
import org.make.core.sequence._
import org.make.core.sequence.indexed.{IndexedStartSequence, SequencesSearchResult}
import org.make.core.user._
import org.make.core.{DateHelper, RequestContext, SlugHelper}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

trait SequenceServiceComponent {
  def sequenceService: SequenceService
}

trait SequenceService {
  def search(maybeUserId: Option[UserId],
             query: SearchQuery,
             requestContext: RequestContext): Future[SequencesSearchResult]
  def startNewSequence(maybeUserId: Option[UserId],
                       slug: String,
                       excludedProposals: Seq[ProposalId] = Seq.empty,
                       requestContext: RequestContext): Future[Option[IndexedStartSequence]]
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
    with UserHistoryCoordinatorServiceComponent
    with SessionHistoryCoordinatorServiceComponent
    with SequenceServiceComponent
    with SequenceSearchEngineComponent
    with SequenceCoordinatorServiceComponent
    with EventBusServiceComponent
    with UserServiceComponent =>

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
                                  excludedProposals: Seq[ProposalId] = Seq.empty,
                                  requestContext: RequestContext): Future[Option[IndexedStartSequence]] = {

      val query = SearchStartSequenceRequest(slug = slug).toSearchQuery
      maybeUserId.foreach { userId =>
        userHistoryCoordinatorService.logHistory(
          LogUserStartSequenceEvent(
            userId,
            requestContext,
            UserAction(DateHelper.now(), LogUserStartSequenceEvent.action, StartSequenceParameters(query))
          )
        )
      }
      elasticsearchSequenceAPI.getStartSequence(query)
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