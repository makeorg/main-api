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
import cats.data.NonEmptyList
import org.make.api.docker.SearchEngineIT
import org.make.api.technical.elasticsearch.{
  DefaultElasticsearchClientComponent,
  ElasticsearchConfiguration,
  ElasticsearchConfigurationComponent
}
import org.make.api.views.HomePageViewResponse.Highlights
import org.make.api.{ActorSystemComponent, ItMakeTest}
import org.make.core.CirceFormatters
import org.make.core.operation.indexed.IndexedOperationOfQuestion
import org.make.core.operation._
import org.make.core.operation.OperationOfQuestion.Status._
import org.make.core.operation.SortAlgorithm._
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.scalatest.Assertion
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.collection.immutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class OperationOfQuestionSearchEngineIT
    extends ItMakeTest
    with CirceFormatters
    with SearchEngineIT[QuestionId, IndexedOperationOfQuestion]
    with DefaultOperationOfQuestionSearchEngineComponent
    with ElasticsearchConfigurationComponent
    with DefaultElasticsearchClientComponent
    with ActorSystemComponent {

  override val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)

  override val StartContainersTimeout: FiniteDuration = 5.minutes

  override val elasticsearchExposedPort: Int = 30005

  override val eSIndexName: String = "operation-of-question-it-test"
  override val eSDocType: String = "operation-of-question"
  override def docs: Seq[IndexedOperationOfQuestion] = indexedOperationOfQuestions

  override val elasticsearchConfiguration: ElasticsearchConfiguration =
    mock[ElasticsearchConfiguration]
  when(elasticsearchConfiguration.connectionString).thenReturn(s"localhost:$elasticsearchExposedPort")
  when(elasticsearchConfiguration.operationOfQuestionAliasName).thenReturn(eSIndexName)
  when(elasticsearchConfiguration.indexName).thenReturn(eSIndexName)

  override def beforeAll(): Unit = {
    super.beforeAll()
    initializeElasticsearch(_.questionId)
  }

  val indexedOperationOfQuestions: immutable.Seq[IndexedOperationOfQuestion] = immutable.Seq(
    IndexedOperationOfQuestion(
      questionId = QuestionId("question-1"),
      question = "First question ?",
      slug = "first-question",
      questionShortTitle = Some("first-short-title"),
      startDate = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      endDate = Some(ZonedDateTime.from(dateFormatter.parse("2019-06-02T01:01:01.123Z"))),
      theme = QuestionTheme(
        gradientStart = "#424242",
        gradientEnd = "#424242",
        color = "#424242",
        fontColor = "#424242",
        secondaryColor = None,
        secondaryFontColor = None
      ),
      description = "some random description",
      consultationImage = None,
      consultationImageAlt = None,
      descriptionImage = None,
      descriptionImageAlt = None,
      countries = NonEmptyList.of(Country("FR")),
      language = Language("fr"),
      operationId = OperationId("operation-id"),
      operationTitle = "operationTitle",
      operationKind = OperationKind.BusinessConsultation.value,
      aboutUrl = None,
      resultsLink = None,
      proposalsCount = 42,
      participantsCount = 84,
      actions = None,
      featured = false,
      status = Finished
    ),
    IndexedOperationOfQuestion(
      questionId = QuestionId("question-2"),
      question = "Second question ?",
      slug = "second-question",
      questionShortTitle = Some("second-short-title"),
      startDate = Some(ZonedDateTime.from(dateFormatter.parse("2019-06-02T01:01:01.123Z"))),
      endDate = Some(ZonedDateTime.from(dateFormatter.parse("2020-06-02T01:01:01.123Z"))),
      theme = QuestionTheme(
        gradientStart = "#424242",
        gradientEnd = "#424242",
        color = "#424242",
        fontColor = "#424242",
        secondaryColor = None,
        secondaryFontColor = None
      ),
      description = "some random description",
      consultationImage = None,
      consultationImageAlt = None,
      descriptionImage = None,
      descriptionImageAlt = None,
      countries = NonEmptyList.of(Country("FR")),
      language = Language("fr"),
      operationId = OperationId("operation-id"),
      operationTitle = "operationTitle",
      operationKind = OperationKind.BusinessConsultation.value,
      aboutUrl = None,
      resultsLink = Some("https://example.com"),
      proposalsCount = 42,
      participantsCount = 84,
      actions = Some("some actions"),
      featured = true,
      status = Open
    ),
    IndexedOperationOfQuestion(
      questionId = QuestionId("question-3"),
      question = "Third question ?",
      slug = "third-question",
      questionShortTitle = None,
      startDate = Some(ZonedDateTime.from(dateFormatter.parse("2018-11-22T01:01:01.123Z"))),
      endDate = Some(ZonedDateTime.from(dateFormatter.parse("2019-06-02T01:01:01.123Z"))),
      theme = QuestionTheme(
        gradientStart = "#424242",
        gradientEnd = "#424242",
        color = "#424242",
        fontColor = "#424242",
        secondaryColor = Some("#424242"),
        secondaryFontColor = None
      ),
      description = "some random description",
      consultationImage = None,
      consultationImageAlt = None,
      descriptionImage = None,
      descriptionImageAlt = None,
      countries = NonEmptyList.of(Country("ES")),
      language = Language("fr"),
      operationId = OperationId("operation-id"),
      operationTitle = "operationTitle",
      operationKind = OperationKind.BusinessConsultation.value,
      aboutUrl = None,
      resultsLink = Some(ResultsLink.Internal.Results.value),
      proposalsCount = 42,
      participantsCount = 84,
      actions = None,
      featured = false,
      status = Finished
    ),
    IndexedOperationOfQuestion(
      questionId = QuestionId("question-4"),
      question = "Fourth question ?",
      slug = "fourth-question",
      questionShortTitle = None,
      startDate = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      endDate = Some(ZonedDateTime.from(dateFormatter.parse("2018-06-02T01:01:01.123Z"))),
      theme = QuestionTheme(
        gradientStart = "#424242",
        gradientEnd = "#424242",
        color = "#424242",
        fontColor = "#424242",
        secondaryColor = Some("#424242"),
        secondaryFontColor = Some("#424242")
      ),
      description = "some random description",
      consultationImage = None,
      consultationImageAlt = None,
      descriptionImage = None,
      descriptionImageAlt = None,
      countries = NonEmptyList.of(Country("FR"), Country("ES")),
      language = Language("fr"),
      operationId = OperationId("operation-id"),
      operationTitle = "operationTitle",
      operationKind = OperationKind.GreatCause.value,
      aboutUrl = None,
      resultsLink = Some("https://example.com"),
      proposalsCount = 42,
      participantsCount = 84,
      actions = None,
      featured = false,
      status = Open
    ),
    IndexedOperationOfQuestion(
      questionId = QuestionId("question-5"),
      question = "Fifth question ?",
      slug = "fifth-question",
      questionShortTitle = Some("fifth-short-title"),
      startDate = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      endDate = None,
      theme = QuestionTheme(
        gradientStart = "#424242",
        gradientEnd = "#424242",
        color = "#424242",
        fontColor = "#424242",
        secondaryColor = None,
        secondaryFontColor = None
      ),
      description = "some random description",
      consultationImage = None,
      consultationImageAlt = None,
      descriptionImage = None,
      descriptionImageAlt = None,
      countries = NonEmptyList.of(Country("FR")),
      language = Language("fr"),
      operationId = OperationId("operation-id"),
      operationTitle = "operationTitle",
      operationKind = OperationKind.PrivateConsultation.value,
      aboutUrl = None,
      resultsLink = None,
      proposalsCount = 42,
      participantsCount = 84,
      actions = None,
      featured = true,
      status = Upcoming
    ),
    IndexedOperationOfQuestion(
      questionId = QuestionId("question-french-accent"),
      question = "Question sur les aînés avec accents ?",
      slug = "aines-question",
      questionShortTitle = Some("aines-short-title"),
      startDate = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      endDate = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      theme = QuestionTheme(
        gradientStart = "#424242",
        gradientEnd = "#424242",
        color = "#424242",
        fontColor = "#424242",
        secondaryColor = None,
        secondaryFontColor = Some("#424242")
      ),
      description = "some random description",
      consultationImage = None,
      consultationImageAlt = None,
      descriptionImage = None,
      descriptionImageAlt = None,
      countries = NonEmptyList.of(Country("FR")),
      language = Language("fr"),
      operationId = OperationId("operation-id"),
      operationTitle = "operationTitle",
      operationKind = OperationKind.BusinessConsultation.value,
      aboutUrl = None,
      resultsLink = Some(ResultsLink.Internal.TopIdeas.value),
      proposalsCount = 42,
      participantsCount = 84,
      actions = None,
      featured = false,
      status = Finished
    )
  )

  Feature("get operation of question by id") {
    val questionId = indexedOperationOfQuestions.head.questionId
    Scenario("should return an operation of question") {
      whenReady(elasticsearchOperationOfQuestionAPI.findOperationOfQuestionById(questionId), Timeout(3.seconds)) {
        case Some(operationOfQuestion) => operationOfQuestion.questionId should equal(questionId)
        case None                      => fail("operation of question not found by id")
      }
    }
  }

  Feature("search by country") {
    Scenario("should return a list of operation of question") {
      val query = OperationOfQuestionSearchQuery(filters =
        Some(OperationOfQuestionSearchFilters(country = Some(CountrySearchFilter(country = Country("ES")))))
      )
      whenReady(elasticsearchOperationOfQuestionAPI.count(query), Timeout(3.seconds)) { result =>
        result should be(2)
      }
      whenReady(elasticsearchOperationOfQuestionAPI.searchOperationOfQuestions(query), Timeout(3.seconds)) { result =>
        result.total should be(2)
      }
    }
  }

  Feature("search by question") {
    Scenario("should return a list of operation of question") {
      val query = OperationOfQuestionSearchQuery(filters =
        Some(OperationOfQuestionSearchFilters(question = Some(QuestionContentSearchFilter(text = "question"))))
      )
      whenReady(elasticsearchOperationOfQuestionAPI.count(query), Timeout(3.seconds)) { result =>
        result should be(5)
      }
      whenReady(elasticsearchOperationOfQuestionAPI.searchOperationOfQuestions(query), Timeout(3.seconds)) { result =>
        result.total should be > 0L
      }
    }

    Scenario("search without accent on accented content should return the accented question") {
      val query = OperationOfQuestionSearchQuery(filters = Some(
        OperationOfQuestionSearchFilters(
          question = Some(QuestionContentSearchFilter(text = "aines")),
          language = Some(LanguageSearchFilter(Language("fr")))
        )
      )
      )
      whenReady(elasticsearchOperationOfQuestionAPI.count(query), Timeout(3.seconds)) { result =>
        result should be(1)
      }
      whenReady(elasticsearchOperationOfQuestionAPI.searchOperationOfQuestions(query), Timeout(3.seconds)) { result =>
        result.total should be > 0L
        result.results.exists(_.slug == "aines-question") shouldBe true
      }
    }

    Scenario("search in italian should not return french results") {
      val query = OperationOfQuestionSearchQuery(filters = Some(
        OperationOfQuestionSearchFilters(
          question = Some(QuestionContentSearchFilter(text = "aines")),
          language = Some(LanguageSearchFilter(Language("it")))
        )
      )
      )
      whenReady(elasticsearchOperationOfQuestionAPI.count(query), Timeout(3.seconds)) { result =>
        result should be(0)
      }
      whenReady(elasticsearchOperationOfQuestionAPI.searchOperationOfQuestions(query), Timeout(3.seconds)) { result =>
        result.total == 0 shouldBe true
      }
    }
  }

  Feature("sort algorithms") {

    def resultsAreSorted(results: Seq[IndexedOperationOfQuestion]): Assertion = {
      results.sortBy(result => (result.endDate.fold(Long.MinValue)(-_.toEpochSecond), result.slug)) should be(results)
    }

    Scenario("chronological") {
      val query = OperationOfQuestionSearchQuery(sortAlgorithm = Some(Chronological))
      whenReady(elasticsearchOperationOfQuestionAPI.count(query), Timeout(3.seconds)) { result =>
        result should be(6)
      }
      whenReady(elasticsearchOperationOfQuestionAPI.searchOperationOfQuestions(query), Timeout(3.seconds)) { result =>
        result.total should be(6)
        resultsAreSorted(result.results)
      }
    }

    Scenario("featured") {
      val query = OperationOfQuestionSearchQuery(sortAlgorithm = Some(Featured))
      whenReady(elasticsearchOperationOfQuestionAPI.count(query), Timeout(3.seconds)) { result =>
        result should be(6)
      }
      whenReady(elasticsearchOperationOfQuestionAPI.searchOperationOfQuestions(query), Timeout(3.seconds)) { result =>
        result.total should be(6)
        val (featured, notFeatured) = result.results.splitAt(2)
        featured.forall(_.featured) should be(true)
        resultsAreSorted(featured)
        notFeatured.forall(!_.featured) should be(true)
        resultsAreSorted(notFeatured)
      }
    }

  }

  Feature("highlights") {

    Scenario("get them") {
      whenReady(elasticsearchOperationOfQuestionAPI.highlights(), Timeout(3.seconds)) { result =>
        result should be(
          Highlights(
            indexedOperationOfQuestions.map(_.participantsCount).sum,
            indexedOperationOfQuestions.map(_.proposalsCount).sum,
            0
          )
        )
      }
    }

  }

}
