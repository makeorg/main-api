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

import org.make.api.MakeUnitTest
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.core.feature._
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class FeatureServiceTest
    extends MakeUnitTest
    with DefaultFeatureServiceComponent
    with PersistentFeatureServiceComponent
    with DefaultIdGeneratorComponent {

  override val persistentFeatureService: PersistentFeatureService = mock[PersistentFeatureService]

  feature("get feature") {
    scenario("get feature from FeatureId") {
      featureService.getFeature(FeatureId("valid-feature"))

      Mockito.verify(persistentFeatureService).get(FeatureId("valid-feature"))
    }
  }

  feature("create feature") {
    scenario("creating a feature success") {
      Mockito
        .when(persistentFeatureService.get(ArgumentMatchers.any[FeatureId]))
        .thenReturn(Future.successful(None))

      val feature =
        Feature(featureId = FeatureId("new-feature"), slug = "new-feature", name = "new feature name")

      Mockito
        .when(persistentFeatureService.persist(ArgumentMatchers.any[Feature]))
        .thenReturn(Future.successful(feature))

      val futureNewFeature: Future[Feature] = featureService.createFeature("new-feature", "new feature name")

      whenReady(futureNewFeature, Timeout(3.seconds)) { _ =>
        Mockito.verify(persistentFeatureService).persist(ArgumentMatchers.any[Feature])
      }
    }
  }

  feature("find features") {

    scenario("find features by slug") {
      val feature1 =
        Feature(featureId = FeatureId("find-feature-1"), slug = "feature-1", name = "Feature 1")
      val feature2 =
        Feature(featureId = FeatureId("find-feature-2"), slug = "feature-2", name = "Feature 2")
      Mockito.reset(persistentFeatureService)
      Mockito
        .when(persistentFeatureService.findAll())
        .thenReturn(Future.successful(Seq(feature1, feature2)))
      Mockito
        .when(persistentFeatureService.findBySlug(ArgumentMatchers.eq("feature-1")))
        .thenReturn(Future.successful(Seq(feature1)))

      val futureFeatures: Future[Seq[Feature]] =
        featureService.findBySlug("feature-1")

      whenReady(futureFeatures, Timeout(3.seconds)) { features =>
        features.size shouldBe 1
        features.map(_.slug).contains(feature1.slug) shouldBe true
        features.map(_.slug).contains(feature2.slug) shouldBe false
      }
    }
  }

  feature("update a feature") {
    scenario("update a feature") {
      val oldFeature = Feature(FeatureId("1234567890"), "old-feature", "old Feature name")
      val newFeature = Feature(FeatureId("1234567890"), "new-feature", "new Feature name")
      Mockito
        .when(persistentFeatureService.get(FeatureId("1234567890")))
        .thenReturn(Future.successful(Some(oldFeature)))
      Mockito
        .when(persistentFeatureService.update(ArgumentMatchers.any[Feature]))
        .thenReturn(Future.successful(Some(newFeature)))

      val futureFeature: Future[Option[Feature]] =
        featureService.updateFeature(featureId = oldFeature.featureId, slug = "new-feature", name = "new Feature name")

      whenReady(futureFeature, Timeout(3.seconds)) { feature =>
        feature.map(_.slug) shouldEqual Some(newFeature.slug)
      }
    }

    scenario("update an non existent feature ") {
      Mockito.when(persistentFeatureService.get(FeatureId("non-existent-feature"))).thenReturn(Future.successful(None))

      val futureFeature: Future[Option[Feature]] = featureService.updateFeature(
        featureId = FeatureId("non-existent-feature"),
        slug = "new-non-existent-feature",
        name = "whatever name"
      )

      whenReady(futureFeature) { feature =>
        feature shouldBe empty
      }
    }
  }

}
