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
import java.util.UUID

import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.question._
import org.make.api.sequence.{
  PersistentSequenceConfigurationComponent,
  PersistentSequenceConfigurationService,
  SequenceConfiguration
}
import org.make.api.technical.IdGeneratorComponent
import org.make.api.{MakeUnitTest, TestUtils}
import org.make.core.{DateHelper, Order}
import org.make.core.elasticsearch.IndexationStatus
import org.make.core.operation._
import org.make.core.operation.indexed.IndexedOperationOfQuestion
import org.make.core.question.{Question, QuestionId}
import org.make.core.sequence.SequenceId
import org.make.core.technical.IdGenerator
import org.make.core.user.UserId
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import org.make.core.technical.Pagination.{End, Start}

class OperationOfQuestionServiceTest
    extends MakeUnitTest
    with DefaultOperationOfQuestionServiceComponent
    with PersistentQuestionServiceComponent
    with IdGeneratorComponent
    with MakeDBExecutionContextComponent
    with PersistentOperationServiceComponent
    with PersistentSequenceConfigurationComponent
    with PersistentOperationOfQuestionServiceComponent
    with OperationOfQuestionSearchEngineComponent
    with QuestionServiceComponent
    with OperationServiceComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val persistentOperationService: PersistentOperationService = mock[PersistentOperationService]
  override val persistentQuestionService: PersistentQuestionService = mock[PersistentQuestionService]

  override val persistentSequenceConfigurationService: PersistentSequenceConfigurationService =
    mock[PersistentSequenceConfigurationService]
  override val persistentOperationOfQuestionService: PersistentOperationOfQuestionService =
    mock[PersistentOperationOfQuestionService]
  override val elasticsearchOperationOfQuestionAPI: OperationOfQuestionSearchEngine =
    mock[OperationOfQuestionSearchEngine]
  override val questionService: QuestionService = mock[QuestionService]
  override val operationService: OperationService = mock[OperationService]
  override val writeExecutionContext: ExecutionContext = mock[ExecutionContext]
  override val readExecutionContext: ExecutionContext = mock[ExecutionContext]

  val userId: UserId = UserId(UUID.randomUUID().toString)
  val now: ZonedDateTime = DateHelper.now()

  Feature("find operations of questions") {
    Scenario("find all") {
      val req = SearchOperationsOfQuestions(
        questionIds = Some(Seq(QuestionId("q-id"))),
        operationIds = Some(Seq(OperationId("o-id"))),
        operationKind = None,
        openAt = None
      )
      operationOfQuestionService.find(Start(42), Some(End(84)), None, Some(Order.asc), req)

      verify(persistentOperationOfQuestionService)
        .search(
          start = Start(42),
          end = Some(End(84)),
          sort = None,
          order = Some(Order.asc),
          questionIds = Some(Seq(QuestionId("q-id"))),
          operationIds = Some(Seq(OperationId("o-id"))),
          operationKind = None,
          openAt = None,
          endAfter = None
        )
    }

    Scenario("find by questionId") {
      operationOfQuestionService.findByQuestionId(QuestionId("question-id"))

      verify(persistentOperationOfQuestionService).getById(QuestionId("question-id"))
    }

    Scenario("find by OperationId") {
      operationOfQuestionService.findByOperationId(OperationId("operation-id"))

      verify(persistentOperationOfQuestionService).find(Some(OperationId("operation-id")))
    }

    Scenario("find by question slug") {
      when(persistentQuestionService.find(SearchQuestionRequest(maybeSlug = Some("specific-question-slug"))))
        .thenReturn(
          Future.successful(Seq(TestUtils.question(id = QuestionId("slug-id"), slug = "specific-question-slug")))
        )

      when(persistentOperationOfQuestionService.getById(QuestionId("slug-id")))
        .thenReturn(
          Future.successful(
            Some(
              TestUtils
                .operationOfQuestion(questionId = QuestionId("slug-id"), operationId = OperationId("slug-operation-id"))
            )
          )
        )

      whenReady(operationOfQuestionService.findByQuestionSlug("specific-question-slug"), Timeout(3.seconds)) {
        _ shouldBe defined
      }
    }

    Scenario("find fake by question slug") {
      when(persistentQuestionService.find(SearchQuestionRequest(maybeSlug = Some("fake-question-slug"))))
        .thenReturn(Future.successful(Seq.empty))

      whenReady(operationOfQuestionService.findByQuestionSlug("fake-question-slug"), Timeout(3.seconds)) {
        _ shouldBe empty
      }

    }
  }

  Feature("search") {
    Scenario("search with query") {
      val query = OperationOfQuestionSearchQuery(filters = Some(
        OperationOfQuestionSearchFilters(questionIds =
          Some(QuestionIdsSearchFilter(Seq(QuestionId("first-question-id"), QuestionId("second-question-id"))))
        )
      )
      )

      operationOfQuestionService.search(query)

      verify(elasticsearchOperationOfQuestionAPI).searchOperationOfQuestions(query)
    }
  }

  Feature("update") {
    Scenario("update simple") {
      val questionId = QuestionId("update-question-id")
      val operationId = OperationId("update-operation-id")
      val operationOfQuestion =
        TestUtils.operationOfQuestion(questionId = questionId, operationId = operationId, operationTitle = "UPDATED")
      val question = TestUtils.question(id = questionId, operationId = Some(operationId))
      val operation = TestUtils.simpleOperation(id = operationId)
      val indexed = IndexedOperationOfQuestion.createFromOperationOfQuestion(operationOfQuestion, operation, question)

      when(persistentOperationOfQuestionService.modify(operationOfQuestion))
        .thenReturn(Future.successful(operationOfQuestion))

      when(questionService.getQuestion(questionId))
        .thenReturn(
          Future
            .successful(Some(question))
        )
      when(persistentOperationOfQuestionService.getById(questionId))
        .thenReturn(Future.successful(Some(operationOfQuestion)))

      when(operationService.findOneSimple(operationId))
        .thenReturn(Future.successful(Some(operation)))

      when(elasticsearchOperationOfQuestionAPI.indexOperationOfQuestion(indexed, None))
        .thenReturn(Future.successful(IndexationStatus.Completed))

      whenReady(operationOfQuestionService.update(operationOfQuestion), Timeout(3.seconds)) { result =>
        result.questionId shouldBe questionId
      }

    }

    Scenario("update with question") {
      val questionId = QuestionId("update-with-question-question-id")
      val operationId = OperationId("update-with-question-operation-id")
      val operationOfQuestion = TestUtils.operationOfQuestion(
        questionId = questionId,
        operationId = operationId,
        operationTitle = "UPDATED WITH QUESTION"
      )
      val withQuestion = TestUtils.question(id = questionId, question = "UPDATED WITH QUESTION ?")
      val operation = TestUtils.simpleOperation(id = operationId)
      val indexed =
        IndexedOperationOfQuestion.createFromOperationOfQuestion(operationOfQuestion, operation, withQuestion)

      when(persistentQuestionService.modify(withQuestion))
        .thenReturn(Future.successful(withQuestion))
      when(persistentOperationOfQuestionService.modify(operationOfQuestion))
        .thenReturn(Future.successful(operationOfQuestion))
      when(questionService.getQuestion(questionId))
        .thenReturn(
          Future
            .successful(Some(withQuestion))
        )
      when(persistentOperationOfQuestionService.getById(questionId))
        .thenReturn(Future.successful(Some(operationOfQuestion)))
      when(operationService.findOneSimple(operationId))
        .thenReturn(Future.successful(Some(operation)))

      when(elasticsearchOperationOfQuestionAPI.indexOperationOfQuestion(indexed, None))
        .thenReturn(Future.successful(IndexationStatus.Completed))

      operationOfQuestionService.updateWithQuestion(operationOfQuestion, withQuestion)

      whenReady(operationOfQuestionService.updateWithQuestion(operationOfQuestion, withQuestion), Timeout(3.seconds)) {
        result =>
          result.questionId shouldBe questionId
      }
    }
  }

  Feature("delete") {
    Scenario("delete OperationOfQuestion and associated objects") {
      val questionId = QuestionId("question-id")
      when(persistentOperationOfQuestionService.delete(eqTo(questionId)))
        .thenReturn(Future.successful({}))
      when(persistentSequenceConfigurationService.delete(eqTo(questionId)))
        .thenReturn(Future.successful({}))
      when(persistentQuestionService.delete(eqTo(questionId))).thenReturn(Future.successful({}))
      whenReady(operationOfQuestionService.delete(questionId), Timeout(3.seconds)) { _ shouldBe () }
    }
    Scenario("delete fake questionId") {
      val questionId = QuestionId("fake")
      when(persistentOperationOfQuestionService.delete(eqTo(questionId)))
        .thenReturn(Future.successful({}))
      when(persistentSequenceConfigurationService.delete(eqTo(questionId)))
        .thenReturn(Future.successful({}))
      when(persistentQuestionService.delete(eqTo(questionId))).thenReturn(Future.successful({}))
      whenReady(operationOfQuestionService.delete(questionId), Timeout(3.seconds)) { _ shouldBe () }
    }
  }

  Feature("create") {
    Scenario("create OperationOfQuestion and associated objects") {

      val operationId = OperationId("some-operation-id")
      val questionId = QuestionId("some-question-id")
      val sequenceId = SequenceId("some-sequence-id")
      val questionCreate: Question = question(questionId, operationId = Some(operationId))
      val operationOfQuestionCreate: OperationOfQuestion =
        operationOfQuestion(
          questionId,
          operationId,
          landingSequenceId = sequenceId,
          metas = Metas(None, None, None),
          proposalsCount = 0,
          participantsCount = 0,
          featured = false
        )
      val sequenceConfiguration = SequenceConfiguration(sequenceId = sequenceId, questionId = questionId)
      val operationCreate = TestUtils.simpleOperation(id = operationId)
      val indexed = IndexedOperationOfQuestion.createFromOperationOfQuestion(
        operationOfQuestionCreate,
        operationCreate,
        questionCreate
      )

      when(idGenerator.nextQuestionId()).thenReturn(questionId)
      when(idGenerator.nextSequenceId()).thenReturn(sequenceConfiguration.sequenceId)
      when(persistentQuestionService.persist(eqTo(questionCreate)))
        .thenReturn(Future.successful(questionCreate))
      when(persistentSequenceConfigurationService.persist(eqTo(sequenceConfiguration)))
        .thenReturn(Future.successful(true))
      when(persistentOperationOfQuestionService.persist(eqTo(operationOfQuestionCreate)))
        .thenReturn(Future.successful(operationOfQuestionCreate))
      when(questionService.getQuestion(questionId))
        .thenReturn(
          Future
            .successful(Some(questionCreate))
        )
      when(persistentOperationOfQuestionService.getById(questionId))
        .thenReturn(Future.successful(Some(operationOfQuestionCreate)))
      when(operationService.findOneSimple(operationId))
        .thenReturn(Future.successful(Some(operationCreate)))
      when(elasticsearchOperationOfQuestionAPI.indexOperationOfQuestion(indexed, None))
        .thenReturn(Future.successful(IndexationStatus.Completed))

      val createParameters = CreateOperationOfQuestion(
        operationId = operationId,
        startDate = operationOfQuestionCreate.startDate,
        endDate = operationOfQuestionCreate.endDate,
        operationTitle = operationOfQuestionCreate.operationTitle,
        slug = questionCreate.slug,
        countries = questionCreate.countries,
        language = questionCreate.language,
        question = questionCreate.question,
        shortTitle = questionCreate.shortTitle,
        consultationImage = operationOfQuestionCreate.consultationImage,
        consultationImageAlt = operationOfQuestionCreate.consultationImageAlt,
        descriptionImage = operationOfQuestionCreate.descriptionImage,
        descriptionImageAlt = operationOfQuestionCreate.descriptionImageAlt,
        actions = operationOfQuestionCreate.actions
      )
      whenReady(operationOfQuestionService.create(createParameters), Timeout(3.seconds)) { ooq =>
        ooq.questionId shouldBe questionId
        ooq.operationId shouldBe operationId
        ooq.landingSequenceId shouldBe sequenceId
      }
    }
  }

  Feature("count") {
    Scenario("count from query") {
      val query = SearchOperationsOfQuestions(questionIds =
        Some(Seq(QuestionId("first-question-id"), QuestionId("second-question-id")))
      )

      operationOfQuestionService.count(query)

      verify(persistentOperationOfQuestionService)
        .count(Some(Seq(QuestionId("first-question-id"), QuestionId("second-question-id"))), None, None)
    }
  }

  Feature("index") {
    Scenario("missing question") {
      val questionId = QuestionId("fake-question")
      when(questionService.getQuestion(questionId)).thenReturn(Future.successful(None))
      whenReady(operationOfQuestionService.indexById(questionId), Timeout(3.seconds)) {
        _ shouldBe empty
      }
    }

    Scenario("missing operation") {
      val questionId = QuestionId("missing-operation")
      when(questionService.getQuestion(questionId))
        .thenReturn(
          Future
            .successful(Some(TestUtils.question(id = questionId, operationId = Some(OperationId("fake-operation")))))
        )
      when(persistentOperationOfQuestionService.getById(questionId))
        .thenReturn(
          Future.successful(
            Some(TestUtils.operationOfQuestion(questionId = questionId, operationId = OperationId("fake-operation")))
          )
        )
      when(operationService.findOneSimple(OperationId("fake-operation"))).thenReturn(Future.successful(None))

      whenReady(operationOfQuestionService.indexById(questionId), Timeout(3.seconds)) {
        _ shouldBe empty
      }
    }

    Scenario("indexing fail") {
      val questionId = QuestionId("indexing-fail-question-id")
      val operationId = OperationId("indexing-fail-operation-id")
      val operationOfQuestion = TestUtils.operationOfQuestion(questionId = questionId, operationId = operationId)
      val question = TestUtils.question(id = questionId, operationId = Some(operationId))
      val operation = TestUtils.simpleOperation(id = operationId)
      val indexed = IndexedOperationOfQuestion.createFromOperationOfQuestion(operationOfQuestion, operation, question)
      when(questionService.getQuestion(questionId))
        .thenReturn(
          Future
            .successful(Some(question))
        )
      when(persistentOperationOfQuestionService.getById(questionId))
        .thenReturn(Future.successful(Some(operationOfQuestion)))
      when(operationService.findOneSimple(operationId))
        .thenReturn(Future.successful(Some(operation)))

      when(elasticsearchOperationOfQuestionAPI.indexOperationOfQuestion(indexed, None))
        .thenReturn(Future.successful(IndexationStatus.Failed(new IllegalStateException("whatever exception"))))

      whenReady(operationOfQuestionService.indexById(questionId), Timeout(3.seconds)) { result =>
        result shouldBe defined
        result.get shouldBe a[IndexationStatus.Failed]
      }
    }

    Scenario("index successful") {
      val questionId = QuestionId("indexing-successful-question-id")
      val operationId = OperationId("indexing-successful-operation-id")
      val operationOfQuestion = TestUtils.operationOfQuestion(questionId = questionId, operationId = operationId)
      val question = TestUtils.question(id = questionId, operationId = Some(operationId))
      val operation = TestUtils.simpleOperation(id = operationId)
      val indexed = IndexedOperationOfQuestion.createFromOperationOfQuestion(operationOfQuestion, operation, question)
      when(questionService.getQuestion(questionId))
        .thenReturn(
          Future
            .successful(Some(question))
        )
      when(persistentOperationOfQuestionService.getById(questionId))
        .thenReturn(Future.successful(Some(operationOfQuestion)))
      when(operationService.findOneSimple(operationId))
        .thenReturn(Future.successful(Some(operation)))

      when(elasticsearchOperationOfQuestionAPI.indexOperationOfQuestion(indexed, None))
        .thenReturn(Future.successful(IndexationStatus.Completed))

      whenReady(operationOfQuestionService.indexById(questionId), Timeout(3.seconds)) { result =>
        result shouldBe defined
        result should contain(IndexationStatus.Completed)
      }
    }

  }
}
