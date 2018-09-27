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

package org.make.api.widget

import org.make.api.operation.PersistentOperationServiceComponent
import org.make.api.proposal._
import org.make.api.sequence.SequenceConfigurationComponent
import org.make.api.sessionhistory.{RequestSessionVoteValues, SessionHistoryCoordinatorServiceComponent}
import org.make.api.userhistory.UserHistoryActor.RequestVoteValues
import org.make.api.userhistory.UserHistoryCoordinatorServiceComponent
import org.make.core.RequestContext
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.operation.OperationId
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.proposal._
import org.make.core.sequence.SequenceId
import org.make.core.tag.TagId
import org.make.core.user.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait WidgetServiceComponent {
  def widgetService: WidgetService
}

trait WidgetService {
  def startNewWidgetSequence(maybeUserId: Option[UserId],
                             widgetOperationId: OperationId,
                             tagsIds: Option[Seq[TagId]],
                             limit: Option[Int],
                             requestContext: RequestContext): Future[ProposalsResultSeededResponse]
}

trait DefaultWidgetServiceComponent extends WidgetServiceComponent {
  this: ProposalServiceComponent
    with ProposalCoordinatorServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with SessionHistoryCoordinatorServiceComponent
    with ProposalSearchEngineComponent
    with PersistentOperationServiceComponent
    with SequenceConfigurationComponent
    with SelectionAlgorithmComponent =>

  override lazy val widgetService: WidgetService = new WidgetService {

    private def futureVotedProposals(maybeUserId: Option[UserId],
                                     requestContext: RequestContext,
                                     proposals: Seq[ProposalId]): Future[Map[ProposalId, VoteAndQualifications]] = {
      maybeUserId.map { userId =>
        userHistoryCoordinatorService.retrieveVoteAndQualifications(RequestVoteValues(userId, proposals))
      }.getOrElse {
        sessionHistoryCoordinatorService.retrieveVoteAndQualifications(
          RequestSessionVoteValues(requestContext.sessionId, proposals)
        )
      }
    }

    override def startNewWidgetSequence(maybeUserId: Option[UserId],
                                        widgetOperationId: OperationId,
                                        tagsIds: Option[Seq[TagId]],
                                        limit: Option[Int],
                                        requestContext: RequestContext): Future[ProposalsResultSeededResponse] = {

      def allProposals(countProposals: Int): Future[Seq[IndexedProposal]] =
        elasticsearchProposalAPI
          .searchProposals(
            SearchQuery(
              filters = Some(
                SearchFilters(
                  operation = Some(OperationSearchFilter(widgetOperationId)),
                  tags = tagsIds.map(TagsSearchFilter)
                )
              ),
              limit = Some(countProposals)
            )
          )
          .map(_.results)

      for {
        countProposals <- elasticsearchProposalAPI.countProposals(
          SearchQuery(
            filters = Some(
              SearchFilters(
                operation = Some(OperationSearchFilter(widgetOperationId)),
                tags = tagsIds.map(TagsSearchFilter)
              )
            )
          )
        )
        allProposals   <- allProposals(countProposals.toInt)
        votedProposals <- futureVotedProposals(maybeUserId, requestContext, allProposals.map(_.id))
        sequenceId <- persistentOperationService
          .getById(widgetOperationId)
          .map(
            _.map(
              operation =>
                operation.countriesConfiguration
                  .find(countryConfiguration => requestContext.country.contains(countryConfiguration.countryCode))
                  .getOrElse(operation.countriesConfiguration.head)
                  .landingSequenceId
            ).getOrElse(SequenceId(requestContext.source.getOrElse("widget")))
          )
        sequenceConfiguration <- sequenceConfigurationService.getSequenceConfiguration(sequenceId)
        proposals <- Future
          .traverse(allProposals.map(_.id)) { id =>
            proposalCoordinatorService.getProposal(id)
          }
          .map(_.flatten)
        selectedProposals = selectionAlgorithm.selectProposalsForSequence(
          limit.getOrElse(10),
          sequenceConfiguration,
          proposals,
          votedProposals.keys.toSeq,
          Seq.empty
        )
        indexedProposals <- elasticsearchProposalAPI.findProposalsByIds(selectedProposals, random = false)
      } yield {
        val indexedProposalsSorted =
          indexedProposals.sortBy(proposal => selectedProposals.indexOf(proposal.id))
        ProposalsResultSeededResponse(
          indexedProposalsSorted.length,
          indexedProposalsSorted.map(
            proposal => ProposalResult.apply(proposal, maybeUserId.contains(proposal.userId), None)
          ),
          None
        )
      }
    }
  }
}
