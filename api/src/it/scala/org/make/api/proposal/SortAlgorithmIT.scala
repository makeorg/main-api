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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source => AkkaSource}
import io.circe.syntax._
import org.make.api.docker.DockerElasticsearchService
import org.make.api.technical.elasticsearch.{
  DefaultElasticsearchClientComponent,
  ElasticsearchConfiguration,
  ElasticsearchConfigurationComponent
}
import org.make.api.{ActorSystemComponent, ItMakeTest}
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.make.core.{CirceFormatters, DateHelper}
import org.mockito.Mockito
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.collection.immutable.Seq
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}
import scala.io.{Codec, Source}
import scala.util.{Failure, Success, Try}

class SortAlgorithmIT
    extends ItMakeTest
    with CirceFormatters
    with DockerElasticsearchService
    with DefaultProposalSearchEngineComponent
    with ElasticsearchConfigurationComponent
    with DefaultElasticsearchClientComponent
    with ActorSystemComponent {

  override val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)

  override val StartContainersTimeout: FiniteDuration = 5.minutes

  override val elasticsearchExposedPort: Int = 30002

  override val elasticsearchConfiguration: ElasticsearchConfiguration = mock[ElasticsearchConfiguration]
  Mockito.when(elasticsearchConfiguration.connectionString).thenReturn(s"localhost:$elasticsearchExposedPort")
  Mockito.when(elasticsearchConfiguration.proposalAliasName).thenReturn(defaultElasticsearchProposalIndex)
  Mockito.when(elasticsearchConfiguration.indexName).thenReturn(defaultElasticsearchProposalIndex)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    startAllOrFail()
    initializeElasticsearch()
  }

  private def initializeElasticsearch(): Unit = {
    implicit val system: ActorSystem = actorSystem

    val elasticsearchEndpoint = s"http://localhost:$elasticsearchExposedPort"
    val proposalMapping =
      Source.fromResource("elasticsearch-mappings/proposal.json")(Codec.UTF8).getLines().mkString("")
    val responseFuture: Future[HttpResponse] =
      Http().singleRequest(
        HttpRequest(
          uri = s"$elasticsearchEndpoint/$defaultElasticsearchProposalIndex",
          method = HttpMethods.PUT,
          entity = HttpEntity(ContentTypes.`application/json`, proposalMapping)
        )
      )
    Await.result(responseFuture, 5.seconds)
    responseFuture.onComplete {
      case Failure(e) =>
        logger.error(s"Cannot create elasticsearch schema: ${e.getStackTrace.mkString("\n")}")
        fail(e)
      case Success(_) => logger.debug("Elasticsearch mapped successfully.")
    }

    val pool: Flow[(HttpRequest, ProposalId), (Try[HttpResponse], ProposalId), Http.HostConnectionPool] =
      Http().cachedHostConnectionPool[ProposalId](
        "localhost",
        elasticsearchExposedPort,
        ConnectionPoolSettings(actorSystem).withMaxConnections(3)
      )

    val insertFutures = AkkaSource[IndexedProposal](proposals).map { proposal =>
      val indexAndDocTypeEndpoint = s"$defaultElasticsearchProposalIndex/$defaultElasticsearchProposalDocType"
      (
        HttpRequest(
          uri = s"$elasticsearchEndpoint/$indexAndDocTypeEndpoint/${proposal.id.value}",
          method = HttpMethods.PUT,
          entity = HttpEntity(ContentTypes.`application/json`, proposal.asJson.toString)
        ),
        proposal.id
      )
    }.via(pool)
      .runForeach {
        case (Failure(e), id) => logger.error(s"Error when indexing proposal ${id.value}:", e)
        case _                =>
      }(ActorMaterializer())
    Await.result(insertFutures, 150.seconds)
    logger.debug("Proposals indexed successfully.")

    val responseRefreshIdeaFuture: Future[HttpResponse] = Http().singleRequest(
      HttpRequest(
        uri = s"$elasticsearchEndpoint/$defaultElasticsearchProposalIndex/_refresh",
        method = HttpMethods.POST
      )
    )
    Await.result(responseRefreshIdeaFuture, 5.seconds)
  }

  private val now = DateHelper.now()
  private def newEmptyProposal(proposalId: String) = IndexedProposal(
    id = ProposalId(proposalId),
    userId = UserId("user-id"),
    content = "This is a test proposal",
    slug = "this-is-a-test-proposal",
    createdAt = now,
    updatedAt = Some(now),
    votes = Seq(
      IndexedVote(key = VoteKey.Agree, qualifications = Seq.empty),
      IndexedVote(key = VoteKey.Disagree, qualifications = Seq.empty),
      IndexedVote(key = VoteKey.Neutral, qualifications = Seq.empty)
    ),
    votesCount = 3,
    votesVerifiedCount = 3,
    toEnrich = false,
    scores = IndexedScores.empty,
    context = None,
    author = Author(
      firstName = None,
      organisationName = None,
      organisationSlug = None,
      postalCode = None,
      age = None,
      avatarUrl = None
    ),
    organisations = Seq.empty,
    themeId = None,
    tags = Seq.empty,
    trending = None,
    labels = Seq.empty,
    country = Country("FR"),
    language = Language("fr"),
    status = ProposalStatus.Accepted,
    ideaId = None,
    operationId = None,
    question = None,
    sequencePool = SequencePool.New,
    initialProposal = false,
    refusalReason = None,
    operationKind = None
  )

  private val proposals: Seq[IndexedProposal] = Seq(
    newEmptyProposal("random-1"),
    newEmptyProposal("random-2"),
    newEmptyProposal("random-3"),
    newEmptyProposal("random-4"),
    newEmptyProposal("random-5"),
    newEmptyProposal("actor-1")
      .copy(organisations = Seq(IndexedOrganisationInfo(UserId("1"), Some("1"), Some("1")))),
    newEmptyProposal("actor-2")
      .copy(
        organisations = Seq(
          IndexedOrganisationInfo(UserId("1"), Some("1"), Some("1")),
          IndexedOrganisationInfo(UserId("2"), Some("2"), Some("2"))
        ),
        tags =
          Seq(IndexedTag(TagId("tag-1"), "tag1", display = true), IndexedTag(TagId("tag-2"), "tag2", display = true))
      ),
    newEmptyProposal("actor-3")
      .copy(
        organisations = Seq(
          IndexedOrganisationInfo(UserId("1"), Some("1"), Some("1")),
          IndexedOrganisationInfo(UserId("2"), Some("2"), Some("2")),
          IndexedOrganisationInfo(UserId("3"), Some("3"), Some("3"))
        )
      ),
    newEmptyProposal("actor-4")
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
    newEmptyProposal("controversy-1").copy(scores = IndexedScores.empty.copy(controversy = 42)),
    newEmptyProposal("controversy-2").copy(scores = IndexedScores.empty.copy(controversy = 427)),
    newEmptyProposal("controversy-3")
      .copy(scores = IndexedScores.empty.copy(controversy = 21), operationId = Some(OperationId("ope-controversy"))),
    newEmptyProposal("popular-1").copy(scores = IndexedScores.empty.copy(topScore = 42)),
    newEmptyProposal("popular-2").copy(scores = IndexedScores.empty.copy(topScore = 21)),
    newEmptyProposal("popular-3")
      .copy(scores = IndexedScores.empty.copy(topScore = 456), operationId = Some(OperationId("ope-popular")))
  )

  feature("random algorithm") {
    scenario("results are the same for the same seed") {
      val query = SearchQuery(sortAlgorithm = Some(RandomAlgorithm(42)))
      val identicalResults = for {
        first  <- elasticsearchProposalAPI.searchProposals(query)
        second <- elasticsearchProposalAPI.searchProposals(query)
      } yield first == second

      whenReady(identicalResults, Timeout(3.seconds)) { result =>
        result should be(true)
      }
    }
    scenario("results are different for a different seed") {
      val firstQuery = SearchQuery(sortAlgorithm = Some(RandomAlgorithm(42)))
      val secondQuery = SearchQuery(sortAlgorithm = Some(RandomAlgorithm(21)))

      val randomResults = for {
        first  <- elasticsearchProposalAPI.searchProposals(firstQuery)
        second <- elasticsearchProposalAPI.searchProposals(secondQuery)
      } yield first != second

      whenReady(randomResults, Timeout(3.seconds)) { result =>
        result should be(true)
      }
    }
  }

  feature("actor vote algorithm") {
    scenario("sort by most number of actor votes") {
      val query = SearchQuery(sortAlgorithm = Some(ActorVoteAlgorithm(42)))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be > 4L
        result.results.head should be(proposals.find(_.id.value == "actor-4").get)
        result.results(1) should be(proposals.find(_.id.value == "actor-3").get)
        result.results(2) should be(proposals.find(_.id.value == "actor-2").get)
        result.results(3) should be(proposals.find(_.id.value == "actor-1").get)
      }
    }

    scenario("sort by most number of actor votes and order by random") {
      val firstQuery = SearchQuery(sortAlgorithm = Some(ActorVoteAlgorithm(42)))
      val secondQuery = SearchQuery(sortAlgorithm = Some(ActorVoteAlgorithm(84)))

      whenReady(elasticsearchProposalAPI.searchProposals(firstQuery), Timeout(3.seconds)) { firstResult =>
        firstResult.total should be > 4L
        firstResult.results.head should be(proposals.find(_.id.value == "actor-4").get)
        firstResult.results(1) should be(proposals.find(_.id.value == "actor-3").get)
        firstResult.results(2) should be(proposals.find(_.id.value == "actor-2").get)
        firstResult.results(3) should be(proposals.find(_.id.value == "actor-1").get)
        whenReady(elasticsearchProposalAPI.searchProposals(secondQuery), Timeout(3.seconds)) { secondResult =>
          secondResult.total should be > 4L
          secondResult.results.head should be(proposals.find(_.id.value == "actor-4").get)
          secondResult.results(1) should be(proposals.find(_.id.value == "actor-3").get)
          secondResult.results(2) should be(proposals.find(_.id.value == "actor-2").get)
          secondResult.results(3) should be(proposals.find(_.id.value == "actor-1").get)
          firstResult.results != secondResult.results should be(true)
          firstResult.results.take(4) == secondResult.results.take(4) should be(true)
        }
      }
    }
  }

  feature("trending algorithms") {
    scenario("controversy algorithm") {
      val query = SearchQuery(sortAlgorithm = Some(ControversyAlgorithm), limit = Some(2))
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.results.size should be(2)
        result.results.headOption.map(_.id.value) should be(Some("controversy-2"))
      }
    }

    scenario("controversy algorithm with other filters") {
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

    scenario("popular algorithm") {
      val query = SearchQuery(sortAlgorithm = Some(PopularAlgorithm), limit = Some(2))
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.results.size should be(2)
        result.results.headOption.map(_.id.value) should be(Some("popular-3"))
      }
    }

    scenario("popular algorithm with other filters") {
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

  feature("tagged first algorithm") {
    scenario("sort by tagged proposals votes") {
      val query = SearchQuery(sortAlgorithm = Some(TaggedFirstLegacyAlgorithm(42)))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be > 4L
        result.results.head should be(proposals.find(_.id.value == "actor-4").get)
        result.results(1) should be(proposals.find(_.id.value == "actor-2").get)
        result.results(2) should be(proposals.find(_.id.value == "actor-3").get)
        result.results(3) should be(proposals.find(_.id.value == "actor-1").get)
      }
    }
  }

}
