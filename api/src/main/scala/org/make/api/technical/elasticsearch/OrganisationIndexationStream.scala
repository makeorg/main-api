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

import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import com.sksamuel.elastic4s.IndexAndType
import com.typesafe.scalalogging.StrictLogging
import org.make.api.organisation.{OrganisationSearchEngine, OrganisationSearchEngineComponent}
import org.make.api.proposal.ProposalSearchEngineComponent
import org.make.api.tagtype.PersistentTagTypeServiceComponent
import org.make.api.user.PersistentUserServiceComponent
import org.make.api.userhistory.UserHistoryActor.{RequestUserVotedProposals, RequestVoteValues}
import org.make.api.userhistory.UserHistoryCoordinatorServiceComponent
import org.make.core.proposal.{ProposalId, SearchFilters, SearchQuery, UserSearchFilter}
import org.make.core.user.User
import org.make.core.user.indexed.IndexedOrganisation

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationDouble

trait OrganisationIndexationStream
    extends IndexationStream
    with OrganisationSearchEngineComponent
    with PersistentUserServiceComponent
    with PersistentTagTypeServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with ProposalSearchEngineComponent
    with StrictLogging {
  object OrganisationStream {
    def runIndexOrganisations(organisationIndexName: String): Flow[Seq[User], Done, NotUsed] =
      Flow[Seq[User]]
        .mapAsync(singleAsync)(organisations => executeIndexOrganisations(organisations, organisationIndexName))

    def flowIndexOrganisations(organisationIndexName: String): Flow[User, Done, NotUsed] =
      groupedOrganisations.via(runIndexOrganisations(organisationIndexName))
  }

  // Use custom `grouped` to reduce the size of every batch. Purpose: avoid a heavy load of actor commands.
  private def groupedOrganisations: Flow[User, Seq[User], NotUsed] = Flow[User].groupedWithin(20, 500.milliseconds)

  private def executeIndexOrganisations(organisations: Seq[User], organisationIndexName: String): Future[Done] = {
    Future
      .traverse(organisations) { organisation =>
        val futureVotedProposals: Future[Seq[ProposalId]] =
          userHistoryCoordinatorService.retrieveVotedProposals(RequestUserVotedProposals(organisation.userId))
        def futureVotesCount(proposalIds: Seq[ProposalId]): Future[Int] =
          userHistoryCoordinatorService
            .retrieveVoteAndQualifications(RequestVoteValues(organisation.userId, proposalIds))
            .map(_.size)
        val futureProposalsCount: Future[Long] = elasticsearchProposalAPI.countProposals(
          SearchQuery(Some(SearchFilters(user = Some(UserSearchFilter(organisation.userId)))))
        )

        val counts: Future[(Int, Int)] = for {
          proposalIds    <- futureVotedProposals
          votesCount     <- futureVotesCount(proposalIds)
          proposalsCount <- futureProposalsCount
        } yield (proposalsCount.toInt, votesCount)

        counts.map {
          case (proposalsCount, votesCount) =>
            IndexedOrganisation.createFromOrganisation(organisation, Some(proposalsCount), Some(votesCount))
        }
      }
      .flatMap { organisations =>
        elasticsearchOrganisationAPI
          .indexOrganisations(
            organisations,
            Some(IndexAndType(organisationIndexName, OrganisationSearchEngine.organisationIndexName))
          )
          .recoverWith {
            case e =>
              logger.error("Indexing organisations failed", e)
              Future.successful(Done)
          }
      }
  }
}
