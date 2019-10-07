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

package org.make.api.feature

import org.make.api.DatabaseTest
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.core.feature._
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentFeatureServiceIT
    extends DatabaseTest
    with DefaultPersistentFeatureServiceComponent
    with DefaultIdGeneratorComponent {

  override protected val cockroachExposedPort: Int = 40009

  def newFeature(slug: String): Feature =
    Feature(featureId = idGenerator.nextFeatureId(), slug = slug, name = "feature name")

  val postalCode: Feature = newFeature("postal-code")
  val noVotes: Feature = newFeature("no-votes")
  val fieldHelp: Feature = newFeature("field-help")
  val stream: Feature = newFeature("stream")
  val rust: Feature = newFeature("rust")
  val feature: Feature = newFeature("feature")
  val deleted: Feature = newFeature("deleted")

  feature("One feature can be persisted and retrieved") {
    scenario("Get feature by featureId") {
      val futureFeature: Future[Option[Feature]] = for {
        _            <- persistentFeatureService.persist(postalCode)
        featureStark <- persistentFeatureService.get(postalCode.featureId)
      } yield featureStark

      whenReady(futureFeature, Timeout(3.seconds)) { result =>
        result.map(_.slug) shouldBe Some("postal-code")
      }
    }

    scenario("Get feature by featureId that does not exists") {
      val futureFeatureId: Future[Option[Feature]] = persistentFeatureService.get(FeatureId("fake"))

      whenReady(futureFeatureId, Timeout(3.seconds)) { result =>
        result shouldBe None
      }
    }
  }

  feature("A list of features can be retrieved") {
    scenario("Get a list of all enabled features") {
      val futurePersistedFeatureList: Future[Seq[Feature]] = for {
        _                <- persistentFeatureService.persist(noVotes)
        featureLannister <- persistentFeatureService.persist(fieldHelp)
        featureBolton    <- persistentFeatureService.persist(stream)
        featureGreyjoy   <- persistentFeatureService.persist(rust)
      } yield Seq(featureLannister, featureBolton, featureGreyjoy)

      val futureFeaturesLists: Future[(Seq[Feature], Seq[Feature])] = for {
        persistedFeaturesList <- futurePersistedFeatureList
        foundFeatures         <- persistentFeatureService.findAll()
      } yield foundFeatures -> persistedFeaturesList

      whenReady(futureFeaturesLists, Timeout(3.seconds)) {
        case (persisted, found) =>
          found.forall(persisted.contains) should be(true)
      }
    }
  }

  feature("One feature can be updated") {
    scenario("Update feature") {
      val futureFeature: Future[Option[Feature]] = for {
        _            <- persistentFeatureService.persist(feature)
        featureStark <- persistentFeatureService.update(feature.copy(slug = "new tully"))
      } yield featureStark

      whenReady(futureFeature, Timeout(3.seconds)) { result =>
        result.map(_.featureId.value) shouldBe Some(feature.featureId.value)
        result.map(_.slug) shouldBe Some("new tully")
      }
    }

    scenario("Update feature that does not exists") {
      val futureFeatureId: Future[Option[Feature]] = persistentFeatureService.update(newFeature("fake"))

      whenReady(futureFeatureId, Timeout(3.seconds)) { result =>
        result shouldBe None
      }
    }
  }

  feature("One feature can be deleted") {
    scenario("Delete feature") {
      val futureFeaturePersisted: Future[Option[Feature]] = for {
        _              <- persistentFeatureService.persist(deleted)
        featureDeleted <- persistentFeatureService.get(deleted.featureId)
      } yield featureDeleted

      whenReady(futureFeaturePersisted, Timeout(3.seconds)) { result =>
        result.map(_.featureId.value) shouldBe Some(deleted.featureId.value)
      }

      val futureFeature: Future[Option[Feature]] = for {
        _              <- persistentFeatureService.remove(deleted.featureId)
        featureDeleted <- persistentFeatureService.get(deleted.featureId)
      } yield featureDeleted

      whenReady(futureFeature, Timeout(3.seconds)) { result =>
        result shouldBe None
      }
    }
  }

}
