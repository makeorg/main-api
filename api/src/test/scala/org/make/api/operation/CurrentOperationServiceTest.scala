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

import org.make.api.MakeUnitTest
import org.make.api.technical.IdGeneratorComponent
import org.make.core.operation.{CurrentOperation, CurrentOperationId}
import org.make.core.question.QuestionId
import org.make.core.technical.IdGenerator
import org.mockito.Mockito
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import eu.timepit.refined.auto._

class CurrentOperationServiceTest
    extends MakeUnitTest
    with DefaultCurrentOperationServiceComponent
    with PersistentCurrentOperationServiceComponent
    with IdGeneratorComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val persistentCurrentOperationService: PersistentCurrentOperationService =
    mock[PersistentCurrentOperationService]

  val currentOperation: CurrentOperation = CurrentOperation(
    currentOperationId = CurrentOperationId("current-operation-id"),
    questionId = QuestionId("question-id"),
    description = "description",
    label = "label",
    picture = "https://example.com/picture.png",
    altPicture = "alt",
    linkLabel = "linkLabel",
    internalLink = None,
    externalLink = Some("https://example.com/link")
  )

  feature("create current operation") {
    scenario("creation") {
      Mockito.when(idGenerator.nextCurrentOperationId()).thenReturn(CurrentOperationId("current-operation-id"))
      Mockito
        .when(persistentCurrentOperationService.persist(currentOperation))
        .thenReturn(Future.successful(currentOperation))

      whenReady(
        currentOperationService.create(
          CreateCurrentOperationRequest(
            questionId = QuestionId("question-id"),
            description = "description",
            label = "label",
            picture = "https://example.com/picture.png",
            altPicture = "alt",
            linkLabel = "linkLabel",
            internalLink = None,
            externalLink = Some("https://example.com/link")
          )
        ),
        Timeout(2.seconds)
      ) { currentOperation =>
        currentOperation.currentOperationId should be(CurrentOperationId("current-operation-id"))
      }
    }
  }

  feature("update current operation") {
    scenario("update when current operation is not found") {
      Mockito
        .when(persistentCurrentOperationService.getById(CurrentOperationId("not-found")))
        .thenReturn(Future.successful(None))

      whenReady(
        currentOperationService.update(
          currentOperationId = CurrentOperationId("not-found"),
          request = UpdateCurrentOperationRequest(
            questionId = QuestionId("question-id"),
            description = "description",
            label = "label",
            picture = "https://example.com/picture.png",
            altPicture = "alt",
            linkLabel = "linkLabel",
            internalLink = None,
            externalLink = Some("https://example.com/link")
          )
        ),
        Timeout(2.seconds)
      ) { result =>
        result should be(None)
      }
    }

    scenario("update when current operation is found") {

      val updatedCurrentOperation: CurrentOperation = currentOperation.copy(label = "updated label")

      Mockito
        .when(persistentCurrentOperationService.getById(CurrentOperationId("current-operation-id")))
        .thenReturn(Future.successful(Some(currentOperation)))
      Mockito
        .when(persistentCurrentOperationService.modify(updatedCurrentOperation))
        .thenReturn(Future.successful(updatedCurrentOperation))

      whenReady(
        currentOperationService.update(
          currentOperationId = CurrentOperationId("current-operation-id"),
          request = UpdateCurrentOperationRequest(
            questionId = QuestionId("question-id"),
            description = "description",
            label = "updated label",
            picture = "https://example.com/picture.png",
            altPicture = "alt",
            linkLabel = "linkLabel",
            internalLink = None,
            externalLink = Some("https://example.com/link")
          )
        ),
        Timeout(2.seconds)
      ) { result =>
        result.map(_.label) should be(Some("updated label"))
      }
    }
  }

}
