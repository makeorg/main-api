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
import org.make.core.feature.{FeatureId, FeatureSlug, Feature => Feat}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class FeatureServiceTest
    extends MakeUnitTest
    with DefaultFeatureServiceComponent
    with PersistentFeatureServiceComponent
    with DefaultIdGeneratorComponent {

  override val persistentFeatureService: PersistentFeatureService = mock[PersistentFeatureService]

  Feature("get feature") {
    Scenario("get feature from FeatureId") {
      featureService.getFeature(FeatureId("valid-feature"))

      verify(persistentFeatureService).get(FeatureId("valid-feature"))
    }
  }

  Feature("create feature") {
    Scenario("creating a feature success") {
      when(persistentFeatureService.get(any[FeatureId]))
        .thenReturn(Future.successful(None))

      val feature =
        Feat(featureId = FeatureId("new-feature"), slug = FeatureSlug("new-feature"), name = "new feature name")

      when(persistentFeatureService.persist(any[Feat]))
        .thenReturn(Future.successful(feature))

      val futureNewFeature: Future[Feat] = featureService.createFeature(FeatureSlug("new-feature"), "new feature name")

      whenReady(futureNewFeature, Timeout(3.seconds)) { _ =>
        verify(persistentFeatureService).persist(any[Feat])
      }
    }
  }

  Feature("find features") {

    Scenario("find features by slug") {
      val feature1 =
        Feat(featureId = FeatureId("find-feature-1"), slug = FeatureSlug("feature-1"), name = "Feature 1")
      val feature2 =
        Feat(featureId = FeatureId("find-feature-2"), slug = FeatureSlug("feature-2"), name = "Feature 2")
      reset(persistentFeatureService)
      when(persistentFeatureService.findAll())
        .thenReturn(Future.successful(Seq(feature1, feature2)))
      when(persistentFeatureService.findBySlug(eqTo(FeatureSlug("feature-1"))))
        .thenReturn(Future.successful(Seq(feature1)))

      val futureFeatures: Future[Seq[Feat]] =
        featureService.findBySlug(FeatureSlug("feature-1"))

      whenReady(futureFeatures, Timeout(3.seconds)) { features =>
        features.size shouldBe 1
        features.map(_.slug).contains(feature1.slug) shouldBe true
        features.map(_.slug).contains(feature2.slug) shouldBe false
      }
    }
  }

  Feature("update a feature") {
    Scenario("update a feature") {
      val oldFeature = Feat(FeatureId("1234567890"), "old Feature name", FeatureSlug("old-feature"))
      val newFeature = Feat(FeatureId("1234567890"), "new Feature name", FeatureSlug("new-feature"))
      when(persistentFeatureService.get(FeatureId("1234567890")))
        .thenReturn(Future.successful(Some(oldFeature)))
      when(persistentFeatureService.update(any[Feat]))
        .thenReturn(Future.successful(Some(newFeature)))

      val futureFeature: Future[Option[Feat]] =
        featureService.updateFeature(
          featureId = oldFeature.featureId,
          slug = FeatureSlug("new-feature"),
          name = "new Feature name"
        )

      whenReady(futureFeature, Timeout(3.seconds)) { feature =>
        feature.map(_.slug) shouldEqual Some(newFeature.slug)
      }
    }

    Scenario("update an non existent feature ") {
      when(persistentFeatureService.get(FeatureId("non-existent-feature"))).thenReturn(Future.successful(None))

      val futureFeature: Future[Option[Feat]] = featureService.updateFeature(
        featureId = FeatureId("non-existent-feature"),
        slug = FeatureSlug("new-non-existent-feature"),
        name = "whatever name"
      )

      whenReady(futureFeature) { feature =>
        feature shouldBe empty
      }
    }
  }

}
