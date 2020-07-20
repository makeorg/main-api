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

import org.make.api.DatabaseTest
import org.make.core.operation.{CurrentOperation, CurrentOperationId}
import org.make.core.question.QuestionId
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

class PersistentCurrentOperationServiceIT extends DatabaseTest with DefaultPersistentCurrentOperationServiceComponent {

  override protected val cockroachExposedPort: Int = 40018

  val currentOperation: CurrentOperation = CurrentOperation(
    currentOperationId = CurrentOperationId("current-operation-id"),
    questionId = QuestionId("question-id"),
    description = "description",
    label = "label",
    picture = "picture.png",
    altPicture = "alt",
    linkLabel = "linkLabel",
    internalLink = None,
    externalLink = Some("link.com")
  )

  Feature("get by id") {
    Scenario("get existing current operation") {
      val futureCurrentOperation: Future[Option[CurrentOperation]] =
        for {
          _                <- persistentCurrentOperationService.persist(currentOperation)
          currentOperation <- persistentCurrentOperationService.getById(CurrentOperationId("current-operation-id"))
        } yield currentOperation

      whenReady(futureCurrentOperation, Timeout(2.seconds)) { currentOperation =>
        currentOperation.map(_.label) should be(Some("label"))
      }
    }

    Scenario("get non existing current operation") {
      whenReady(persistentCurrentOperationService.getById(CurrentOperationId("not-found")), Timeout(2.seconds)) {
        currentOperation =>
          currentOperation should be(None)
      }
    }
  }

  Feature("search current operation") {
    Scenario("search all") {
      val futureCurrentOperations: Future[Seq[CurrentOperation]] =
        for {
          _ <- persistentCurrentOperationService.persist(
            currentOperation.copy(currentOperationId = CurrentOperationId("id-2"))
          )
          _ <- persistentCurrentOperationService.persist(
            currentOperation.copy(currentOperationId = CurrentOperationId("id-3"))
          )
          currentOperations <- persistentCurrentOperationService.getAll
        } yield currentOperations

      whenReady(futureCurrentOperations, Timeout(2.seconds)) { currentOperations =>
        currentOperations.exists(_.currentOperationId == CurrentOperationId("id-2")) should be(true)
        currentOperations.exists(_.currentOperationId == CurrentOperationId("id-3")) should be(true)
      }
    }
  }

  Feature("update current operation") {
    Scenario("update existing current operation") {
      val updatedCurrentOperation =
        currentOperation.copy(label = "updated label")

      whenReady(persistentCurrentOperationService.modify(updatedCurrentOperation), Timeout(2.seconds)) {
        currentOperation =>
          currentOperation.currentOperationId should be(CurrentOperationId("current-operation-id"))
          currentOperation.label should be("updated label")
      }
    }
  }

  Feature("delete current operation") {
    Scenario("delete existing current operation") {
      val futureDelete: Future[(Option[CurrentOperation], Option[CurrentOperation])] = for {
        _ <- persistentCurrentOperationService.persist(
          currentOperation.copy(currentOperationId = CurrentOperationId("to-delete"))
        )
        beforeOperation <- persistentCurrentOperationService.getById(CurrentOperationId("to-delete"))
        _               <- persistentCurrentOperationService.delete(CurrentOperationId("to-delete"))
        afterOperation  <- persistentCurrentOperationService.getById(CurrentOperationId("to-delete"))
      } yield (beforeOperation, afterOperation)

      whenReady(futureDelete, Timeout(3.seconds)) {
        case (beforeOperation, afterOperation) =>
          beforeOperation.map(_.currentOperationId.value) should be(Some("to-delete"))
          afterOperation.map(_.currentOperationId.value) should be(None)
      }
    }
  }

}
