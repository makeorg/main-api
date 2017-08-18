package org.make.api.technical.elasticsearch

import java.time.ZonedDateTime
import java.util.UUID

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import org.make.api.{DockerElasticsearchService, ItMakeTest}
import org.make.core.proposal.ProposalId
import org.mockito.Mockito
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source
import scala.util.Failure

class ElasticsearchAPIIT
    extends ItMakeTest
    with DockerElasticsearchService
    with DefaultElasticsearchAPIComponent
    with ElasticsearchConfigurationComponent {

  override val elasticsearchConfiguration: ElasticsearchConfiguration =
    mock[ElasticsearchConfiguration]
  Mockito.when(elasticsearchConfiguration.host).thenReturn(defaultElasticsearchHttpHost)
  Mockito.when(elasticsearchConfiguration.port).thenReturn(defaultElasticsearchPortExposed)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    startAllOrFail()
    initializeElasticsearch()
  }

  private def initializeElasticsearch(): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    val databaseEndpoint = s"http://$defaultElasticsearchHttpHost:$defaultElasticsearchPortExposed"

    // register index
    val proposalMapping = Source.fromResource("proposal-mapping.json").getLines().mkString("")
    val responseFuture: Future[HttpResponse] =
      Http().singleRequest(
        HttpRequest(
          uri = s"$databaseEndpoint/$defaultElasticsearchIndex",
          method = HttpMethods.PUT,
          entity = HttpEntity(ContentTypes.`application/json`, proposalMapping)
        )
      )
    Await.result(responseFuture, 5.seconds)

    // inserting data
    val bufferedSource = Source.fromResource("proposals.txt")
    val proposalList = bufferedSource.getLines().map(_.trim).filter(!_.isEmpty).map(_.split(";")).toSeq
    val insertFutures: Future[Seq[HttpResponse]] = Future.sequence(proposalList.map(dumpedDoc => {
      val (proposalId, docBody) = (dumpedDoc(0), dumpedDoc(1))
      val indexAndDocTypeEndpoint = s"$defaultElasticsearchIndex/$defaultElasticsearchDocType"

      // TODO replace single requests by connection pool
      Http().singleRequest(
        HttpRequest(
          uri = s"$databaseEndpoint/$indexAndDocTypeEndpoint/$proposalId",
          method = HttpMethods.PUT,
          entity = HttpEntity(ContentTypes.`application/json`, docBody)
        )
      )
    }))

    Await.result(insertFutures, 150.seconds)
    insertFutures.onComplete {
      case Failure(e) => fail(e)
      case _          =>
    }
  }

  private val now = ZonedDateTime.now
  private val newProposal = ProposalElasticsearch(
    UUID.randomUUID(),
    None,
    "This is a test proposal",
    "DummyContent",
    now,
    now,
    0,
    0,
    0,
    "DummyContent",
    "DummyContent",
    "DummyContent",
    "DummyContent",
    0,
    Seq.empty,
    Seq.empty
  )

  feature("saving new proposal") {
    scenario("should return done") {
      whenReady(elasticsearchAPI.save(newProposal), Timeout(3.seconds)) { result =>
        result should be(Done)
      }
    }
  }

  private val storedTestProposals: Seq[Array[String]] =
    Source.fromResource("proposals.txt").getLines().map(_.trim).filter(!_.isEmpty).map(_.split(";")).toSeq

  feature("get proposal by id") {
    val proposalId = storedTestProposals.head(0)
    scenario("should return a proposal") {
      whenReady(elasticsearchAPI.getProposalById(ProposalId(proposalId)), Timeout(3.seconds)) {
        case Some(proposal) =>
          proposal.id.toString should equal(proposalId)
        case None => fail("proposal not found by id")
      }
    }
  }

  feature("search proposals by content") {
    Given("searching by keywords")
    val query =
      """
        |{
        |  "filter": {
        |     "content": {"text": "Il faut que"}
        |  },
        |  "options": {}
        |}
      """.stripMargin
    scenario("should return a list of proposals") {
      whenReady(elasticsearchAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.flatten.length should be > 0
      }
    }
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    stopAllQuietly()
  }
}
