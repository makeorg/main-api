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

import grizzled.slf4j.Logging
import org.make.api.operation.OperationOfQuestionSearchEngineComponent
import org.make.api.proposal._
import org.make.api.segment.SegmentServiceComponent
import org.make.api.sessionhistory._
import org.make.api.technical.security.SecurityConfigurationComponent
import org.make.api.userhistory._
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal._
import org.make.core.proposal.indexed.{IndexedProposal, SequencePool}
import org.make.core.question.QuestionId
import org.make.core.user._
import org.make.core.{proposal, DateHelper, RequestContext}
import eu.timepit.refined.auto._
import org.make.api.demographics.DemographicsCardServiceComponent
import org.make.core.demographics.DemographicsCardId
import org.make.core.sequence.SequenceConfiguration
import org.make.core.session.SessionId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DefaultSequenceServiceComponent extends SequenceServiceComponent {
  this: DemographicsCardServiceComponent
    with OperationOfQuestionSearchEngineComponent
    with ProposalSearchEngineComponent
    with ProposalServiceComponent
    with SecurityConfigurationComponent
    with SegmentServiceComponent
    with SequenceConfigurationComponent
    with SessionHistoryCoordinatorServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with Logging =>

  override lazy val sequenceService: DefaultSequenceService = new DefaultSequenceService

  class DefaultSequenceService extends SequenceService {

    override def startNewSequence[T: SequenceBehaviourProvider](
      behaviourParam: T,
      maybeUserId: Option[UserId],
      questionId: QuestionId,
      includedProposalsIds: Seq[ProposalId],
      requestContext: RequestContext,
      cardId: Option[DemographicsCardId],
      token: Option[String],
      configurationOverride: Option[SequenceConfiguration] = None
    ): Future[SequenceResult] = {
      val log = logStartSequenceUserHistory(questionId, maybeUserId, includedProposalsIds, requestContext)
      val votedProposals =
        sessionHistoryCoordinatorService.retrieveVotedProposals(requestContext.sessionId)
      val resolveBehaviour = createBehaviour(behaviourParam, questionId, requestContext, configurationOverride)
      def fullSequence(behaviour: SequenceBehaviour, proposalsToExclude: Seq[ProposalId]) =
        simpleSequence(includedProposalsIds, behaviour, proposalsToExclude, Some(requestContext.sessionId))
      for {
        _                  <- log
        proposalsToExclude <- votedProposals
        behaviour          <- resolveBehaviour
        sequenceProposals  <- fullSequence(behaviour, proposalsToExclude)
        sequenceVotes      <- votesForProposals(maybeUserId, requestContext, sequenceProposals.map(_.id))
        demographicsCard   <- demographicsCardService.getOrPickRandom(cardId, token, questionId)
      } yield SequenceResult(
        proposals = sequenceProposals
          .map(indexed => {
            val proposalKey =
              proposalService.generateProposalKeyHash(
                indexed.id,
                requestContext.sessionId,
                requestContext.location,
                securityConfiguration.secureVoteSalt
              )
            ProposalResponse(indexed, maybeUserId.contains(indexed.userId), sequenceVotes.get(indexed.id), proposalKey)
          }),
        demographics = demographicsCard
      )
    }

    override def simpleSequence(
      includedProposalsIds: Seq[ProposalId],
      behaviour: SequenceBehaviour,
      proposalsToExclude: Seq[ProposalId],
      sessionId: Option[SessionId]
    ): Future[Seq[IndexedProposal]] = {
      def logFallback(count: Int, questionId: QuestionId): Unit = sessionId match {
        case Some(id) =>
          logger.warn(
            s"Sequence fallback missing $count proposals for sessionId ${id.value} and question ${questionId.value}"
          )
        case None => logger.warn(s"Sequence fallback missing $count proposals for question ${questionId.value}")
      }
      chooseSequenceProposals(includedProposalsIds, behaviour, proposalsToExclude, logFallback)
    }

    private def createBehaviour[T: SequenceBehaviourProvider](
      behaviourParam: T,
      questionId: QuestionId,
      requestContext: RequestContext,
      overrideConfiguration: Option[SequenceConfiguration]
    ): Future[SequenceBehaviour] = {
      val futureMaybeSegment = segmentService.resolveSegment(requestContext)
      val futureConfig = overrideConfiguration match {
        case Some(config) => Future.successful(config)
        case None         => sequenceConfigurationService.getSequenceConfigurationByQuestionId(questionId)
      }
      for {
        maybeSegment <- futureMaybeSegment
        config       <- futureConfig
      } yield SequenceBehaviourProvider[T].behaviour(behaviourParam, config, questionId, maybeSegment)
    }

    private def logStartSequenceUserHistory(
      questionId: QuestionId,
      maybeUserId: Option[UserId],
      includedProposalsIds: Seq[ProposalId],
      requestContext: RequestContext
    ): Future[Unit] = {
      (maybeUserId, requestContext.sessionId) match {
        case (Some(userId), _) =>
          userHistoryCoordinatorService.logTransactionalHistory(
            LogUserStartSequenceEvent(
              userId,
              requestContext,
              UserAction(
                date = DateHelper.now(),
                actionType = LogUserStartSequenceEvent.action,
                arguments = StartSequenceParameters(None, Some(questionId), None, includedProposalsIds)
              )
            )
          )
        case (None, sessionId) =>
          sessionHistoryCoordinatorService.logTransactionalHistory(
            LogSessionStartSequenceEvent(
              sessionId,
              requestContext,
              SessionAction(
                date = DateHelper.now(),
                actionType = LogSessionStartSequenceEvent.action,
                arguments = StartSequenceParameters(None, Some(questionId), None, includedProposalsIds)
              )
            )
          )
      }
    }

    private def futureIncludedProposals(includedProposalsIds: Seq[ProposalId]): Future[Seq[IndexedProposal]] =
      if (includedProposalsIds.nonEmpty) {
        elasticsearchProposalAPI
          .searchProposals(
            proposal.SearchQuery(
              filters = Some(proposal.SearchFilters(proposal = Some(ProposalSearchFilter(includedProposalsIds)))),
              limit = Some(includedProposalsIds.size)
            )
          )
          .map(_.results)
      } else {
        Future.successful(Seq.empty)
      }

    private def chooseSequenceProposals(
      includedProposalsIds: Seq[ProposalId],
      behaviour: SequenceBehaviour,
      proposalsToExclude: Seq[ProposalId],
      logFallback: (Int, QuestionId) => Unit
    ): Future[Seq[IndexedProposal]] = {

      def futureFallbackProposals(
        excluded: Seq[ProposalId],
        selectedProposals: Seq[IndexedProposal]
      ): Future[Seq[IndexedProposal]] = {
        behaviour.fallbackProposals(
          currentSequenceSize = selectedProposals.size,
          search = searchProposals(
            excluded ++ selectedProposals.map(_.id),
            behaviour.specificConfiguration.sequenceSize - selectedProposals.size
          ),
          logFallback
        )
      }

      val excluded = proposalsToExclude ++ includedProposalsIds
      for {
        includedProposals <- futureIncludedProposals(includedProposalsIds)
        newProposals <- behaviour.newProposals(
          searchProposals(excluded, behaviour.specificConfiguration.sequenceSize * 3)
        )
        testedProposals <- behaviour.testedProposals(
          searchProposals(excluded, behaviour.specificConfiguration.maxTestedProposalCount)
        )
        selectedProposals = behaviour.selectProposals(includedProposals, newProposals, testedProposals)
        fallbackProposals <- futureFallbackProposals(excluded, selectedProposals)
      } yield selectedProposals ++ fallbackProposals
    }

    private def votesForProposals(
      maybeUserId: Option[UserId],
      requestContext: RequestContext,
      proposals: Seq[ProposalId]
    ): Future[Map[ProposalId, VoteAndQualifications]] =
      maybeUserId.map { userId =>
        userHistoryCoordinatorService.retrieveVoteAndQualifications(userId, proposals)
      }.getOrElse {
        sessionHistoryCoordinatorService.retrieveVoteAndQualifications(requestContext.sessionId, proposals)
      }

    private def searchProposals(excluded: Seq[ProposalId], limit: Int)(
      questionId: QuestionId,
      maybeSegment: Option[String],
      maybePool: Option[SequencePool],
      baseQuery: proposal.SearchQuery,
      sortAlgorithm: SortAlgorithm
    ): Future[Seq[IndexedProposal]] = {
      val poolFilter = maybePool.map(SequencePoolSearchFilter.apply)
      val query = baseQuery.copy(
        filters = SearchFilters.merge(
          baseQuery.filters,
          Some(
            SearchFilters(
              sequencePool = maybeSegment.fold(poolFilter)(_ => None),
              sequenceSegmentPool = maybeSegment.flatMap(_   => poolFilter),
              question = Some(QuestionSearchFilter(Seq(questionId))),
              segment = maybeSegment.map(SegmentSearchFilter.apply)
            )
          )
        ),
        excludes = Some(proposal.SearchFilters(proposal = Some(ProposalSearchFilter(excluded)))),
        limit = Some(limit),
        sortAlgorithm = Some(sortAlgorithm)
      )

      elasticsearchProposalAPI.searchProposals(query).map(_.results)
    }
  }
}
