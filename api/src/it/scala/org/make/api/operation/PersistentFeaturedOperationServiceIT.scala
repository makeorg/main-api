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
import org.make.core.operation.{FeaturedOperation, FeaturedOperationId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

class PersistentFeaturedOperationServiceIT
    extends DatabaseTest
    with DefaultPersistentFeaturedOperationServiceComponent {

  override protected val cockroachExposedPort: Int = 40016

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

  feature("get by id") {
    scenario("get existing featured operation") {
      val futureFeaturedOperation: Future[Option[FeaturedOperation]] =
        for {
          _                 <- persistentFeaturedOperationService.persist(featuredOperation)
          featuredOperation <- persistentFeaturedOperationService.getById(FeaturedOperationId("featured-operation-id"))
        } yield featuredOperation

      whenReady(futureFeaturedOperation, Timeout(2.seconds)) { featuredOperation =>
        featuredOperation.map(_.title) should be(Some("title"))
      }
    }

    scenario("get non existing featured operation") {
      whenReady(persistentFeaturedOperationService.getById(FeaturedOperationId("not-found")), Timeout(2.seconds)) {
        featuredOperation =>
          featuredOperation should be(None)
      }
    }
  }

  feature("search featured operation") {
    scenario("search all") {
      val futureFeaturedOperations: Future[Seq[FeaturedOperation]] =
        for {
          _ <- persistentFeaturedOperationService.persist(
            featuredOperation.copy(featuredOperationId = FeaturedOperationId("id-2"), slot = 2)
          )
          _ <- persistentFeaturedOperationService.persist(
            featuredOperation.copy(featuredOperationId = FeaturedOperationId("id-3"), slot = 3)
          )
          featuredOperations <- persistentFeaturedOperationService.getAll
        } yield featuredOperations

      whenReady(futureFeaturedOperations, Timeout(2.seconds)) { featuredOperations =>
        featuredOperations.exists(_.featuredOperationId == FeaturedOperationId("id-2")) should be(true)
        featuredOperations.exists(_.featuredOperationId == FeaturedOperationId("id-3")) should be(true)
      }
    }
  }

  feature("update featured operation") {
    scenario("update existing featured operation") {
      val updatedFeaturedOperation =
        featuredOperation.copy(title = "updated title")

      whenReady(persistentFeaturedOperationService.modify(updatedFeaturedOperation), Timeout(2.seconds)) {
        featuredOperation =>
          featuredOperation.featuredOperationId should be(FeaturedOperationId("featured-operation-id"))
          featuredOperation.title should be("updated title")
      }
    }
  }

}
