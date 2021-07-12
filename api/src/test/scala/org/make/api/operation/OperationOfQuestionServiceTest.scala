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
import org.make.api.proposal.{ProposalSearchEngine, ProposalSearchEngineComponent}
import org.make.api.question._
import org.make.api.sequence.{PersistentSequenceConfigurationComponent, PersistentSequenceConfigurationService}
import org.make.api.tag.{TagFilter, TagService, TagServiceComponent}
import org.make.api.technical.IdGeneratorComponent
import org.make.api.{MakeUnitTest, TestUtils}
import org.make.core.{DateHelper, Order}
import org.make.core.elasticsearch.IndexationStatus
import org.make.core.operation._
import org.make.core.operation.indexed.{IndexedOperationOfQuestion, OperationOfQuestionSearchResult}
import org.make.core.proposal.ProposalStatus
import org.make.core.question.{Question, QuestionId}
import org.make.core.sequence.{SequenceConfiguration, SequenceId}
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
    with ProposalSearchEngineComponent
    with QuestionServiceComponent
    with OperationServiceComponent
    with TagServiceComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val persistentOperationService: PersistentOperationService = mock[PersistentOperationService]
  override val persistentQuestionService: PersistentQuestionService = mock[PersistentQuestionService]

  override val persistentSequenceConfigurationService: PersistentSequenceConfigurationService =
    mock[PersistentSequenceConfigurationService]
  override val persistentOperationOfQuestionService: PersistentOperationOfQuestionService =
    mock[PersistentOperationOfQuestionService]
  override val elasticsearchOperationOfQuestionAPI: OperationOfQuestionSearchEngine =
    mock[OperationOfQuestionSearchEngine]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val questionService: QuestionService = mock[QuestionService]
  override val operationService: OperationService = mock[OperationService]
  override val tagService: TagService = mock[TagService]
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
        openAt = None,
        slug = Some("slug")
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
          endAfter = None,
          slug = Some("slug")
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

      when(elasticsearchOperationOfQuestionAPI.findOperationOfQuestionById(questionId))
        .thenReturn(Future.successful(Some(TestUtils.indexedOperationOfQuestion(questionId, operationId))))
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
      when(elasticsearchOperationOfQuestionAPI.findOperationOfQuestionById(questionId))
        .thenReturn(Future.successful(Some(TestUtils.indexedOperationOfQuestion(questionId, operationId))))
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
        .thenReturn(Future.unit)
      when(persistentSequenceConfigurationService.delete(eqTo(questionId)))
        .thenReturn(Future.unit)
      when(persistentQuestionService.delete(eqTo(questionId))).thenReturn(Future.unit)
      whenReady(operationOfQuestionService.delete(questionId), Timeout(3.seconds)) { _ shouldBe () }
    }
    Scenario("delete fake questionId") {
      val questionId = QuestionId("fake")
      when(persistentOperationOfQuestionService.delete(eqTo(questionId)))
        .thenReturn(Future.unit)
      when(persistentSequenceConfigurationService.delete(eqTo(questionId)))
        .thenReturn(Future.unit)
      when(persistentQuestionService.delete(eqTo(questionId))).thenReturn(Future.unit)
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
      val sequenceConfiguration = SequenceConfiguration.default.copy(sequenceId = sequenceId, questionId = questionId)
      val operationCreate = TestUtils.simpleOperation(id = operationId)
      val indexed = IndexedOperationOfQuestion.createFromOperationOfQuestion(
        operationOfQuestionCreate,
        operationCreate,
        questionCreate
      )

      when(idGenerator.nextQuestionId()).thenReturn(questionId)
      when(idGenerator.nextSequenceId()).thenReturn(sequenceConfiguration.sequenceId)
      when(idGenerator.nextSpecificSequenceConfigurationId())
        .thenReturn(
          sequenceConfiguration.controversial.specificSequenceConfigurationId,
          sequenceConfiguration.popular.specificSequenceConfigurationId,
          sequenceConfiguration.keyword.specificSequenceConfigurationId
        )
      when(idGenerator.nextExplorationSequenceConfigurationId())
        .thenReturn(sequenceConfiguration.mainSequence.explorationSequenceConfigurationId)
      when(persistentQuestionService.persist(eqTo(questionCreate)))
        .thenReturn(Future.successful(questionCreate))
      when(persistentSequenceConfigurationService.persist(eqTo(sequenceConfiguration)))
        .thenReturn(Future.successful(true))
      when(persistentOperationOfQuestionService.persist(any[OperationOfQuestion]))
        .thenReturn(Future.successful(operationOfQuestionCreate))
      when(elasticsearchOperationOfQuestionAPI.findOperationOfQuestionById(questionId))
        .thenReturn(Future.successful(Some(TestUtils.indexedOperationOfQuestion(questionId, operationId))))
      when(questionService.getQuestion(questionId)).thenReturn(Future.successful(Some(questionCreate)))
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
        actions = operationOfQuestionCreate.actions,
        featured = true
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
      val query = SearchOperationsOfQuestions(
        questionIds = Some(Seq(QuestionId("first-question-id"), QuestionId("second-question-id"))),
        slug = Some("slug")
      )

      operationOfQuestionService.count(query)

      verify(persistentOperationOfQuestionService)
        .count(
          Some(Seq(QuestionId("first-question-id"), QuestionId("second-question-id"))),
          None,
          None,
          None,
          Some("slug")
        )
    }
  }

  Feature("index") {
    Scenario("missing question") {
      val questionId = QuestionId("fake-question")
      when(elasticsearchOperationOfQuestionAPI.findOperationOfQuestionById(questionId))
        .thenReturn(Future.successful(None))
      when(questionService.getQuestion(questionId)).thenReturn(Future.successful(None))
      whenReady(operationOfQuestionService.indexById(questionId), Timeout(3.seconds)) {
        _ shouldBe empty
      }
    }

    Scenario("missing operation") {
      val questionId = QuestionId("missing-operation")
      val operationId = OperationId("fake-operation")

      when(elasticsearchOperationOfQuestionAPI.findOperationOfQuestionById(questionId))
        .thenReturn(Future.successful(Some(TestUtils.indexedOperationOfQuestion(questionId, operationId))))
      when(questionService.getQuestion(questionId))
        .thenReturn(
          Future
            .successful(Some(TestUtils.question(id = questionId, operationId = Some(operationId))))
        )
      when(persistentOperationOfQuestionService.getById(questionId))
        .thenReturn(
          Future.successful(Some(TestUtils.operationOfQuestion(questionId = questionId, operationId = operationId)))
        )
      when(operationService.findOneSimple(operationId)).thenReturn(Future.successful(None))

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

      when(elasticsearchOperationOfQuestionAPI.findOperationOfQuestionById(questionId))
        .thenReturn(Future.successful(Some(TestUtils.indexedOperationOfQuestion(questionId, operationId))))
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
      val indexed = IndexedOperationOfQuestion
        .createFromOperationOfQuestion(operationOfQuestion, operation, question)
        .copy(top20ConsensusThreshold = Some(42d))

      when(elasticsearchOperationOfQuestionAPI.findOperationOfQuestionById(questionId))
        .thenReturn(
          Future.successful(
            Some(TestUtils.indexedOperationOfQuestion(questionId, operationId, top20ConsensusThreshold = Some(42d)))
          )
        )
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

  Feature("infos") {
    Scenario("ooq infos") {
      val qId = QuestionId("q-id-infos")
      when(
        elasticsearchOperationOfQuestionAPI.searchOperationOfQuestions(query = argThat[OperationOfQuestionSearchQuery] {
          query: OperationOfQuestionSearchQuery =>
            query.filters.isDefined && query.filters.flatMap(_.question).isEmpty
        })
      ).thenReturn(
        Future.successful(
          OperationOfQuestionSearchResult(1L, Seq(indexedOperationOfQuestion(qId, OperationId("o-id-infos"))))
        )
      )
      when(
        elasticsearchProposalAPI.countProposalsByQuestion(
          maybeQuestionIds = Some(Seq(qId)),
          status = Some(Seq(ProposalStatus.Pending)),
          maybeUserId = None,
          toEnrich = None
        )
      ).thenReturn(Future.successful(Map(qId -> 42L)))
      when(
        elasticsearchProposalAPI.countProposalsByQuestion(
          maybeQuestionIds = Some(Seq(qId)),
          status = Some(ProposalStatus.values),
          maybeUserId = None,
          toEnrich = None
        )
      ).thenReturn(Future.successful(Map(qId -> 420L)))
      when(tagService.count(tagFilter = TagFilter(questionIds = Some(Seq(qId))))).thenReturn(Future.successful(42))
      whenReady(operationOfQuestionService.getQuestionsInfos(None, ModerationMode.Moderation), Timeout(3.seconds)) {
        res =>
          res.size shouldBe 1
          res.head.questionId shouldBe qId
          res.head.proposalToModerateCount shouldBe 42
          res.head.totalProposalCount shouldBe 420
          res.head.hasTags shouldBe true
      }
    }
  }
}
