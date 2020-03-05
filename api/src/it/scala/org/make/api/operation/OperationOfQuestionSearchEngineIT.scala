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

package org.make.api.operation

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.scaladsl.{Flow, Source => AkkaSource}
import io.circe.syntax._
import org.make.api.docker.DockerElasticsearchService
import org.make.api.technical.elasticsearch.{
  DefaultElasticsearchClientComponent,
  ElasticsearchConfiguration,
  ElasticsearchConfigurationComponent
}
import org.make.api.{ActorSystemComponent, ItMakeTest}
import org.make.core.CirceFormatters
import org.make.core.operation.indexed.IndexedOperationOfQuestion
import org.make.core.operation._
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.mockito.Mockito
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.collection.immutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}
import scala.io.{Codec, Source}
import scala.util.{Failure, Success, Try}

class OperationOfQuestionSearchEngineIT
    extends ItMakeTest
    with CirceFormatters
    with DockerElasticsearchService
    with DefaultOperationOfQuestionSearchEngineComponent
    with ElasticsearchConfigurationComponent
    with DefaultElasticsearchClientComponent
    with ActorSystemComponent {

  override val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)

  override val StartContainersTimeout: FiniteDuration = 5.minutes

  override val elasticsearchExposedPort: Int = 30005

  private val eSIndexName: String = "operation-of-question-it-test"
  private val eSDocType: String = "operation-of-question"

  override val elasticsearchConfiguration: ElasticsearchConfiguration =
    mock[ElasticsearchConfiguration]
  Mockito.when(elasticsearchConfiguration.connectionString).thenReturn(s"localhost:$elasticsearchExposedPort")
  Mockito.when(elasticsearchConfiguration.operationOfQuestionAliasName).thenReturn(eSIndexName)
  Mockito.when(elasticsearchConfiguration.indexName).thenReturn(eSIndexName)

  override def beforeAll(): Unit = {
    super.beforeAll()
    initializeElasticsearch()
  }

  val indexedOperationOfQuestions: immutable.Seq[IndexedOperationOfQuestion] = immutable.Seq(
    IndexedOperationOfQuestion(
      questionId = QuestionId("question-1"),
      question = "First question ?",
      slug = "first-question",
      startDate = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      endDate = Some(ZonedDateTime.from(dateFormatter.parse("2019-06-02T01:01:01.123Z"))),
      theme =
        QuestionTheme(gradientStart = "#424242", gradientEnd = "#424242", color = "#424242", fontColor = "#424242"),
      description = "some random description",
      consultationImage = None,
      country = Country("FR"),
      language = Language("fr"),
      operationId = OperationId("operation-id"),
      operationTitle = "operationTitle",
      operationKind = OperationKind.PublicConsultation.shortName,
      aboutUrl = None
    ),
    IndexedOperationOfQuestion(
      questionId = QuestionId("question-2"),
      question = "Second question ?",
      slug = "second-question",
      startDate = Some(ZonedDateTime.from(dateFormatter.parse("2019-06-02T01:01:01.123Z"))),
      endDate = Some(ZonedDateTime.from(dateFormatter.parse("2020-06-02T01:01:01.123Z"))),
      theme =
        QuestionTheme(gradientStart = "#424242", gradientEnd = "#424242", color = "#424242", fontColor = "#424242"),
      description = "some random description",
      consultationImage = None,
      country = Country("FR"),
      language = Language("fr"),
      operationId = OperationId("operation-id"),
      operationTitle = "operationTitle",
      operationKind = OperationKind.PublicConsultation.shortName,
      aboutUrl = None
    ),
    IndexedOperationOfQuestion(
      questionId = QuestionId("question-3"),
      question = "Third question ?",
      slug = "third-question",
      startDate = Some(ZonedDateTime.from(dateFormatter.parse("2018-11-22T01:01:01.123Z"))),
      endDate = Some(ZonedDateTime.from(dateFormatter.parse("2019-06-02T01:01:01.123Z"))),
      theme =
        QuestionTheme(gradientStart = "#424242", gradientEnd = "#424242", color = "#424242", fontColor = "#424242"),
      description = "some random description",
      consultationImage = None,
      country = Country("FR"),
      language = Language("fr"),
      operationId = OperationId("operation-id"),
      operationTitle = "operationTitle",
      operationKind = OperationKind.BusinessConsultation.shortName,
      aboutUrl = None
    ),
    IndexedOperationOfQuestion(
      questionId = QuestionId("question-4"),
      question = "Fourth question ?",
      slug = "fourth-question",
      startDate = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      endDate = Some(ZonedDateTime.from(dateFormatter.parse("2018-06-02T01:01:01.123Z"))),
      theme =
        QuestionTheme(gradientStart = "#424242", gradientEnd = "#424242", color = "#424242", fontColor = "#424242"),
      description = "some random description",
      consultationImage = None,
      country = Country("FR"),
      language = Language("fr"),
      operationId = OperationId("operation-id"),
      operationTitle = "operationTitle",
      operationKind = OperationKind.GreatCause.shortName,
      aboutUrl = None
    ),
    IndexedOperationOfQuestion(
      questionId = QuestionId("question-5"),
      question = "Fifth question ?",
      slug = "fifth-question",
      startDate = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      endDate = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      theme =
        QuestionTheme(gradientStart = "#424242", gradientEnd = "#424242", color = "#424242", fontColor = "#424242"),
      description = "some random description",
      consultationImage = None,
      country = Country("FR"),
      language = Language("fr"),
      operationId = OperationId("operation-id"),
      operationTitle = "operationTitle",
      operationKind = OperationKind.PrivateConsultation.shortName,
      aboutUrl = None
    ),
    IndexedOperationOfQuestion(
      questionId = QuestionId("question-french-accent"),
      question = "Question sur les aînés avec accents ?",
      slug = "aines-question",
      startDate = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      endDate = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      theme =
        QuestionTheme(gradientStart = "#424242", gradientEnd = "#424242", color = "#424242", fontColor = "#424242"),
      description = "some random description",
      consultationImage = None,
      country = Country("FR"),
      language = Language("fr"),
      operationId = OperationId("operation-id"),
      operationTitle = "operationTitle",
      operationKind = OperationKind.PublicConsultation.shortName,
      aboutUrl = None
    )
  )

  private def initializeElasticsearch(): Unit = {
    implicit val actorSystem: ActorSystem = ActorSystem()
    val elasticsearchEndpoint = s"http://localhost:$elasticsearchExposedPort"
    val proposalMapping =
      Source.fromResource("elasticsearch-mappings/operationOfQuestion.json")(Codec.UTF8).getLines().mkString("")
    val responseFuture: Future[HttpResponse] =
      Http().singleRequest(
        HttpRequest(
          uri = s"$elasticsearchEndpoint/$eSIndexName",
          method = HttpMethods.PUT,
          entity = HttpEntity(ContentTypes.`application/json`, proposalMapping)
        )
      )
    Await.result(responseFuture, 20.seconds)
    responseFuture.onComplete {
      case Failure(e) =>
        logger.error(s"Cannot create elasticsearch schema: ${e.getStackTrace.mkString("\n")}")
        fail(e)
      case Success(_) => logger.debug("Elasticsearch mapped successfully.")
    }

    val pool: Flow[(HttpRequest, QuestionId), (Try[HttpResponse], QuestionId), Http.HostConnectionPool] =
      Http().cachedHostConnectionPool[QuestionId](
        "localhost",
        elasticsearchExposedPort,
        ConnectionPoolSettings(actorSystem).withMaxConnections(3)
      )

    val insertFutures = AkkaSource[IndexedOperationOfQuestion](indexedOperationOfQuestions).map { operationOfQuestion =>
      val indexAndDocTypeEndpoint = s"$eSIndexName/$eSDocType"
      (
        HttpRequest(
          uri = s"$elasticsearchEndpoint/$indexAndDocTypeEndpoint/${operationOfQuestion.questionId.value}",
          method = HttpMethods.PUT,
          entity = HttpEntity(ContentTypes.`application/json`, operationOfQuestion.asJson.toString)
        ),
        operationOfQuestion.questionId
      )
    }.via(pool).runForeach {
      case (Failure(e), id) => logger.error(s"Error when indexing operation of question ${id.value}:", e)
      case _                =>
    }
    Await.result(insertFutures, 150.seconds)
    logger.debug("Operation of questions indexed successfully.")

    val responseRefreshIdeaFuture: Future[HttpResponse] = Http().singleRequest(
      HttpRequest(uri = s"$elasticsearchEndpoint/$eSIndexName/_refresh", method = HttpMethods.POST)
    )
    Await.result(responseRefreshIdeaFuture, 5.seconds)
  }

  feature("get operation of question by id") {
    val questionId = indexedOperationOfQuestions.head.questionId
    scenario("should return an operation of question") {
      whenReady(elasticsearchOperationOfQuestionAPI.findOperationOfQuestionById(questionId), Timeout(3.seconds)) {
        case Some(operationOfQuestion) => operationOfQuestion.questionId should equal(questionId)
        case None                      => fail("operation of question not found by id")
      }
    }
  }

  feature("search by question") {
    scenario("should return a list of operation of question") {
      val query = OperationOfQuestionSearchQuery(
        filters =
          Some(OperationOfQuestionSearchFilters(question = Some(QuestionContentSearchFilter(text = "question"))))
      )
      whenReady(elasticsearchOperationOfQuestionAPI.searchOperationOfQuestions(query), Timeout(3.seconds)) { result =>
        result.total should be > 0L
      }
    }

    scenario("search without accent on accented content should return the accented question") {
      val query = OperationOfQuestionSearchQuery(
        filters = Some(
          OperationOfQuestionSearchFilters(
            question = Some(QuestionContentSearchFilter(text = "aines")),
            language = Some(LanguageSearchFilter(Language("fr")))
          )
        )
      )
      whenReady(elasticsearchOperationOfQuestionAPI.searchOperationOfQuestions(query), Timeout(3.seconds)) { result =>
        result.total should be > 0L
        result.results.exists(_.slug == "aines-question") shouldBe true
      }
    }

    scenario("search in italian should not return french results") {
      val query = OperationOfQuestionSearchQuery(
        filters = Some(
          OperationOfQuestionSearchFilters(
            question = Some(QuestionContentSearchFilter(text = "aines")),
            language = Some(LanguageSearchFilter(Language("it")))
          )
        )
      )
      whenReady(elasticsearchOperationOfQuestionAPI.searchOperationOfQuestions(query), Timeout(3.seconds)) { result =>
        result.total == 0 shouldBe true
      }
    }
  }

}
