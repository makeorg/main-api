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
import org.make.core.operation.{FeaturedOperation, FeaturedOperationId}
import org.make.core.technical.IdGenerator
import org.mockito.Mockito
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class FeaturedOperationServiceTest
    extends MakeUnitTest
    with DefaultFeaturedOperationServiceComponent
    with PersistentFeaturedOperationServiceComponent
    with IdGeneratorComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val persistentFeaturedOperationService: PersistentFeaturedOperationService =
    mock[PersistentFeaturedOperationService]

  val featuredOperation: FeaturedOperation = FeaturedOperation(
    featuredOperationId = FeaturedOperationId("featured-operation-id"),
    questionId = None,
    title = "title",
    description = Some("description"),
    landscapePicture = "landPic.png",
    portraitPicture = "portPic.png",
    altPicture = "alt",
    label = "label",
    buttonLabel = "button",
    internalLink = None,
    externalLink = Some("link.com"),
    slot = 1
  )

  feature("create featured operation") {
    scenario("creation") {
      Mockito.when(idGenerator.nextFeaturedOperationId()).thenReturn(FeaturedOperationId("featured-operation-id"))
      Mockito
        .when(persistentFeaturedOperationService.persist(featuredOperation))
        .thenReturn(Future.successful(featuredOperation))

      whenReady(
        featuredOperationService.create(
          CreateFeaturedOperationRequest(
            questionId = None,
            title = "title",
            description = Some("description"),
            landscapePicture = "landPic.png",
            portraitPicture = "portPic.png",
            altPicture = "alt",
            label = "label",
            buttonLabel = "button",
            internalLink = None,
            externalLink = Some("link.com"),
            slot = 1
          )
        ),
        Timeout(2.seconds)
      ) { featuredOperation =>
        featuredOperation.featuredOperationId should be(FeaturedOperationId("featured-operation-id"))
      }
    }
  }

  feature("update featured operation") {
    scenario("update when featured operation is not found") {
      Mockito
        .when(persistentFeaturedOperationService.getById(FeaturedOperationId("not-found")))
        .thenReturn(Future.successful(None))

      whenReady(
        featuredOperationService.update(
          featuredOperationId = FeaturedOperationId("not-found"),
          request = UpdateFeaturedOperationRequest(
            questionId = None,
            title = "title",
            description = Some("description"),
            landscapePicture = "landPic.png",
            portraitPicture = "portPic.png",
            altPicture = "alt",
            label = "label",
            buttonLabel = "button",
            internalLink = None,
            externalLink = Some("link.com"),
            slot = 1
          )
        ),
        Timeout(2.seconds)
      ) { result =>
        result should be(None)
      }
    }

    scenario("update when featured operation is found") {

      val updatedFeaturedOperation: FeaturedOperation = featuredOperation.copy(title = "updated title")

      Mockito
        .when(persistentFeaturedOperationService.getById(FeaturedOperationId("featured-operation-id")))
        .thenReturn(Future.successful(Some(featuredOperation)))
      Mockito
        .when(persistentFeaturedOperationService.modify(updatedFeaturedOperation))
        .thenReturn(Future.successful(updatedFeaturedOperation))

      whenReady(
        featuredOperationService.update(
          featuredOperationId = FeaturedOperationId("featured-operation-id"),
          request = UpdateFeaturedOperationRequest(
            questionId = None,
            title = "updated title",
            description = Some("description"),
            landscapePicture = "landPic.png",
            portraitPicture = "portPic.png",
            altPicture = "alt",
            label = "label",
            buttonLabel = "button",
            internalLink = None,
            externalLink = Some("link.com"),
            slot = 1
          )
        ),
        Timeout(2.seconds)
      ) { result =>
        result.map(_.title) should be(Some("updated title"))
      }
    }
  }

  feature("delete featured operation") {
    scenario("delete featured operation") {
      val featuredOperation1 = featuredOperation
      val featuredOperation3 =
        featuredOperation.copy(featuredOperationId = FeaturedOperationId("ope-3"), title = "title 3", slot = 3)

      Mockito
        .when(persistentFeaturedOperationService.delete(FeaturedOperationId("to-delete")))
        .thenReturn(Future.successful({}))

      Mockito
        .when(persistentFeaturedOperationService.getAll)
        .thenReturn(Future.successful(Seq(featuredOperation1, featuredOperation3)))

      whenReady(featuredOperationService.delete(FeaturedOperationId("to-delete")), Timeout(2.seconds)) { _ =>
        Mockito
          .verify(persistentFeaturedOperationService)
          .modify(featuredOperation1)

        Mockito
          .verify(persistentFeaturedOperationService)
          .modify(featuredOperation3.copy(slot = 2))
      }

    }
  }

}
