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

package org.make.api.sequence

import java.time.ZonedDateTime

import com.sksamuel.elastic4s.searches.sort.SortOrder
import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal._
import org.make.api.sessionhistory._
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent}
import org.make.api.user.{UserResponse, UserServiceComponent}
import org.make.api.userhistory.UserHistoryActor.{RequestUserVotedProposals, RequestVoteValues}
import org.make.api.userhistory._
import org.make.core.common.indexed.Sort
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.operation.OperationId
import org.make.core.proposal.{ProposalId, QuestionSearchFilter, RandomAlgorithm, SequencePoolSearchFilter}
import org.make.core.proposal.indexed.{IndexedProposal, ProposalElasticsearchFieldNames}
import org.make.core.reference.ThemeId
import org.make.core.sequence._
import org.make.core.sequence.indexed.SequencesSearchResult
import org.make.core.tag.TagId
import org.make.core.user._
import org.make.core.{proposal, DateHelper, RequestContext, SlugHelper}

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
                       tagsIds: Option[Seq[TagId]],
                       requestContext: RequestContext): Future[Option[SequenceResult]]
  def startNewSequence(maybeUserId: Option[UserId],
                       sequenceId: SequenceId,
                       includedProposals: Seq[ProposalId],
                       tagsIds: Option[Seq[TagId]],
                       requestContext: RequestContext): Future[Option[SequenceResult]]
  def getSequenceById(sequenceId: SequenceId, requestContext: RequestContext): Future[Option[Sequence]]
  def create(userId: UserId,
             requestContext: RequestContext,
             createdAt: ZonedDateTime,
             title: String,
             themeIds: Seq[ThemeId] = Seq.empty,
             operationId: Option[OperationId],
             searchable: Boolean): Future[Option[SequenceResponse]]
  def update(sequenceId: SequenceId,
             moderatorId: UserId,
             requestContext: RequestContext,
             title: Option[String],
             status: Option[SequenceStatus],
             operationId: Option[OperationId],
             themeIds: Seq[ThemeId]): Future[Option[SequenceResponse]]
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
                                  tagsIds: Option[Seq[TagId]],
                                  requestContext: RequestContext): Future[Option[SequenceResult]] = {
      logStartSequenceUserHisory(Some(slug), None, maybeUserId, includedProposals, requestContext)

      elasticsearchSequenceAPI.findSequenceBySlug(slug).flatMap {
        case None => Future.successful(None)
        case Some(sequence) =>
          sequenceConfigurationService.getSequenceConfiguration(sequence.id).flatMap { sequenceConfiguration =>
            startSequence(maybeUserId, sequenceConfiguration, includedProposals, tagsIds, requestContext)
          }
      }
    }

    override def startNewSequence(maybeUserId: Option[UserId],
                                  sequenceId: SequenceId,
                                  includedProposals: Seq[ProposalId],
                                  tagsIds: Option[Seq[TagId]],
                                  requestContext: RequestContext): Future[Option[SequenceResult]] = {
      logStartSequenceUserHisory(None, Some(sequenceId), maybeUserId, includedProposals, requestContext)

      sequenceConfigurationService.getSequenceConfiguration(sequenceId).flatMap { sequenceConfiguration =>
        startSequence(maybeUserId, sequenceConfiguration, includedProposals, tagsIds, requestContext)
      }
    }

    private def startSequence(maybeUserId: Option[UserId],
                              sequenceConfiguration: SequenceConfiguration,
                              includedProposalIds: Seq[ProposalId],
                              tagsIds: Option[Seq[TagId]],
                              requestContext: RequestContext): Future[Option[SequenceResult]] = {

      val futureIncludedProposals: Future[Seq[IndexedProposal]] = if (includedProposalIds.nonEmpty) {
        elasticsearchProposalAPI.findProposalsByIds(includedProposalIds, random = false)
      } else {
        Future.successful(Seq.empty)
      }

      val futureNewProposalsPool: Future[Seq[IndexedProposal]] =
        elasticsearchProposalAPI
          .searchProposals(
            proposal.SearchQuery(
              filters = Some(
                proposal.SearchFilters(
                  sequencePool = Some(SequencePoolSearchFilter("new")),
                  question = Some(QuestionSearchFilter(sequenceConfiguration.questionId)),
                  tags = tagsIds.map(proposal.TagsSearchFilter.apply)
                )
              ),
              limit = Some(sequenceConfiguration.sequenceSize * 3),
              sort = Some(Sort(Some(ProposalElasticsearchFieldNames.createdAt), Some(SortOrder.ASC)))
            )
          )
          .map(_.results)

      val futureTestedProposalsPool: Future[Seq[IndexedProposal]] =
        elasticsearchProposalAPI
          .searchProposals(
            proposal.SearchQuery(
              filters = Some(
                proposal.SearchFilters(
                  sequencePool = Some(SequencePoolSearchFilter("tested")),
                  question = Some(QuestionSearchFilter(sequenceConfiguration.questionId)),
                  tags = tagsIds.map(proposal.TagsSearchFilter.apply)
                )
              ),
              limit = Some(sequenceConfiguration.maxTestedProposalCount),
              sortAlgorithm = Some(RandomAlgorithm())
            )
          )
          .map(_.results)

      for {
        includedProposals  <- futureIncludedProposals
        allNewProposals    <- futureNewProposalsPool
        allTestedProposals <- futureTestedProposalsPool
        allVotes           <- futureVotedProposals(maybeUserId, requestContext)

        newProposals = allNewProposals.groupBy(_.userId).values.map(_.head).toSeq
        testedProposals = allTestedProposals.groupBy(_.userId).values.map(_.head).toSeq
        votes = allVotes.filter(id => newProposals.map(_.id).contains(id) || testedProposals.map(_.id).contains(id))

        selectedProposals = selectionAlgorithm
          .selectProposalsForSequence(
            sequenceConfiguration = sequenceConfiguration,
            includedProposals = includedProposals,
            newProposals = newProposals,
            testedProposals = testedProposals,
            votedProposals = votes
          )
        sequenceVotes <- futureVotedProposalsAndVotes(maybeUserId, requestContext, selectedProposals.map(_.id))
      } yield {
        Some(
          SequenceResult(
            id = sequenceConfiguration.sequenceId,
            title = "deprecated",
            slug = "deprecated",
            proposals = selectedProposals
              .map(
                indexed => ProposalResult(indexed, maybeUserId.contains(indexed.userId), sequenceVotes.get(indexed.id))
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
                                     requestContext: RequestContext): Future[Seq[ProposalId]] =
      maybeUserId.map { userId =>
        userHistoryCoordinatorService.retrieveVotedProposals(RequestUserVotedProposals(userId))
      }.getOrElse {
        sessionHistoryCoordinatorService
          .retrieveVotedProposals(RequestSessionVotedProposals(requestContext.sessionId))
      }

    private def futureVotedProposalsAndVotes(
      maybeUserId: Option[UserId],
      requestContext: RequestContext,
      proposals: Seq[ProposalId]
    ): Future[Map[ProposalId, VoteAndQualifications]] =
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
                        themeIds: Seq[ThemeId]): Future[Option[SequenceResponse]] = {

      sequenceCoordinatorService
        .update(
          UpdateSequenceCommand(
            moderatorId = userId,
            sequenceId = sequenceId,
            requestContext = requestContext,
            title = title,
            status = status,
            operationId = operationId,
            themeIds = themeIds
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
            proposalIds = sequence.proposalIds,
            sequenceTranslation = sequence.sequenceTranslation,
            events = events
          )
        )
      }
    }
  }
}
