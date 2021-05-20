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
import org.make.api.sequence.SequenceBehaviour.ConsensusParam
import org.make.api.sessionhistory._
import org.make.api.technical.security.{SecurityConfigurationComponent, SecurityHelper}
import org.make.api.userhistory.UserHistoryActor.{RequestUserVotedProposals, RequestVoteValues}
import org.make.api.userhistory._
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal._
import org.make.core.proposal.indexed.{IndexedProposal, SequencePool, Zone}
import org.make.core.question.QuestionId
import org.make.core.tag.TagId
import org.make.core.user._
import org.make.core.{proposal, DateHelper, RequestContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SequenceServiceComponent {
  def sequenceService: SequenceService
}

trait SequenceService {
  def startNewSequence[T: SequenceBehaviourProvider](
    behaviourParam: T,
    maybeUserId: Option[UserId],
    questionId: QuestionId,
    includedProposalsIds: Seq[ProposalId],
    requestContext: RequestContext
  ): Future[SequenceResult]

  def startNewSequence(
    zone: Option[Zone],
    keyword: Option[ProposalKeywordKey],
    maybeUserId: Option[UserId],
    questionId: QuestionId,
    includedProposalIds: Seq[ProposalId],
    tagsIds: Option[Seq[TagId]],
    requestContext: RequestContext
  ): Future[SequenceResult]
}

trait DefaultSequenceServiceComponent extends SequenceServiceComponent {
  this: UserHistoryCoordinatorServiceComponent
    with ProposalSearchEngineComponent
    with SecurityConfigurationComponent
    with SegmentServiceComponent
    with SequenceConfigurationComponent
    with SessionHistoryCoordinatorServiceComponent
    with OperationOfQuestionSearchEngineComponent
    with Logging =>

  override lazy val sequenceService: DefaultSequenceService = new DefaultSequenceService

  class DefaultSequenceService extends SequenceService {

    def startNewSequence[T: SequenceBehaviourProvider](
      behaviourParam: T,
      maybeUserId: Option[UserId],
      questionId: QuestionId,
      includedProposalsIds: Seq[ProposalId],
      requestContext: RequestContext
    ): Future[SequenceResult] = {
      val log = logStartSequenceUserHistory(questionId, maybeUserId, includedProposalsIds, requestContext)
      val votedProposals = futureVotedProposals(maybeUserId = maybeUserId, requestContext = requestContext)
      val behaviour = createBehaviour(behaviourParam, questionId, requestContext)
      for {
        _                  <- log
        proposalsToExclude <- votedProposals
        behaviour          <- behaviour
        sequenceProposals  <- chooseSequenceProposals(includedProposalsIds, behaviour, proposalsToExclude)
        sequenceVotes      <- votesForProposals(maybeUserId, requestContext, sequenceProposals.map(_.id))
      } yield SequenceResult(proposals = sequenceProposals
        .map(indexed => {
          val proposalKey =
            SecurityHelper.generateProposalKeyHash(
              indexed.id,
              requestContext.sessionId,
              requestContext.location,
              securityConfiguration.secureVoteSalt
            )
          ProposalResponse(indexed, maybeUserId.contains(indexed.userId), sequenceVotes.get(indexed.id), proposalKey)
        })
      )
    }

    def createBehaviour[T: SequenceBehaviourProvider](
      behaviourParam: T,
      questionId: QuestionId,
      requestContext: RequestContext
    ): Future[SequenceBehaviour] = {
      val futureMaybeSegment = segmentService.resolveSegment(requestContext)
      val futureConfig = sequenceConfigurationService.getSequenceConfigurationByQuestionId(questionId)
      for {
        maybeSegment <- futureMaybeSegment
        config       <- futureConfig
      } yield SequenceBehaviourProvider[T].behaviour(
        behaviourParam,
        config,
        questionId,
        maybeSegment,
        requestContext.sessionId
      )
    }

    @Deprecated
    override def startNewSequence(
      zone: Option[Zone],
      keyword: Option[ProposalKeywordKey],
      maybeUserId: Option[UserId],
      questionId: QuestionId,
      includedProposalIds: Seq[ProposalId],
      tagsIds: Option[Seq[TagId]],
      requestContext: RequestContext
    ): Future[SequenceResult] = {
      for {
        _                  <- logStartSequenceUserHistory(questionId, maybeUserId, includedProposalIds, requestContext)
        behaviour          <- resolveBehaviour(questionId, requestContext, zone, keyword, tagsIds)
        proposalsToExclude <- futureVotedProposals(maybeUserId = maybeUserId, requestContext = requestContext)
        sequenceProposals  <- chooseSequenceProposals(includedProposalIds, behaviour, proposalsToExclude)
        sequenceVotes      <- votesForProposals(maybeUserId, requestContext, sequenceProposals.map(_.id))
      } yield SequenceResult(proposals = sequenceProposals
        .map(indexed => {
          val proposalKey =
            SecurityHelper.generateProposalKeyHash(
              indexed.id,
              requestContext.sessionId,
              requestContext.location,
              securityConfiguration.secureVoteSalt
            )
          ProposalResponse(indexed, maybeUserId.contains(indexed.userId), sequenceVotes.get(indexed.id), proposalKey)
        })
      )
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

    @Deprecated
    private def futureTop20ConsensusThreshold(questionId: QuestionId): Future[Option[Double]] =
      elasticsearchOperationOfQuestionAPI
        .findOperationOfQuestionById(questionId)
        .map(_.flatMap(_.top20ConsensusThreshold))

    private def futureVotedProposals(
      maybeUserId: Option[UserId],
      requestContext: RequestContext
    ): Future[Seq[ProposalId]] =
      maybeUserId.map { userId =>
        userHistoryCoordinatorService.retrieveVotedProposals(RequestUserVotedProposals(userId = userId))
      }.getOrElse {
        sessionHistoryCoordinatorService
          .retrieveVotedProposals(RequestSessionVotedProposals(requestContext.sessionId))
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

    @Deprecated
    def resolveBehaviour(
      questionId: QuestionId,
      requestContext: RequestContext,
      zone: Option[Zone],
      keyword: Option[ProposalKeywordKey],
      tagsIds: Option[Seq[TagId]]
    ): Future[SequenceBehaviour] = {
      val futureParams = for {
        maybeSegment <- segmentService.resolveSegment(requestContext)
        config       <- sequenceConfigurationService.getSequenceConfigurationByQuestionId(questionId)
      } yield (maybeSegment, config)
      futureParams.flatMap {
        case (maybeSegment, config) =>
          (keyword, tagsIds, zone) match {
            case (None, None, None) =>
              Future.successful(SequenceBehaviour.Standard(config, questionId, maybeSegment, requestContext.sessionId))
            case (Some(kw), _, _) =>
              Future.successful(
                SequenceBehaviour.Keyword(kw, config, questionId, maybeSegment, requestContext.sessionId)
              )
            case (_, _, Some(Zone.Consensus)) =>
              futureTop20ConsensusThreshold(questionId).map(
                threshold =>
                  SequenceBehaviour
                    .Consensus(ConsensusParam(threshold), config, questionId, maybeSegment, requestContext.sessionId)
              )
            case (_, _, Some(_)) =>
              Future.successful(
                SequenceBehaviour.Controversy(config, questionId, maybeSegment, requestContext.sessionId)
              )
            case (_, tags, _) =>
              Future.successful(
                SequenceBehaviour.Tags(tags, config, questionId, maybeSegment, requestContext.sessionId)
              )
          }
      }
    }

    private def chooseSequenceProposals(
      includedProposalsIds: Seq[ProposalId],
      behaviour: SequenceBehaviour,
      proposalsToExclude: Seq[ProposalId]
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
          )
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
        userHistoryCoordinatorService.retrieveVoteAndQualifications(RequestVoteValues(userId, proposals))
      }.getOrElse {
        sessionHistoryCoordinatorService.retrieveVoteAndQualifications(
          RequestSessionVoteValues(requestContext.sessionId, proposals)
        )
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
