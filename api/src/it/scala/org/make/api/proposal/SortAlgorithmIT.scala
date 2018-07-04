package org.make.api.proposal

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source => AkkaSource}
import io.circe.syntax._
import org.make.api.ItMakeTest
import org.make.api.docker.DockerElasticsearchService
import org.make.api.technical.elasticsearch.{ElasticsearchConfiguration, ElasticsearchConfigurationComponent}
import org.make.core.idea.CountrySearchFilter
import org.make.core.proposal._
import org.make.core.proposal.indexed._
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
    with ElasticsearchConfigurationComponent {

  override val StartContainersTimeout: FiniteDuration = 5.minutes

  override val elasticsearchExposedPort: Int = 30002

  override val elasticsearchConfiguration: ElasticsearchConfiguration = mock[ElasticsearchConfiguration]
  Mockito.when(elasticsearchConfiguration.connectionString).thenReturn(s"localhost:$elasticsearchExposedPort")
  Mockito.when(elasticsearchConfiguration.aliasName).thenReturn(defaultElasticsearchIndex)
  Mockito.when(elasticsearchConfiguration.indexName).thenReturn(defaultElasticsearchIndex)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    startAllOrFail()
    initializeElasticsearch()
  }

  private def initializeElasticsearch(): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    val elasticsearchEndpoint = s"http://localhost:$elasticsearchExposedPort"
    val proposalMapping = Source.fromResource("elasticsearch-mapping.json")(Codec.UTF8).getLines().mkString("")
    val responseFuture: Future[HttpResponse] =
      Http().singleRequest(
        HttpRequest(
          uri = s"$elasticsearchEndpoint/$defaultElasticsearchIndex",
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
        ConnectionPoolSettings(system).withMaxConnections(3)
      )

    val insertFutures = AkkaSource[IndexedProposal](proposals).map { proposal =>
      val indexAndDocTypeEndpoint = s"$defaultElasticsearchIndex/$defaultElasticsearchDocType"
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
      HttpRequest(uri = s"$elasticsearchEndpoint/$defaultElasticsearchIndex/_refresh", method = HttpMethods.POST)
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
    scores = IndexedScores.empty,
    context = None,
    author = Author(firstName = None, organisationName = None, postalCode = None, age = None, avatarUrl = None),
    organisations = Seq.empty,
    themeId = None,
    tags = Seq.empty,
    trending = None,
    labels = Seq.empty,
    country = "FR",
    language = "fr",
    status = ProposalStatus.Accepted,
    ideaId = None,
    operationId = None
  )

  private val proposals: Seq[IndexedProposal] = Seq(
    newEmptyProposal("random-1"),
    newEmptyProposal("random-2"),
    newEmptyProposal("random-3"),
    newEmptyProposal("random-4"),
    newEmptyProposal("random-5"),
    newEmptyProposal("actor-1")
      .copy(organisations = Seq(IndexedOrganisationInfo(UserId("1"), Some("1")))),
    newEmptyProposal("actor-2")
      .copy(
        organisations =
          Seq(IndexedOrganisationInfo(UserId("1"), Some("1")), IndexedOrganisationInfo(UserId("2"), Some("2")))
      ),
    newEmptyProposal("actor-3")
      .copy(
        organisations = Seq(
          IndexedOrganisationInfo(UserId("1"), Some("1")),
          IndexedOrganisationInfo(UserId("2"), Some("2")),
          IndexedOrganisationInfo(UserId("3"), Some("3"))
        )
      ),
    newEmptyProposal("actor-4")
      .copy(
        organisations = Seq(
          IndexedOrganisationInfo(UserId("1"), Some("1")),
          IndexedOrganisationInfo(UserId("2"), Some("2")),
          IndexedOrganisationInfo(UserId("3"), Some("3")),
          IndexedOrganisationInfo(UserId("4"), Some("4"))
        )
      ),
    newEmptyProposal("controversy-1").copy(
      trending = Some("controversy"),
      votes = Seq(
        IndexedVote(key = VoteKey.Agree, count = 42, qualifications = Seq.empty),
        IndexedVote(key = VoteKey.Disagree, count = 54, qualifications = Seq.empty),
        IndexedVote(key = VoteKey.Neutral, count = 4, qualifications = Seq.empty)
      )
    ),
    newEmptyProposal("controversy-2").copy(
      trending = Some("controversy"),
      country = "42",
      votes = Seq(
        IndexedVote(key = VoteKey.Agree, count = 41, qualifications = Seq.empty),
        IndexedVote(key = VoteKey.Disagree, count = 53, qualifications = Seq.empty),
        IndexedVote(key = VoteKey.Neutral, count = 2, qualifications = Seq.empty)
      )
    ),
    newEmptyProposal("popular-1").copy(
      trending = Some("popular"),
      votes = Seq(
        IndexedVote(key = VoteKey.Agree, count = 84, qualifications = Seq.empty),
        IndexedVote(key = VoteKey.Disagree, count = 6, qualifications = Seq.empty),
        IndexedVote(key = VoteKey.Neutral, count = 10, qualifications = Seq.empty)
      )
    ),
    newEmptyProposal("popular-2").copy(
      trending = Some("popular"),
      country = "42",
      votes = Seq(
        IndexedVote(key = VoteKey.Agree, count = 84, qualifications = Seq.empty),
        IndexedVote(key = VoteKey.Disagree, count = 6, qualifications = Seq.empty),
        IndexedVote(key = VoteKey.Neutral, count = 10, qualifications = Seq.empty)
      )
    )
  )

  feature("random algorithm") {
    scenario("results are the same for the same seed") {
      val query = SearchQuery(sortAlgorithm = Some(RandomAlgorithm(Some(42))))
      val identicalResults = for {
        first  <- elasticsearchProposalAPI.searchProposals(query)
        second <- elasticsearchProposalAPI.searchProposals(query)
      } yield first == second

      whenReady(identicalResults, Timeout(3.seconds)) { result =>
        result should be(true)
      }
    }
    scenario("results are different for a different seed") {
      val firstQuery = SearchQuery(sortAlgorithm = Some(RandomAlgorithm(Some(42))))
      val secondQuery = SearchQuery(sortAlgorithm = Some(RandomAlgorithm(Some(21))))

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
      val query = SearchQuery(sortAlgorithm = Some(ActorVoteAlgorithm(None)))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be > 4
        result.results.head should be(proposals.find(_.id.value == "actor-4").get)
        result.results(1) should be(proposals.find(_.id.value == "actor-3").get)
        result.results(2) should be(proposals.find(_.id.value == "actor-2").get)
        result.results(3) should be(proposals.find(_.id.value == "actor-1").get)
      }
    }

    scenario("sort by most number of actor votes and order by random") {
      val firstQuery = SearchQuery(sortAlgorithm = Some(ActorVoteAlgorithm(Some(42))))
      val secondQuery = SearchQuery(sortAlgorithm = Some(ActorVoteAlgorithm(Some(84))))

      whenReady(elasticsearchProposalAPI.searchProposals(firstQuery), Timeout(3.seconds)) { firstResult =>
        firstResult.total should be > 4
        firstResult.results.head should be(proposals.find(_.id.value == "actor-4").get)
        firstResult.results(1) should be(proposals.find(_.id.value == "actor-3").get)
        firstResult.results(2) should be(proposals.find(_.id.value == "actor-2").get)
        firstResult.results(3) should be(proposals.find(_.id.value == "actor-1").get)
        whenReady(elasticsearchProposalAPI.searchProposals(secondQuery), Timeout(3.seconds)) { secondResult =>
          secondResult.total should be > 4
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
      val query = SearchQuery(sortAlgorithm = Some(ControversyAlgorithm(Some(42))))
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.results.size should be(2)
        result.results.forall(_.trending.contains("controversy")) should be(true)
      }
    }

    scenario("controversy algorithm with other filters") {
      val query = SearchQuery(
        sortAlgorithm = Some(ControversyAlgorithm(Some(42))),
        filters = Some(SearchFilters(country = Some(CountrySearchFilter("42"))))
      )
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.results.size should be(1)
        result.results.forall(_.trending.contains("controversy")) should be(true)
        result.results.forall(_.id == ProposalId("controversy-2")) should be(true)
      }
    }

    scenario("popular algorithm") {
      val query = SearchQuery(sortAlgorithm = Some(PopularAlgorithm(Some(42))))
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.results.size should be(2)
        result.results.forall(_.trending.contains("popular")) should be(true)
      }
    }

    scenario("popular algorithm with other filters") {
      val query = SearchQuery(
        sortAlgorithm = Some(PopularAlgorithm(Some(42))),
        filters = Some(SearchFilters(country = Some(CountrySearchFilter("42"))))
      )
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.results.size should be(1)
        result.results.forall(_.trending.contains("popular")) should be(true)
        result.results.forall(_.id == ProposalId("popular-2")) should be(true)
      }
    }
  }

}
