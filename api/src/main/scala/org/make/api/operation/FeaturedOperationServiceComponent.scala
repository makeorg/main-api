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
import org.make.core.operation.{FeaturedOperation, FeaturedOperationId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FeaturedOperationServiceComponent {
  def featuredOperationService: FeaturedOperationService
}

trait FeaturedOperationService {
  def create(request: CreateFeaturedOperationRequest): Future[FeaturedOperation]
  def update(
    featuredOperationId: FeaturedOperationId,
    request: UpdateFeaturedOperationRequest
  ): Future[Option[FeaturedOperation]]
  def getFeaturedOperation(featuredOperationId: FeaturedOperationId): Future[Option[FeaturedOperation]]
  def getAll: Future[Seq[FeaturedOperation]]
  def delete(featuredOperationId: FeaturedOperationId): Future[Unit]
}

trait DefaultFeaturedOperationServiceComponent extends FeaturedOperationServiceComponent {
  this: PersistentFeaturedOperationServiceComponent with IdGeneratorComponent =>

  override lazy val featuredOperationService: FeaturedOperationService = new DefaultFeaturedOperationService

  class DefaultFeaturedOperationService extends FeaturedOperationService {

    override def create(request: CreateFeaturedOperationRequest): Future[FeaturedOperation] = {
      val featuredOperation = FeaturedOperation(
        featuredOperationId = idGenerator.nextFeaturedOperationId(),
        questionId = request.questionId,
        title = request.title,
        description = request.description,
        landscapePicture = request.landscapePicture,
        portraitPicture = request.portraitPicture,
        altPicture = request.altPicture,
        label = request.label,
        buttonLabel = request.buttonLabel,
        internalLink = request.internalLink,
        externalLink = request.externalLink.map(_.value),
        slot = request.slot
      )
      persistentFeaturedOperationService.persist(featuredOperation)
    }

    override def update(
      featuredOperationId: FeaturedOperationId,
      request: UpdateFeaturedOperationRequest
    ): Future[Option[FeaturedOperation]] = {
      persistentFeaturedOperationService.getById(featuredOperationId).flatMap {
        case Some(featuredOperation) =>
          persistentFeaturedOperationService
            .modify(
              featuredOperation.copy(
                questionId = request.questionId,
                title = request.title,
                description = request.description,
                landscapePicture = request.landscapePicture,
                portraitPicture = request.portraitPicture,
                altPicture = request.altPicture,
                label = request.label,
                buttonLabel = request.buttonLabel,
                internalLink = request.internalLink,
                externalLink = request.externalLink.map(_.value),
                slot = request.slot
              )
            )
            .map(Some.apply)
        case None => Future.successful(None)
      }
    }

    override def getFeaturedOperation(featuredOperationId: FeaturedOperationId): Future[Option[FeaturedOperation]] = {
      persistentFeaturedOperationService.getById(featuredOperationId)
    }

    override def getAll: Future[Seq[FeaturedOperation]] = {
      persistentFeaturedOperationService.getAll
    }

    override def delete(featuredOperationId: FeaturedOperationId): Future[Unit] = {
      persistentFeaturedOperationService.delete(featuredOperationId).flatMap { _ =>
        persistentFeaturedOperationService.getAll.map { operations =>
          operations.sortBy(_.slot).zipWithIndex.map {
            case (ope, index) => persistentFeaturedOperationService.modify(ope.copy(slot = index + 1))
          }
        }
      }
    }

  }
}
