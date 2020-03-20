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

import com.sksamuel.elastic4s.searches.sort.SortOrder
import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal._
import org.make.api.segment.SegmentServiceComponent
import org.make.api.sessionhistory._
import org.make.api.technical.security.{SecurityConfigurationComponent, SecurityHelper}
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, MakeRandom}
import org.make.api.user.UserServiceComponent
import org.make.api.userhistory.UserHistoryActor.{RequestUserVotedProposals, RequestVoteValues}
import org.make.api.userhistory._
import org.make.core.common.indexed.Sort
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.indexed.{IndexedProposal, ProposalElasticsearchFieldNames, SequencePool}
import org.make.core.proposal.{
  ProposalId,
  ProposalSearchFilter,
  QuestionSearchFilter,
  RandomAlgorithm,
  SegmentSearchFilter,
  SequencePoolSearchFilter
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
  def startNewSequence(maybeUserId: Option[UserId],
                       questionId: QuestionId,
                       includedProposals: Seq[ProposalId],
                       tagsIds: Option[Seq[TagId]],
                       requestContext: RequestContext): Future[Option[SequenceResult]]
}

trait DefaultSequenceServiceComponent extends SequenceServiceComponent {
  this: IdGeneratorComponent
    with ProposalServiceComponent
    with ProposalCoordinatorServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with SessionHistoryCoordinatorServiceComponent
    with SequenceServiceComponent
    with ProposalSearchEngineComponent
    with EventBusServiceComponent
    with UserServiceComponent
    with MakeSettingsComponent
    with SelectionAlgorithmComponent
    with SequenceConfigurationComponent
    with SecurityConfigurationComponent
    with SegmentServiceComponent
    with StrictLogging =>

  override lazy val sequenceService: SequenceService = new DefaultSequenceService

  class DefaultSequenceService extends SequenceService {

    override def startNewSequence(maybeUserId: Option[UserId],
                                  questionId: QuestionId,
                                  includedProposals: Seq[ProposalId],
                                  tagsIds: Option[Seq[TagId]],
                                  requestContext: RequestContext): Future[Option[SequenceResult]] = {
      logStartSequenceUserHistory(Some(questionId), maybeUserId, includedProposals, requestContext)

      futureVotedProposals(maybeUserId = maybeUserId, proposalsIds = None, requestContext = requestContext).flatMap {
        proposalsVoted =>
          sequenceConfigurationService.getSequenceConfigurationByQuestionId(questionId).flatMap {
            sequenceConfiguration =>
              startSequence(
                maybeUserId,
                sequenceConfiguration,
                includedProposals,
                proposalsVoted,
                tagsIds,
                requestContext
              ).flatMap {
                case Some(sequenceResult) if sequenceResult.proposals.size < sequenceConfiguration.sequenceSize =>
                  fallbackEmptySequence(
                    maybeUserId,
                    sequenceConfiguration,
                    sequenceResult.proposals,
                    proposalsVoted,
                    requestContext
                  )
                case other => Future.successful(other)
              }
          }

      }
    }

    private def fallbackEmptySequence(maybeUserId: Option[UserId],
                                      sequenceConfiguration: SequenceConfiguration,
                                      initialSequenceProposals: Seq[ProposalResponse],
                                      proposalsVoted: Seq[ProposalId],
                                      requestContext: RequestContext): Future[Option[SequenceResult]] = {

      logger.warn(
        s"Sequence fallback for user ${requestContext.sessionId} and question ${sequenceConfiguration.questionId}"
      )

      val proposalsToExclude: Seq[ProposalId] = proposalsVoted ++ initialSequenceProposals.map(_.id)

      for {
        selectedProposals <- elasticsearchProposalAPI
          .searchProposals(
            proposal.SearchQuery(
              filters = Some(
                proposal.SearchFilters(question = Some(QuestionSearchFilter(Seq(sequenceConfiguration.questionId))))
              ),
              excludes = Some(proposal.SearchFilters(proposal = Some(ProposalSearchFilter(proposalsToExclude)))),
              limit = Some(sequenceConfiguration.sequenceSize - initialSequenceProposals.size),
              sort = Some(Sort(Some(ProposalElasticsearchFieldNames.createdAt), Some(SortOrder.DESC)))
            )
          )
        sequenceVotes <- futureVotedProposalsAndVotes(maybeUserId, requestContext, selectedProposals.results.map(_.id))
      } yield {
        Some(
          SequenceResult(
            id = sequenceConfiguration.sequenceId,
            title = "deprecated",
            slug = "deprecated",
            proposals = (selectedProposals.results.map { indexed =>
              {
                val proposalKey =
                  SecurityHelper.generateProposalKeyHash(
                    indexed.id,
                    requestContext.sessionId,
                    requestContext.location,
                    securityConfiguration.secureVoteSalt
                  )
                ProposalResponse(
                  indexed,
                  maybeUserId.contains(indexed.userId),
                  sequenceVotes.get(indexed.id),
                  proposalKey
                )
              }
            } ++ initialSequenceProposals).distinctBy(_.id)
          )
        )
      }
    }

    private def startSequence(maybeUserId: Option[UserId],
                              sequenceConfiguration: SequenceConfiguration,
                              includedProposalIds: Seq[ProposalId],
                              proposalsVoted: Seq[ProposalId],
                              tagsIds: Option[Seq[TagId]],
                              requestContext: RequestContext): Future[Option[SequenceResult]] = {

      val futureIncludedProposals: Future[Seq[IndexedProposal]] = if (includedProposalIds.nonEmpty) {
        elasticsearchProposalAPI.findProposalsByIds(includedProposalIds, size = 1000, random = false)
      } else {
        Future.successful(Seq.empty)
      }

      def futureNewProposalsPool(maybeSegment: Option[String],
                                 proposalsToExclude: Seq[ProposalId]): Future[Seq[IndexedProposal]] = {
        val (poolSearch, segmentPoolSearch) = maybeSegment match {
          case None    => (Some(SequencePoolSearchFilter(SequencePool.New)), None)
          case Some(_) => (None, Some(SequencePoolSearchFilter(SequencePool.New)))
        }
        elasticsearchProposalAPI
          .searchProposals(
            proposal.SearchQuery(
              filters = Some(
                proposal.SearchFilters(
                  sequencePool = poolSearch,
                  sequenceSegmentPool = segmentPoolSearch,
                  question = Some(QuestionSearchFilter(Seq(sequenceConfiguration.questionId))),
                  tags = tagsIds.map(proposal.TagsSearchFilter.apply),
                  segment = maybeSegment.map(SegmentSearchFilter.apply)
                )
              ),
              excludes = Some(proposal.SearchFilters(proposal = Some(ProposalSearchFilter(proposalsToExclude)))),
              limit = Some(sequenceConfiguration.sequenceSize * 3),
              sort = Some(Sort(Some(ProposalElasticsearchFieldNames.createdAt), Some(SortOrder.ASC)))
            )
          )
          .map(_.results)
      }

      def futureTestedProposalsPool(maybeSegment: Option[String],
                                    proposalsToExclude: Seq[ProposalId]): Future[Seq[IndexedProposal]] = {
        val (poolSearch, segmentPoolSearch) = maybeSegment match {
          case None    => (Some(SequencePoolSearchFilter(SequencePool.Tested)), None)
          case Some(_) => (None, Some(SequencePoolSearchFilter(SequencePool.Tested)))
        }
        elasticsearchProposalAPI
          .searchProposals(
            proposal.SearchQuery(
              filters = Some(
                proposal.SearchFilters(
                  sequencePool = poolSearch,
                  sequenceSegmentPool = segmentPoolSearch,
                  question = Some(QuestionSearchFilter(Seq(sequenceConfiguration.questionId))),
                  tags = tagsIds.map(proposal.TagsSearchFilter.apply),
                  segment = maybeSegment.map(SegmentSearchFilter.apply)
                )
              ),
              excludes = Some(proposal.SearchFilters(proposal = Some(ProposalSearchFilter(proposalsToExclude)))),
              limit = Some(sequenceConfiguration.maxTestedProposalCount),
              sortAlgorithm = Some(RandomAlgorithm(MakeRandom.random.nextInt()))
            )
          )
          .map(_.results)
      }

      for {
        includedProposals  <- futureIncludedProposals
        maybeSegment       <- segmentService.resolveSegment(requestContext)
        allNewProposals    <- futureNewProposalsPool(maybeSegment, proposalsVoted)
        allTestedProposals <- futureTestedProposalsPool(maybeSegment, proposalsVoted)

        newProposals = allNewProposals.groupBy(_.userId).values.map(_.head).toSeq
        testedProposals = allTestedProposals.groupBy(_.userId).values.map(_.head).toSeq
        // votes should equal proposalsVoted as new/tested pools are filtered from proposalsVoted
        votes = proposalsVoted.filter(
          id => newProposals.map(_.id).contains(id) || testedProposals.map(_.id).contains(id)
        )

        selectedProposals = sequenceConfiguration.selectionAlgorithmName match {
          case SelectionAlgorithmName.Bandit =>
            banditSelectionAlgorithm.selectProposalsForSequence(
              sequenceConfiguration = sequenceConfiguration,
              includedProposals = includedProposals,
              newProposals = newProposals,
              testedProposals = testedProposals,
              votedProposals = votes,
              userSegment = maybeSegment
            )
          case SelectionAlgorithmName.RoundRobin =>
            roundRobinSelectionAlgorithm.selectProposalsForSequence(
              sequenceConfiguration = sequenceConfiguration,
              includedProposals = includedProposals,
              newProposals = allNewProposals,
              testedProposals = allTestedProposals,
              votedProposals = votes,
              userSegment = maybeSegment
            )
        }
        sequenceVotes <- futureVotedProposalsAndVotes(maybeUserId, requestContext, selectedProposals.map(_.id))
      } yield {
        Some(
          SequenceResult(
            id = sequenceConfiguration.sequenceId,
            title = "deprecated",
            slug = "deprecated",
            proposals = selectedProposals
              .map(indexed => {
                val proposalKey =
                  SecurityHelper.generateProposalKeyHash(
                    indexed.id,
                    requestContext.sessionId,
                    requestContext.location,
                    securityConfiguration.secureVoteSalt
                  )
                ProposalResponse(
                  indexed,
                  maybeUserId.contains(indexed.userId),
                  sequenceVotes.get(indexed.id),
                  proposalKey
                )
              })
          )
        )
      }
    }

    private def logStartSequenceUserHistory(questionId: Option[QuestionId],
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
                arguments = StartSequenceParameters(None, questionId, None, includedProposals)
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
                arguments = StartSequenceParameters(None, questionId, None, includedProposals)
              )
            )
          )
      }

    }

    private def futureVotedProposals(maybeUserId: Option[UserId],
                                     proposalsIds: Option[Seq[ProposalId]],
                                     requestContext: RequestContext): Future[Seq[ProposalId]] =
      maybeUserId.map { userId =>
        userHistoryCoordinatorService.retrieveVotedProposals(
          RequestUserVotedProposals(userId = userId, proposalsIds = proposalsIds)
        )
      }.getOrElse {
        sessionHistoryCoordinatorService
          .retrieveVotedProposals(RequestSessionVotedProposals(requestContext.sessionId, proposalsIds = proposalsIds))
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

  }
}
