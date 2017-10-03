package org.make.api.sequence

import java.time.ZonedDateTime

import akka.util.Timeout
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent}
import org.make.api.user.{UserResponse, UserServiceComponent}
import org.make.api.userhistory.UserHistoryCoordinatorServiceComponent
import org.make.core.proposal.ProposalId
import org.make.core.reference.{TagId, ThemeId}
import org.make.core.sequence._
import org.make.core.user._
import org.make.core.{RequestContext, SlugHelper}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

trait SequenceServiceComponent {
  def sequenceService: SequenceService
}

trait SequenceService {
  // toDo
  def getSequenceById(sequenceId: SequenceId, requestContext: RequestContext): Future[Option[Sequence]]
  def create(userId: UserId,
             requestContext: RequestContext,
             createdAt: ZonedDateTime,
             title: String,
             tagIds: Seq[TagId] = Seq.empty,
             themeIds: Seq[ThemeId] = Seq.empty): Future[Option[SequenceResponse]]
  def update(sequenceId: SequenceId,
             moderatorId: UserId,
             requestContext: RequestContext,
             updatedAt: ZonedDateTime,
             title: String): Future[Option[Sequence]]
  def addProposals(sequenceId: SequenceId,
                   moderatorId: UserId,
                   requestContext: RequestContext,
                   updatedAt: ZonedDateTime,
                   proposalids: Seq[ProposalId]): Future[Option[Sequence]]
  def removeProposals(sequenceId: SequenceId,
                      moderatorId: UserId,
                      requestContext: RequestContext,
                      updatedAt: ZonedDateTime,
                      proposalIds: Seq[ProposalId]): Future[Option[Sequence]]
  def getModerationSequenceById(sequenceId: SequenceId): Future[Option[SequenceResponse]]
}

trait DefaultSequenceServiceComponent extends SequenceServiceComponent {
  this: IdGeneratorComponent
    with UserHistoryCoordinatorServiceComponent
    with SessionHistoryCoordinatorServiceComponent
    with SequenceServiceComponent
    with SequenceCoordinatorServiceComponent
    with EventBusServiceComponent
    with UserServiceComponent =>

  override lazy val sequenceService: SequenceService = new SequenceService {

    implicit private val defaultTimeout: Timeout = new Timeout(5.seconds)

    override def create(userId: UserId,
                        requestContext: RequestContext,
                        createdAt: ZonedDateTime,
                        title: String,
                        tagIds: Seq[TagId],
                        themeIds: Seq[ThemeId]): Future[Option[SequenceResponse]] = {
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
            status = SequenceStatus.Published
          )
        )
        .flatMap(getModerationSequenceById)
    }

    override def addProposals(sequenceId: SequenceId,
                              userId: UserId,
                              requestContext: RequestContext,
                              updatedAt: ZonedDateTime,
                              proposalIds: Seq[ProposalId]): Future[Option[Sequence]] = {
      sequenceCoordinatorService.addProposals(
        AddProposalsSequenceCommand(
          sequenceId = sequenceId,
          requestContext = requestContext,
          proposalIds = proposalIds,
          moderatorId = userId
        )
      )
    }

    override def removeProposals(sequenceId: SequenceId,
                                 userId: UserId,
                                 requestContext: RequestContext,
                                 updatedAt: ZonedDateTime,
                                 proposalIds: Seq[ProposalId]): Future[Option[Sequence]] = {
      sequenceCoordinatorService.removeProposals(
        RemoveProposalsSequenceCommand(
          moderatorId = userId,
          sequenceId = sequenceId,
          proposalIds = proposalIds,
          requestContext = requestContext
        )
      )
    }

    override def update(sequenceId: SequenceId,
                        userId: UserId,
                        requestContext: RequestContext,
                        updatedAt: ZonedDateTime,
                        title: String): Future[Option[Sequence]] = {

      sequenceCoordinatorService.update(
        UpdateSequenceCommand(
          moderatorId = userId,
          sequenceId = sequenceId,
          requestContext = requestContext,
          title = title
        )
      )
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
