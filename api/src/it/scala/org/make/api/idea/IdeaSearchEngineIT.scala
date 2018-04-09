package org.make.api.idea

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
import org.make.core
import org.make.core.idea.indexed._
import org.make.core.idea.{IdeaId, IdeaSearchQuery, IdeaStatus}
import org.make.core.{CirceFormatters, DateHelper}
import org.mockito.Mockito
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.collection.immutable.Seq
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}
import scala.io.{Codec, Source}
import scala.util.{Failure, Success, Try}

class IdeaSearchEngineIT
    extends ItMakeTest
    with CirceFormatters
    with DockerElasticsearchService
    with DefaultIdeaSearchEngineComponent
    with ElasticsearchConfigurationComponent {

  override val StartContainersTimeout: FiniteDuration = 5.minutes

  override protected def afterAll(): Unit = {
    super.afterAll()
    stopAllQuietly()
  }

  private val eSIndexName: String = "ideaittest"
  private val eSDocType: String = "idea"

  override val elasticsearchConfiguration: ElasticsearchConfiguration =
    mock[ElasticsearchConfiguration]
  Mockito.when(elasticsearchConfiguration.connectionString).thenReturn(s"localhost:$defaultElasticsearchPortExposed")
  Mockito.when(elasticsearchConfiguration.aliasName).thenReturn(eSIndexName)
  Mockito.when(elasticsearchConfiguration.indexName).thenReturn(eSIndexName)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    startAllOrFail()
    initializeElasticsearch()
  }

  val ideasActivated: Seq[IndexedIdea] = Seq(
    IndexedIdea(
      ideaId = IdeaId("01"),
      name = "c-idea01",
      operationId = None,
      themeId = None,
      question = Some("question01"),
      country = Some("FR"),
      language = Some("fr"),
      status = IdeaStatus.Activated,
      createdAt = DateHelper.now(),
      updatedAt = Some(DateHelper.now())
    ),
    IndexedIdea(
      ideaId = IdeaId("02"),
      name = "a-idea02",
      operationId = None,
      themeId = None,
      question = Some("question02"),
      country = Some("FR"),
      language = Some("fr"),
      status = IdeaStatus.Activated,
      createdAt = DateHelper.now(),
      updatedAt = Some(DateHelper.now())
    ),
    IndexedIdea(
      ideaId = IdeaId("03"),
      name = "b-idea03",
      operationId = None,
      themeId = None,
      question = Some("question03"),
      country = Some("FR"),
      language = Some("fr"),
      status = IdeaStatus.Activated,
      createdAt = DateHelper.now(),
      updatedAt = Some(DateHelper.now())
    )
  )

  private def ideas: Seq[IndexedIdea] = ideasActivated

  private def initializeElasticsearch(): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    val elasticsearchEndpoint = s"http://localhost:$defaultElasticsearchPortExposed"
    val ideaMapping = Source.fromResource("elasticsearch-mapping.json")(Codec.UTF8).getLines().mkString("")
    val responseFuture: Future[HttpResponse] = Http().singleRequest(
      HttpRequest(
        uri = s"$elasticsearchEndpoint/$eSIndexName",
        method = HttpMethods.PUT,
        entity = HttpEntity(ContentTypes.`application/json`, ideaMapping)
      )
    )

    Await.result(responseFuture, 5.seconds)
    responseFuture.onComplete {
      case Failure(e) =>
        logger.error(s"Cannot create elasticsearch schema: ${e.getStackTrace.mkString("\n")}")
        fail(e)
      case Success(_) => logger.debug(s"""Elasticsearch mapped successfully on index "$eSIndexName" """)
    }

    val pool: Flow[(HttpRequest, IdeaId), (Try[HttpResponse], IdeaId), Http.HostConnectionPool] =
      Http().cachedHostConnectionPool[IdeaId](
        "localhost",
        defaultElasticsearchPortExposed,
        ConnectionPoolSettings(system).withMaxConnections(3)
      )

    val insertFutures = AkkaSource[IndexedIdea](ideas).map { idea =>
      val indexAndDocTypeEndpoint = s"$eSIndexName/$eSDocType"
      (
        HttpRequest(
          uri = s"$elasticsearchEndpoint/$indexAndDocTypeEndpoint/${idea.ideaId.value}",
          method = HttpMethods.PUT,
          entity = HttpEntity(ContentTypes.`application/json`, idea.asJson.toString)
        ),
        idea.ideaId
      )
    }.via(pool)
      .runForeach {
        case (Failure(e), id) => logger.error(s"Error when indexing idea ${id.value}:", e)
        case _                =>
      }(ActorMaterializer())

    Await.result(insertFutures, 150.seconds)
    logger.debug("Ideas indexed successfully.")

    val responseRefreshIdeaFuture: Future[HttpResponse] = Http().singleRequest(
      HttpRequest(uri = s"$elasticsearchEndpoint/$eSIndexName/_refresh", method = HttpMethods.POST)
    )
    Await.result(responseRefreshIdeaFuture, 5.seconds)
  }

  feature("get idea list") {
    scenario("get idea list ordered by name") {
      Given("""a list of idea named "a_idea02", "b_idea03" and "c_idea01" """)
      When("I get idea list ordered by name with an order desc")
      Then("""The result should be "a_idea02", "b_idea03" and "c_idea01" """)
      val ideaSearchQuery: IdeaSearchQuery = IdeaFiltersRequest.empty
        .copy(limit = Some(3), skip = Some(0), order = Some("asc"), sort = Some("name"))
        .toSearchQuery(core.RequestContext.empty)
      whenReady(elasticsearchIdeaAPI.searchIdeas(ideaSearchQuery), Timeout(5.seconds)) { result =>
        result.total shouldBe (3)
        result.results(0).name shouldBe ("a-idea02")
        result.results(1).name shouldBe ("b-idea03")
        result.results(2).name shouldBe ("c-idea01")
      }
    }
  }

}