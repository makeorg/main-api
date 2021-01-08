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

import cats.syntax.list._
import com.sksamuel.elastic4s.searches.sort.SortOrder
import com.typesafe.scalalogging.StrictLogging
import org.make.api.proposal._
import org.make.api.segment.SegmentServiceComponent
import org.make.api.sessionhistory._
import org.make.api.technical.security.{SecurityConfigurationComponent, SecurityHelper}
import org.make.api.technical.MakeRandom
import org.make.api.userhistory.UserHistoryActor.{RequestUserVotedProposals, RequestVoteValues}
import org.make.api.userhistory._
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.indexed.{IndexedProposal, SequencePool, Zone}
import org.make.core.proposal.{
  CreationDateAlgorithm,
  ProposalId,
  ProposalSearchFilter,
  QuestionSearchFilter,
  RandomAlgorithm,
  SegmentFirstAlgorithm,
  SegmentSearchFilter,
  SequencePoolSearchFilter,
  SortAlgorithm,
  ZoneSearchFilter
}
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
  def startNewSequence(
    zone: Option[Zone],
    maybeUserId: Option[UserId],
    questionId: QuestionId,
    includedProposals: Seq[ProposalId],
    tagsIds: Option[Seq[TagId]],
    requestContext: RequestContext
  ): Future[SequenceResult]
}

trait DefaultSequenceServiceComponent extends SequenceServiceComponent {
  this: UserHistoryCoordinatorServiceComponent
    with SessionHistoryCoordinatorServiceComponent
    with ProposalSearchEngineComponent
    with SelectionAlgorithmComponent
    with SequenceConfigurationComponent
    with SecurityConfigurationComponent
    with SegmentServiceComponent
    with StrictLogging =>

  override lazy val sequenceService: SequenceService = new DefaultSequenceService

  class DefaultSequenceService extends SequenceService {

    override def startNewSequence(
      zone: Option[Zone],
      maybeUserId: Option[UserId],
      questionId: QuestionId,
      includedProposals: Seq[ProposalId],
      tagsIds: Option[Seq[TagId]],
      requestContext: RequestContext
    ): Future[SequenceResult] = {

      val futureIncludedProposals: Future[Seq[IndexedProposal]] = if (includedProposals.nonEmpty) {
        elasticsearchProposalAPI.findProposalsByIds(includedProposals, size = 1000, random = false)
      } else {
        Future.successful(Seq.empty)
      }

      for {
        _                     <- logStartSequenceUserHistory(Some(questionId), maybeUserId, includedProposals, requestContext)
        proposalsVoted        <- futureVotedProposals(maybeUserId = maybeUserId, requestContext = requestContext)
        sequenceConfiguration <- getSequenceConfiguration(zone, questionId)
        includedProposals     <- futureIncludedProposals // TODO shouldn't they be excluded from searches?
        maybeSegment          <- segmentService.resolveSegment(requestContext)
        searchProposals = (
          maybePool: Option[SequencePool],
          excluded: Seq[ProposalId],
          limit: Int,
          sortAlgorithm: SortAlgorithm
        ) => {
          val (poolSearch, segmentPoolSearch, tagsSearch, segmentSearch) = (maybePool, maybeSegment) match {
            case (None, _) => (None, None, None, None)
            case (Some(pool), None) =>
              (
                Some(SequencePoolSearchFilter(pool)),
                None,
                tagsIds.map(proposal.TagsSearchFilter.apply),
                maybeSegment.map(SegmentSearchFilter.apply)
              )
            case (Some(pool), Some(_)) =>
              (
                None,
                Some(SequencePoolSearchFilter(pool)),
                tagsIds.map(proposal.TagsSearchFilter.apply),
                maybeSegment.map(SegmentSearchFilter.apply)
              )
          }
          elasticsearchProposalAPI
            .searchProposals(
              proposal.SearchQuery(
                filters = Some(
                  proposal.SearchFilters(
                    sequencePool = poolSearch,
                    sequenceSegmentPool = segmentPoolSearch,
                    question = Some(QuestionSearchFilter(Seq(questionId))),
                    tags = tagsSearch,
                    segment = segmentSearch,
                    zone = zone.map(ZoneSearchFilter)
                  )
                ),
                excludes = Some(proposal.SearchFilters(proposal = Some(ProposalSearchFilter(excluded)))),
                limit = Some(limit),
                sortAlgorithm = Some(sortAlgorithm)
              )
            )
            .map(_.results)
        }
        allNewProposals <- searchProposals(
          Some(SequencePool.New),
          proposalsVoted,
          sequenceConfiguration.sequenceSize * 3,
          CreationDateAlgorithm(SortOrder.Asc)
        )
        allTestedProposals <- searchProposals(
          Some(SequencePool.Tested),
          proposalsVoted,
          sequenceConfiguration.maxTestedProposalCount,
          RandomAlgorithm(MakeRandom.nextInt())
        )

        newProposals = allNewProposals.toList.groupByNel(_.userId).values.map(_.head).toSeq
        testedProposals = allTestedProposals.toList.groupByNel(_.userId).values.map(_.head).toSeq
        // votes should equal proposalsVoted as new/tested pools are filtered from proposalsVoted
        votes = proposalsVoted.filter(
          id => newProposals.map(_.id).contains(id) || testedProposals.map(_.id).contains(id)
        )

        (selectionAlgorithm, newCandidates, testedCandidates) = sequenceConfiguration.selectionAlgorithmName match {
          case SelectionAlgorithmName.Bandit =>
            (banditSelectionAlgorithm, newProposals, testedProposals)
          case SelectionAlgorithmName.RoundRobin =>
            (roundRobinSelectionAlgorithm, allNewProposals, allTestedProposals)
        }

        selectedProposals = selectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = sequenceConfiguration,
          includedProposals = includedProposals,
          newProposals = newCandidates,
          testedProposals = testedCandidates,
          votedProposals = votes,
          userSegment = maybeSegment
        )
        fallbackProposals <- if (selectedProposals.size < sequenceConfiguration.sequenceSize) {
          logger.warn(
            s"Sequence fallback for user ${requestContext.sessionId.value} and question ${sequenceConfiguration.questionId.value}"
          )
          val sortAlgorithm: SortAlgorithm = maybeSegment
            .map[SortAlgorithm](SegmentFirstAlgorithm)
            .getOrElse[SortAlgorithm](CreationDateAlgorithm(SortOrder.Desc))

          searchProposals(
            None,
            votes ++ selectedProposals.map(_.id),
            sequenceConfiguration.sequenceSize - selectedProposals.size,
            sortAlgorithm
          )
        } else {
          Future.successful(Nil)
        }
        sequenceProposals = selectedProposals ++ fallbackProposals
        sequenceVotes <- futureVotedProposalsAndVotes(maybeUserId, requestContext, sequenceProposals.map(_.id))
      } yield SequenceResult(
        id = sequenceConfiguration.sequenceId,
        title = "deprecated",
        slug = "deprecated",
        proposals = sequenceProposals
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
      questionId: Option[QuestionId],
      maybeUserId: Option[UserId],
      includedProposals: Seq[ProposalId],
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
                arguments = StartSequenceParameters(None, questionId, None, includedProposals)
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
                arguments = StartSequenceParameters(None, questionId, None, includedProposals)
              )
            )
          )
      }

    }

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

    private def getSequenceConfiguration(zone: Option[Zone], questionId: QuestionId): Future[SequenceConfiguration] = {
      sequenceConfigurationService
        .getSequenceConfigurationByQuestionId(questionId)
        .map(
          config =>
            zone match {
              case Some(Zone.Consensus | Zone.Controversy) =>
                config.copy(selectionAlgorithmName = SelectionAlgorithmName.Bandit)
              case _ => config
            }
        )
    }

  }
}
