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

import org.make.api.question.{PersistentQuestionService, PersistentQuestionServiceComponent}
import org.make.api.tag.{PersistentTagService, PersistentTagServiceComponent}
import org.make.api.technical.IdGeneratorComponent
import org.make.api.{EmptyActorSystemComponent, MakeUnitTest}
import org.make.core.DateHelper
import org.make.core.operation.OperationActionType.{OperationCreateAction, OperationUpdateAction}
import org.make.core.operation.OperationKind.{BusinessConsultation, GreatCause}
import org.make.core.operation._
import org.make.core.question.QuestionId
import org.make.core.technical.IdGenerator
import org.make.core.user.UserId
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import java.time.ZonedDateTime
import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

class OperationServiceTest
    extends MakeUnitTest
    with DefaultOperationServiceComponent
    with PersistentTagServiceComponent
    with PersistentQuestionServiceComponent
    with IdGeneratorComponent
    with PersistentOperationServiceComponent
    with OperationOfQuestionServiceComponent
    with EmptyActorSystemComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val persistentOperationService: PersistentOperationService = mock[PersistentOperationService]
  override lazy val persistentTagService: PersistentTagService = mock[PersistentTagService]
  override val persistentQuestionService: PersistentQuestionService = mock[PersistentQuestionService]
  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]

  val now: ZonedDateTime = DateHelper.now()

  Feature("create operation") {
    Scenario("create") {
      val op =
        simpleOperation(id = OperationId("create"), slug = "create", operationKind = OperationKind.BusinessConsultation)
      val userId = UserId("creator")
      when(idGenerator.nextOperationId()).thenReturn(op.operationId)
      when(persistentOperationService.persist(any[SimpleOperation])).thenReturn(Future.successful(op))
      when(persistentOperationService.addActionToOperation(argThat[OperationAction] { action =>
        action.makeUserId == userId && action.actionType == OperationCreateAction.value
      }, eqTo(op.operationId))).thenReturn(Future.successful(true))

      whenReady(operationService.create(userId, op.slug, op.operationKind), Timeout(2.seconds)) {
        _ shouldBe op.operationId
      }
    }
  }

  Feature("update operation") {
    Scenario("update") {
      val opId = OperationId("update")
      val questions = Seq(
        QuestionWithDetails(
          question(QuestionId("q-id-1")),
          operationOfQuestion(QuestionId("q-id-1"), opId, featured = true)
        ),
        QuestionWithDetails(
          question(QuestionId("q-id-2")),
          operationOfQuestion(QuestionId("q-id-2"), opId, featured = true)
        ),
        QuestionWithDetails(
          question(QuestionId("q-id-3")),
          operationOfQuestion(QuestionId("q-id-3"), opId, featured = true)
        )
      )
      val op = operation(operationId = opId, slug = "update", questions = questions, operationKind = GreatCause)
      val simple = SimpleOperation(opId, op.status, op.slug, op.operationKind, op.createdAt, op.updatedAt)
      val userId = UserId("updator")
      when(idGenerator.nextOperationId()).thenReturn(op.operationId)
      when(persistentOperationService.getById(op.operationId)).thenReturn(Future.successful(Some(op)))
      when(persistentOperationService.modify(simple)).thenReturn(Future.successful(simple))
      when(persistentOperationService.modify(simple.copy(operationKind = BusinessConsultation)))
        .thenReturn(Future.successful(simple.copy(operationKind = BusinessConsultation)))

      when(persistentOperationService.addActionToOperation(argThat[OperationAction] { action =>
        action.makeUserId == userId && action.actionType == OperationUpdateAction.value
      }, eqTo(op.operationId))).thenReturn(Future.successful(true))

      questions.foreach { q =>
        when(operationOfQuestionService.update(eqTo(q.details)))
          .thenReturn(Future.successful(q.details))
      }

      whenReady(operationService.update(opId, userId, Some(op.slug)), Timeout(2.seconds)) {
        _ shouldBe Some(op.operationId)
      }

      whenReady(
        operationService.update(opId, userId, Some(op.slug), operationKind = Some(BusinessConsultation)),
        Timeout(2.seconds)
      ) {
        _ shouldBe Some(op.operationId)
      }
    }

  }

}
