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

package org.make.api.proposal

import org.make.api.docker.SearchEngineIT
import org.make.api.technical.elasticsearch.{
  DefaultElasticsearchClientComponent,
  ElasticsearchConfiguration,
  ElasticsearchConfigurationComponent
}
import org.make.api.MakeUnitTest
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.tag.TagId
import org.make.core.user.{UserId, UserType}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.collection.immutable.Seq
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class SortAlgorithmIT
    extends MakeUnitTest
    with SearchEngineIT[ProposalId, IndexedProposal]
    with DefaultProposalSearchEngineComponent
    with ElasticsearchConfigurationComponent
    with DefaultElasticsearchClientComponent {

  override val StartContainersTimeout: FiniteDuration = 5.minutes

  override val elasticsearchExposedPort: Int = 30002

  override val elasticsearchConfiguration: ElasticsearchConfiguration = mock[ElasticsearchConfiguration]
  when(elasticsearchConfiguration.connectionString).thenReturn(s"localhost:$elasticsearchExposedPort")
  when(elasticsearchConfiguration.proposalAliasName).thenReturn(defaultElasticsearchProposalIndex)
  when(elasticsearchConfiguration.indexName).thenReturn(defaultElasticsearchProposalIndex)

  override val eSIndexName: String = defaultElasticsearchProposalIndex
  override val eSDocType: String = defaultElasticsearchProposalDocType
  override def docs: Seq[IndexedProposal] = proposals

  override def beforeAll(): Unit = {
    super.beforeAll()
    initializeElasticsearch(_.id)
  }

  private def newEmptyOrganisationProposal(proposalId: String): IndexedProposal = {
    val proposal = indexedProposal(ProposalId(proposalId))
    proposal.copy(author = proposal.author.copy(userType = UserType.UserTypeOrganisation))
  }

  private def newEmptyPersonalityProposal(proposalId: String): IndexedProposal = {
    val proposal = indexedProposal(ProposalId(proposalId))
    proposal.copy(author = proposal.author.copy(userType = UserType.UserTypePersonality))
  }

  private val proposals: Seq[IndexedProposal] = Seq(
    indexedProposal(ProposalId("random-1")),
    indexedProposal(ProposalId("random-2")),
    indexedProposal(ProposalId("random-3")),
    indexedProposal(ProposalId("random-4")),
    indexedProposal(ProposalId("random-5")),
    indexedProposal(ProposalId("actor-1"))
      .copy(organisations = Seq(IndexedOrganisationInfo(UserId("1"), Some("1"), Some("1")))),
    indexedProposal(ProposalId("actor-2"))
      .copy(
        organisations = Seq(
          IndexedOrganisationInfo(UserId("1"), Some("1"), Some("1")),
          IndexedOrganisationInfo(UserId("2"), Some("2"), Some("2"))
        ),
        tags =
          Seq(IndexedTag(TagId("tag-1"), "tag1", display = true), IndexedTag(TagId("tag-2"), "tag2", display = true))
      ),
    indexedProposal(ProposalId("actor-3"))
      .copy(organisations = Seq(
        IndexedOrganisationInfo(UserId("1"), Some("1"), Some("1")),
        IndexedOrganisationInfo(UserId("2"), Some("2"), Some("2")),
        IndexedOrganisationInfo(UserId("3"), Some("3"), Some("3"))
      )
      ),
    indexedProposal(ProposalId("actor-4"))
      .copy(
        organisations = Seq(
          IndexedOrganisationInfo(UserId("1"), Some("1"), Some("1")),
          IndexedOrganisationInfo(UserId("2"), Some("2"), Some("2")),
          IndexedOrganisationInfo(UserId("3"), Some("3"), Some("3")),
          IndexedOrganisationInfo(UserId("4"), Some("4"), Some("4"))
        ),
        tags =
          Seq(IndexedTag(TagId("tag-1"), "tag1", display = true), IndexedTag(TagId("tag-2"), "tag2", display = true))
      ),
    indexedProposal(ProposalId("controversy-1"))
      .copy(
        scores = IndexedScores.empty.copy(controversy = IndexedScore(0, 0.15, 0)),
        sequencePool = SequencePool.Tested
      ),
    indexedProposal(ProposalId("controversy-2"))
      .copy(
        scores = IndexedScores.empty.copy(controversy = IndexedScore(0, 0.95, 0)),
        sequencePool = SequencePool.Tested
      ),
    indexedProposal(ProposalId("controversy-3"))
      .copy(
        scores = IndexedScores.empty.copy(controversy = IndexedScore(0, 0.21, 0)),
        operationId = Some(OperationId("ope-controversy")),
        sequencePool = SequencePool.Tested
      ),
    indexedProposal(ProposalId("controversy-4"))
      .copy(
        scores = IndexedScores.empty.copy(controversy = IndexedScore(0, 0.14, 0)),
        sequencePool = SequencePool.Tested
      ),
    indexedProposal(ProposalId("controversy-new"))
      .copy(scores = IndexedScores.empty.copy(controversy = IndexedScore(0, 0.95, 0)), sequencePool = SequencePool.New),
    indexedProposal(ProposalId("realistic-1"))
      .copy(
        scores = IndexedScores.empty.copy(realistic = IndexedScore(0, 0.15, 0)),
        sequencePool = SequencePool.Tested
      ),
    indexedProposal(ProposalId("realistic-2"))
      .copy(
        scores = IndexedScores.empty.copy(realistic = IndexedScore(0, 0.95, 0)),
        sequencePool = SequencePool.Tested
      ),
    indexedProposal(ProposalId("realistic-3"))
      .copy(
        scores = IndexedScores.empty.copy(realistic = IndexedScore(0, 0.21, 0)),
        operationId = Some(OperationId("ope-realistic")),
        sequencePool = SequencePool.Tested
      ),
    indexedProposal(ProposalId("realistic-4"))
      .copy(
        scores = IndexedScores.empty.copy(realistic = IndexedScore(0, 0.25, 0)),
        sequencePool = SequencePool.Tested
      ),
    indexedProposal(ProposalId("realistic-new"))
      .copy(scores = IndexedScores.empty.copy(realistic = IndexedScore(0, 0.95, 0)), sequencePool = SequencePool.New),
    indexedProposal(ProposalId("popular-1"))
      .copy(sequencePool = SequencePool.Tested, scores = IndexedScores.empty.copy(topScore = IndexedScore(0, 1.4, 0))),
    indexedProposal(ProposalId("popular-2"))
      .copy(sequencePool = SequencePool.Tested, scores = IndexedScores.empty.copy(topScore = IndexedScore(0, 0.1, 0))),
    indexedProposal(ProposalId("popular-3"))
      .copy(
        sequencePool = SequencePool.Tested,
        scores = IndexedScores.empty.copy(topScore = IndexedScore(0, 4.2, 0)),
        operationId = Some(OperationId("ope-popular"))
      ),
    indexedProposal(ProposalId("popular-new"))
      .copy(sequencePool = SequencePool.New, scores = IndexedScores.empty.copy(topScore = IndexedScore(0, 1.4, 0))),
    newEmptyOrganisationProposal("b2b-1"),
    newEmptyOrganisationProposal("b2b-2"),
    newEmptyPersonalityProposal("b2b-3")
  )

  Feature("random algorithm") {
    Scenario("results are the same for the same seed") {
      val query = SearchQuery(sortAlgorithm = Some(RandomAlgorithm(42)))
      val identicalResults = for {
        first  <- elasticsearchProposalAPI.searchProposals(query)
        second <- elasticsearchProposalAPI.searchProposals(query)
      } yield (first, second)

      whenReady(identicalResults, Timeout(3.seconds)) {
        case (first, second) =>
          first.results should not be empty
          first should be(second)
      }
    }
    Scenario("results are different for a different seed") {
      val firstQuery = SearchQuery(sortAlgorithm = Some(RandomAlgorithm(42)))
      val secondQuery = SearchQuery(sortAlgorithm = Some(RandomAlgorithm(21)))

      val randomResults = for {
        first  <- elasticsearchProposalAPI.searchProposals(firstQuery)
        second <- elasticsearchProposalAPI.searchProposals(secondQuery)
      } yield (first, second)

      whenReady(randomResults, Timeout(3.seconds)) {
        case (first, second) =>
          first should not be (second)
      }
    }
  }

  Feature("actor vote algorithm") {
    Scenario("sort by most number of actor votes") {
      val query = SearchQuery(sortAlgorithm = Some(ActorVoteAlgorithm(42)))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be > 4L
        result.results.take(4).map(_.id.value) should be(Seq("actor-4", "actor-3", "actor-2", "actor-1"))
      }
    }

    Scenario("sort by most number of actor votes and order by random") {
      val firstQuery = SearchQuery(sortAlgorithm = Some(ActorVoteAlgorithm(42)))
      val secondQuery = SearchQuery(sortAlgorithm = Some(ActorVoteAlgorithm(84)))

      whenReady(elasticsearchProposalAPI.searchProposals(firstQuery), Timeout(3.seconds)) { firstResult =>
        firstResult.total should be > 4L
        firstResult.results.take(4).map(_.id.value) should be(Seq("actor-4", "actor-3", "actor-2", "actor-1"))
        whenReady(elasticsearchProposalAPI.searchProposals(secondQuery), Timeout(3.seconds)) { secondResult =>
          secondResult.total should be > 4L
          secondResult.results.take(4).map(_.id.value) should be(Seq("actor-4", "actor-3", "actor-2", "actor-1"))
          firstResult.results should not be (secondResult.results)
          firstResult.results.take(4) should be(secondResult.results.take(4))
        }
      }
    }
  }

  Feature("controversy algorithm") {
    Scenario("controversy algorithm") {
      val query = SearchQuery(sortAlgorithm = Some(ControversyAlgorithm), limit = Some(2))
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.results.size should be(2)
        result.results.headOption.map(_.id.value) should be(Some("controversy-2"))
      }
    }

    Scenario("controversy algorithm custom threshold") {
      val query = SearchQuery(sortAlgorithm = Some(ControversyAlgorithm), limit = Some(2))
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.results.headOption.map(_.id.value) should be(Some("controversy-2"))
      }
    }

    Scenario("controversy algorithm with other filters") {
      val query = SearchQuery(
        filters = Some(SearchFilters(operation = Some(OperationSearchFilter(Seq(OperationId("ope-controversy")))))),
        sortAlgorithm = Some(ControversyAlgorithm),
        limit = Some(2)
      )
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.results.size should be(1)
        result.results.headOption.map(_.id.value) should be(Some("controversy-3"))
      }
    }
  }

  Feature("popular algorithm") {
    Scenario("popular algorithm") {
      val query = SearchQuery(sortAlgorithm = Some(PopularAlgorithm), limit = Some(2))
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.results.size should be(2)
        result.results.headOption.map(_.id.value) should be(Some("popular-3"))
        result.results(1).id.value should be("popular-1")
      }
    }

    Scenario("popular algorithm with other filters") {
      val query = SearchQuery(
        filters = Some(SearchFilters(operation = Some(OperationSearchFilter(Seq(OperationId("ope-popular")))))),
        sortAlgorithm = Some(PopularAlgorithm),
        limit = Some(2)
      )
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.results.size should be(1)
        result.results.headOption.map(_.id.value) should be(Some("popular-3"))
      }
    }
  }

  Feature("B2B first algorithm") {
    Scenario("B2B first algorithm") {
      val query = SearchQuery(sortAlgorithm = Some(B2BFirstAlgorithm))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be(proposals.size)
        val resultB2B = result.results.take(3)
        resultB2B.map(_.id.value) should contain("b2b-1")
        resultB2B.map(_.id.value) should contain("b2b-2")
        resultB2B.map(_.id.value) should contain("b2b-3")
      }
    }
  }

}
