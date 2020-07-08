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

import eu.timepit.refined.auto._
import org.make.api.technical.IdGeneratorComponent
import org.make.core.operation.{CurrentOperation, CurrentOperationId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CurrentOperationServiceComponent {
  def currentOperationService: CurrentOperationService
}

trait CurrentOperationService {
  def create(request: CreateCurrentOperationRequest): Future[CurrentOperation]
  def update(
    currentOperationId: CurrentOperationId,
    request: UpdateCurrentOperationRequest
  ): Future[Option[CurrentOperation]]
  def getCurrentOperation(currentOperationId: CurrentOperationId): Future[Option[CurrentOperation]]
  def getAll: Future[Seq[CurrentOperation]]
  def delete(currentOperationId: CurrentOperationId): Future[Unit]
}

trait DefaultCurrentOperationServiceComponent extends CurrentOperationServiceComponent {
  this: PersistentCurrentOperationServiceComponent with IdGeneratorComponent =>

  override lazy val currentOperationService: CurrentOperationService = new DefaultCurrentOperationService

  class DefaultCurrentOperationService extends CurrentOperationService {

    override def create(request: CreateCurrentOperationRequest): Future[CurrentOperation] = {
      val currentOperation = CurrentOperation(
        currentOperationId = idGenerator.nextCurrentOperationId(),
        questionId = request.questionId,
        description = request.description,
        label = request.label,
        picture = request.picture,
        altPicture = request.altPicture,
        linkLabel = request.linkLabel,
        internalLink = request.internalLink,
        externalLink = request.externalLink.map(_.value)
      )
      persistentCurrentOperationService.persist(currentOperation)
    }

    override def update(
      currentOperationId: CurrentOperationId,
      request: UpdateCurrentOperationRequest
    ): Future[Option[CurrentOperation]] = {
      persistentCurrentOperationService.getById(currentOperationId).flatMap {
        case Some(currentOperation) =>
          persistentCurrentOperationService
            .modify(
              currentOperation.copy(
                description = request.description,
                label = request.label,
                picture = request.picture,
                altPicture = request.altPicture,
                linkLabel = request.linkLabel,
                internalLink = request.internalLink,
                externalLink = request.externalLink.map(_.value)
              )
            )
            .map(Some.apply)
        case None => Future.successful(None)
      }
    }

    override def getCurrentOperation(currentOperationId: CurrentOperationId): Future[Option[CurrentOperation]] = {
      persistentCurrentOperationService.getById(currentOperationId)
    }

    override def getAll: Future[Seq[CurrentOperation]] = {
      persistentCurrentOperationService.getAll
    }

    override def delete(currentOperationId: CurrentOperationId): Future[Unit] = {
      persistentCurrentOperationService.delete(currentOperationId)
    }

  }
}
