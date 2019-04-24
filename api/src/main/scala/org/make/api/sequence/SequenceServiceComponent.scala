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
import org.make.api.sessionhistory._
import org.make.api.technical.security.{SecurityConfigurationComponent, SecurityHelper}
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, MakeRandom}
import org.make.api.user.UserServiceComponent
import org.make.api.userhistory.UserHistoryActor.{RequestUserVotedProposals, RequestVoteValues}
import org.make.api.userhistory._
import org.make.core.common.indexed.Sort
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.indexed.{IndexedProposal, ProposalElasticsearchFieldNames}
import org.make.core.proposal.{ProposalId, QuestionSearchFilter, RandomAlgorithm, SequencePoolSearchFilter}
import org.make.core.sequence._
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
                       sequenceId: SequenceId,
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
    with StrictLogging =>

  override lazy val sequenceService: SequenceService = new SequenceService {

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
                  question = Some(QuestionSearchFilter(Seq(sequenceConfiguration.questionId))),
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
                  question = Some(QuestionSearchFilter(Seq(sequenceConfiguration.questionId))),
                  tags = tagsIds.map(proposal.TagsSearchFilter.apply)
                )
              ),
              limit = Some(sequenceConfiguration.maxTestedProposalCount),
              sortAlgorithm = Some(RandomAlgorithm(MakeRandom.random.nextInt()))
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

        selectedProposals = sequenceConfiguration.selectionAlgorithmName match {
          case SelectionAlgorithmName.Bandit =>
            banditSelectionAlgorithm.selectProposalsForSequence(
              sequenceConfiguration = sequenceConfiguration,
              includedProposals = includedProposals,
              newProposals = newProposals,
              testedProposals = testedProposals,
              votedProposals = votes
            )
          case SelectionAlgorithmName.RoundRobin =>
            roundRobinSelectionAlgorithm.selectProposalsForSequence(
              sequenceConfiguration = sequenceConfiguration,
              includedProposals = includedProposals,
              newProposals = allNewProposals,
              testedProposals = allTestedProposals,
              votedProposals = votes
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

  }
}
