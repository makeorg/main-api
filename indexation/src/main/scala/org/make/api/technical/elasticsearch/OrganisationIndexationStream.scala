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

package org.make.api.technical.elasticsearch

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.{Done, NotUsed}
import com.sksamuel.elastic4s.IndexAndType
import grizzled.slf4j.Logging
import org.make.api.organisation.{OrganisationSearchEngine, OrganisationSearchEngineComponent}
import org.make.api.proposal.ProposalSearchEngineComponent
import org.make.api.user.PersistentUserServiceComponent
import org.make.api.userhistory.UserHistoryActorCompanion.RequestUserVotedProposals
import org.make.api.userhistory.UserHistoryCoordinatorServiceComponent
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal._
import org.make.core.proposal.indexed.{IndexedProposal, ProposalsSearchResult}
import org.make.core.question.QuestionId
import org.make.core.user.{User, UserId}
import org.make.core.user.indexed.{IndexedOrganisation, ProposalsAndVotesCountsByQuestion}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationDouble

trait OrganisationIndexationStream
    extends IndexationStream
    with OrganisationSearchEngineComponent
    with PersistentUserServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with ProposalSearchEngineComponent
    with Logging {
  object OrganisationStream {
    def runIndexOrganisations(
      organisationIndexName: String
    )(implicit mat: Materializer): Flow[Seq[User], Done, NotUsed] =
      Flow[Seq[User]]
        .mapAsync(singleAsync)(organisations => executeIndexOrganisations(organisations, organisationIndexName))

    def flowIndexOrganisations(organisationIndexName: String)(implicit mat: Materializer): Flow[User, Done, NotUsed] =
      groupedOrganisations.via(runIndexOrganisations(organisationIndexName))
  }

  // Use custom `grouped` to reduce the size of every batch. Purpose: avoid a heavy load of actor commands.
  private def groupedOrganisations: Flow[User, Seq[User], NotUsed] = Flow[User].groupedWithin(20, 500.milliseconds)

  private def executeIndexOrganisations(organisations: Seq[User], organisationIndexName: String)(
    implicit mat: Materializer
  ): Future[Done] = {
    def futureVotedProposals(organisationId: UserId): Future[Seq[ProposalId]] =
      userHistoryCoordinatorService.retrieveVotedProposals(RequestUserVotedProposals(organisationId))

    def futureFindProposals(proposalIds: Seq[ProposalId]): Future[ProposalsSearchResult] =
      elasticsearchProposalAPI.searchProposals(
        SearchQuery(filters = Some(SearchFilters(proposal = Some(ProposalSearchFilter(proposalIds)))))
      )

    def futureVotes(
      proposalIds: Seq[ProposalId],
      organisationId: UserId
    ): Future[Map[ProposalId, VoteAndQualifications]] =
      userHistoryCoordinatorService.retrieveVoteAndQualifications(organisationId, proposalIds)

    def futureProposalsCountByQuestion(organisationId: UserId): Future[Map[QuestionId, Long]] =
      elasticsearchProposalAPI.countProposalsByQuestion(
        maybeQuestionIds = None,
        status = Some(Seq(ProposalStatus.Accepted)),
        maybeUserId = Some(organisationId),
        toEnrich = None,
        minVotesCount = None,
        minScore = None
      )

    def generateProposalsAndVotesCountsByQuestion(
      proposals: ProposalsSearchResult,
      votesByProposals: Map[ProposalId, VoteAndQualifications],
      proposalsCountByQuestion: Map[QuestionId, Long]
    ): Seq[ProposalsAndVotesCountsByQuestion] = {
      val questionIds: Set[QuestionId] = proposalsCountByQuestion.keySet ++ proposals.results.collect {
        case proposal if votesByProposals.keys.toSet.contains(proposal.id) => proposal.question.map(_.questionId)
      }.flatten

      questionIds.map { questionId =>
        val votedProposalsOfQuestion: Seq[IndexedProposal] =
          proposals.results.filter(_.question.exists(_.questionId == questionId))
        val votesCount: Int = votesByProposals.collect {
          case (proposalId, votes) if votedProposalsOfQuestion.exists(_.id == proposalId) => votes
        }.size
        val proposalsCount: Int = proposalsCountByQuestion.getOrElse(questionId, 0L).toInt
        ProposalsAndVotesCountsByQuestion(questionId, proposalsCount, votesCount)
      }.toSeq
    }

    Source(organisations)
      .mapAsync(parallelism) { organisation =>
        val futureCountsByQuestion: Future[Seq[ProposalsAndVotesCountsByQuestion]] = for {
          proposalIds              <- futureVotedProposals(organisation.userId)
          proposals                <- futureFindProposals(proposalIds)
          votesByProposals         <- futureVotes(proposalIds, organisation.userId)
          proposalsCountByQuestion <- futureProposalsCountByQuestion(organisation.userId)
        } yield generateProposalsAndVotesCountsByQuestion(proposals, votesByProposals, proposalsCountByQuestion)

        futureCountsByQuestion.map { countsByQuestion =>
          IndexedOrganisation.createFromOrganisation(organisation, countsByQuestion)
        }
      }
      .groupedWithin(20, 500.milliseconds)
      .mapAsync(singleAsync) { organisations =>
        elasticsearchOrganisationAPI
          .indexOrganisations(
            organisations,
            Some(IndexAndType(organisationIndexName, OrganisationSearchEngine.organisationIndexName))
          )
          .recoverWith {
            case e =>
              logger.error("Indexing of one organisation chunk failed", e)
              Future.successful(Done)
          }
      }
      .runWith(Sink.ignore)
  }
}
