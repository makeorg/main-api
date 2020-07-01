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
import org.make.core.DateHelper
import org.make.core.elasticsearch.IndexationStatus
import org.make.core.operation._
import org.make.core.operation.indexed.IndexedOperationOfQuestion
import org.make.core.question.{Question, QuestionId}
import org.make.core.sequence.SequenceId
import org.make.core.technical.IdGenerator
import org.make.core.user.UserId
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

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

  feature("find operations of questions") {
    scenario("find all") {
      val req = SearchOperationsOfQuestions(
        questionIds = Some(Seq(QuestionId("q-id"))),
        operationIds = Some(Seq(OperationId("o-id"))),
        operationKind = None,
        openAt = None
      )
      operationOfQuestionService.find(42, Some(84), None, Some("ASC"), req)

      Mockito
        .verify(persistentOperationOfQuestionService)
        .search(
          42,
          Some(84),
          None,
          Some("ASC"),
          Some(Seq(QuestionId("q-id"))),
          Some(Seq(OperationId("o-id"))),
          None,
          None
        )
    }

    scenario("find by questionId") {
      operationOfQuestionService.findByQuestionId(QuestionId("question-id"))

      Mockito.verify(persistentOperationOfQuestionService).getById(QuestionId("question-id"))
    }

    scenario("find by OperationId") {
      operationOfQuestionService.findByOperationId(OperationId("operation-id"))

      Mockito.verify(persistentOperationOfQuestionService).find(Some(OperationId("operation-id")))
    }

    scenario("find by question slug") {
      Mockito
        .when(persistentQuestionService.find(SearchQuestionRequest(maybeSlug = Some("specific-question-slug"))))
        .thenReturn(
          Future.successful(Seq(TestUtils.question(id = QuestionId("slug-id"), slug = "specific-question-slug")))
        )

      Mockito
        .when(persistentOperationOfQuestionService.getById(QuestionId("slug-id")))
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

    scenario("find fake by question slug") {
      Mockito
        .when(persistentQuestionService.find(SearchQuestionRequest(maybeSlug = Some("fake-question-slug"))))
        .thenReturn(Future.successful(Seq.empty))

      whenReady(operationOfQuestionService.findByQuestionSlug("fake-question-slug"), Timeout(3.seconds)) {
        _ shouldBe empty
      }

    }
  }

  feature("search") {
    scenario("search with query") {
      val query = OperationOfQuestionSearchQuery(filters = Some(
        OperationOfQuestionSearchFilters(questionIds =
          Some(QuestionIdsSearchFilter(Seq(QuestionId("first-question-id"), QuestionId("second-question-id"))))
        )
      )
      )

      operationOfQuestionService.search(query)

      Mockito.verify(elasticsearchOperationOfQuestionAPI).searchOperationOfQuestions(query)
    }
  }

  feature("update") {
    scenario("update simple") {
      val questionId = QuestionId("update-question-id")
      val operationId = OperationId("update-operation-id")
      val operationOfQuestion =
        TestUtils.operationOfQuestion(questionId = questionId, operationId = operationId, operationTitle = "UPDATED")
      val question = TestUtils.question(id = questionId, operationId = Some(operationId))
      val operation = TestUtils.simpleOperation(id = operationId)
      val indexed = IndexedOperationOfQuestion.createFromOperationOfQuestion(operationOfQuestion, operation, question)

      Mockito
        .when(persistentOperationOfQuestionService.modify(operationOfQuestion))
        .thenReturn(Future.successful(operationOfQuestion))

      Mockito
        .when(questionService.getQuestion(questionId))
        .thenReturn(
          Future
            .successful(Some(question))
        )
      Mockito
        .when(persistentOperationOfQuestionService.getById(questionId))
        .thenReturn(Future.successful(Some(operationOfQuestion)))
      Mockito
        .when(operationService.findOneSimple(operationId))
        .thenReturn(Future.successful(Some(operation)))

      Mockito
        .when(elasticsearchOperationOfQuestionAPI.updateOperationOfQuestion(indexed, None))
        .thenReturn(Future.successful(IndexationStatus.Completed))

      whenReady(operationOfQuestionService.update(operationOfQuestion), Timeout(3.seconds)) { result =>
        result.questionId shouldBe questionId
      }

    }

    scenario("update with question") {
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

      Mockito
        .when(persistentQuestionService.modify(withQuestion))
        .thenReturn(Future.successful(withQuestion))
      Mockito
        .when(persistentOperationOfQuestionService.modify(operationOfQuestion))
        .thenReturn(Future.successful(operationOfQuestion))
      Mockito
        .when(questionService.getQuestion(questionId))
        .thenReturn(
          Future
            .successful(Some(withQuestion))
        )
      Mockito
        .when(persistentOperationOfQuestionService.getById(questionId))
        .thenReturn(Future.successful(Some(operationOfQuestion)))
      Mockito
        .when(operationService.findOneSimple(operationId))
        .thenReturn(Future.successful(Some(operation)))

      Mockito
        .when(elasticsearchOperationOfQuestionAPI.updateOperationOfQuestion(indexed, None))
        .thenReturn(Future.successful(IndexationStatus.Completed))

      operationOfQuestionService.updateWithQuestion(operationOfQuestion, withQuestion)

      whenReady(operationOfQuestionService.updateWithQuestion(operationOfQuestion, withQuestion), Timeout(3.seconds)) {
        result =>
          result.questionId shouldBe questionId
      }
    }
  }

  feature("delete") {
    scenario("delete OperationOfQuestion and associated objects") {
      val questionId = QuestionId("question-id")
      Mockito
        .when(persistentOperationOfQuestionService.delete(ArgumentMatchers.eq(questionId)))
        .thenReturn(Future.successful({}))
      Mockito
        .when(persistentSequenceConfigurationService.delete(ArgumentMatchers.eq(questionId)))
        .thenReturn(Future.successful({}))
      Mockito.when(persistentQuestionService.delete(ArgumentMatchers.eq(questionId))).thenReturn(Future.successful({}))
      whenReady(operationOfQuestionService.delete(questionId), Timeout(3.seconds)) { _ shouldBe () }
    }
    scenario("delete fake questionId") {
      val questionId = QuestionId("fake")
      Mockito
        .when(persistentOperationOfQuestionService.delete(ArgumentMatchers.eq(questionId)))
        .thenReturn(Future.successful({}))
      Mockito
        .when(persistentSequenceConfigurationService.delete(ArgumentMatchers.eq(questionId)))
        .thenReturn(Future.successful({}))
      Mockito.when(persistentQuestionService.delete(ArgumentMatchers.eq(questionId))).thenReturn(Future.successful({}))
      whenReady(operationOfQuestionService.delete(questionId), Timeout(3.seconds)) { _ shouldBe () }
    }
  }

  feature("create") {
    scenario("create OperationOfQuestion and associated objects") {

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

      Mockito.when(idGenerator.nextQuestionId()).thenReturn(questionId)
      Mockito.when(idGenerator.nextSequenceId()).thenReturn(sequenceConfiguration.sequenceId)
      Mockito
        .when(persistentQuestionService.persist(ArgumentMatchers.eq(questionCreate)))
        .thenReturn(Future.successful(questionCreate))
      Mockito
        .when(persistentSequenceConfigurationService.persist(ArgumentMatchers.eq(sequenceConfiguration)))
        .thenReturn(Future.successful(true))
      Mockito
        .when(persistentOperationOfQuestionService.persist(ArgumentMatchers.eq(operationOfQuestionCreate)))
        .thenReturn(Future.successful(operationOfQuestionCreate))

      val createParameters = CreateOperationOfQuestion(
        operationId = operationId,
        startDate = operationOfQuestionCreate.startDate,
        endDate = operationOfQuestionCreate.endDate,
        operationTitle = operationOfQuestionCreate.operationTitle,
        slug = questionCreate.slug,
        country = questionCreate.country,
        language = questionCreate.language,
        question = questionCreate.question,
        shortTitle = questionCreate.shortTitle,
        consultationImage = operationOfQuestionCreate.consultationImage,
        descriptionImage = operationOfQuestionCreate.descriptionImage,
        actions = operationOfQuestionCreate.actions
      )
      whenReady(operationOfQuestionService.create(createParameters), Timeout(3.seconds)) { ooq =>
        ooq.questionId shouldBe questionId
        ooq.operationId shouldBe operationId
        ooq.landingSequenceId shouldBe sequenceId
      }
    }
  }

  feature("count") {
    scenario("count from query") {
      val query = SearchOperationsOfQuestions(questionIds =
        Some(Seq(QuestionId("first-question-id"), QuestionId("second-question-id")))
      )

      operationOfQuestionService.count(query)

      Mockito
        .verify(persistentOperationOfQuestionService)
        .count(Some(Seq(QuestionId("first-question-id"), QuestionId("second-question-id"))), None, None)
    }
  }

  feature("index") {
    scenario("missing question") {
      val questionId = QuestionId("fake-question")
      Mockito.when(questionService.getQuestion(questionId)).thenReturn(Future.successful(None))
      whenReady(operationOfQuestionService.indexById(questionId), Timeout(3.seconds)) {
        _ shouldBe empty
      }
    }

    scenario("missing operation") {
      val questionId = QuestionId("missing-operation")
      Mockito
        .when(questionService.getQuestion(questionId))
        .thenReturn(
          Future
            .successful(Some(TestUtils.question(id = questionId, operationId = Some(OperationId("fake-operation")))))
        )
      Mockito
        .when(persistentOperationOfQuestionService.getById(questionId))
        .thenReturn(
          Future.successful(
            Some(TestUtils.operationOfQuestion(questionId = questionId, operationId = OperationId("fake-operation")))
          )
        )
      Mockito.when(operationService.findOneSimple(OperationId("fake-operation"))).thenReturn(Future.successful(None))

      whenReady(operationOfQuestionService.indexById(questionId), Timeout(3.seconds)) {
        _ shouldBe empty
      }
    }

    scenario("indexing fail") {
      val questionId = QuestionId("indexing-fail-question-id")
      val operationId = OperationId("indexing-fail-operation-id")
      val operationOfQuestion = TestUtils.operationOfQuestion(questionId = questionId, operationId = operationId)
      val question = TestUtils.question(id = questionId, operationId = Some(operationId))
      val operation = TestUtils.simpleOperation(id = operationId)
      val indexed = IndexedOperationOfQuestion.createFromOperationOfQuestion(operationOfQuestion, operation, question)
      Mockito
        .when(questionService.getQuestion(questionId))
        .thenReturn(
          Future
            .successful(Some(question))
        )
      Mockito
        .when(persistentOperationOfQuestionService.getById(questionId))
        .thenReturn(Future.successful(Some(operationOfQuestion)))
      Mockito
        .when(operationService.findOneSimple(operationId))
        .thenReturn(Future.successful(Some(operation)))

      Mockito
        .when(elasticsearchOperationOfQuestionAPI.updateOperationOfQuestion(indexed, None))
        .thenReturn(Future.successful(IndexationStatus.Failed(new IllegalStateException("whatever exception"))))

      whenReady(operationOfQuestionService.indexById(questionId), Timeout(3.seconds)) { result =>
        result shouldBe defined
        result.get shouldBe a[IndexationStatus.Failed]
      }
    }

    scenario("index successful") {
      val questionId = QuestionId("indexing-successful-question-id")
      val operationId = OperationId("indexing-successful-operation-id")
      val operationOfQuestion = TestUtils.operationOfQuestion(questionId = questionId, operationId = operationId)
      val question = TestUtils.question(id = questionId, operationId = Some(operationId))
      val operation = TestUtils.simpleOperation(id = operationId)
      val indexed = IndexedOperationOfQuestion.createFromOperationOfQuestion(operationOfQuestion, operation, question)
      Mockito
        .when(questionService.getQuestion(questionId))
        .thenReturn(
          Future
            .successful(Some(question))
        )
      Mockito
        .when(persistentOperationOfQuestionService.getById(questionId))
        .thenReturn(Future.successful(Some(operationOfQuestion)))
      Mockito
        .when(operationService.findOneSimple(operationId))
        .thenReturn(Future.successful(Some(operation)))

      Mockito
        .when(elasticsearchOperationOfQuestionAPI.updateOperationOfQuestion(indexed, None))
        .thenReturn(Future.successful(IndexationStatus.Completed))

      whenReady(operationOfQuestionService.indexById(questionId), Timeout(3.seconds)) { result =>
        result shouldBe defined
        result should contain(IndexationStatus.Completed)
      }
    }

  }
}
