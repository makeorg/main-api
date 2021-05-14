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
import org.make.core.feature.{Feature => Feat, _}
import org.make.core.technical.Pagination
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentFeatureServiceIT
    extends DatabaseTest
    with DefaultPersistentFeatureServiceComponent
    with DefaultIdGeneratorComponent {

  override protected val cockroachExposedPort: Int = 40009

  def newFeature(slug: String): Feat =
    Feat(featureId = idGenerator.nextFeatureId(), slug = slug, name = "feature name")

  val postalCode: Feat = newFeature("postal-code")
  val noVotes: Feat = newFeature("no-votes")
  val fieldHelp: Feat = newFeature("field-1-help")
  val stream: Feat = newFeature("stream-1")
  val rust: Feat = newFeature("rust")
  val feature: Feat = newFeature("feature")
  val deleted: Feat = newFeature("deleted")

  Feature("One feature can be persisted and retrieved") {
    Scenario("Get feature by featureId") {
      val futureFeature: Future[Option[Feat]] = for {
        _            <- persistentFeatureService.persist(postalCode)
        featureStark <- persistentFeatureService.get(postalCode.featureId)
      } yield featureStark

      whenReady(futureFeature, Timeout(3.seconds)) { result =>
        result.map(_.slug) shouldBe Some("postal-code")
      }
    }

    Scenario("Get feature by featureId that does not exists") {
      val futureFeatureId: Future[Option[Feat]] = persistentFeatureService.get(FeatureId("fake"))

      whenReady(futureFeatureId, Timeout(3.seconds)) { result =>
        result shouldBe None
      }
    }
  }

  Feature("A list of features can be retrieved") {
    Scenario("Get a list of all enabled features") {
      val futurePersistedFeatureList: Future[Seq[Feat]] = for {
        _             <- persistentFeatureService.persist(noVotes)
        featureHelp   <- persistentFeatureService.persist(fieldHelp)
        featureStream <- persistentFeatureService.persist(stream)
        featureRust   <- persistentFeatureService.persist(rust)
      } yield Seq(featureHelp, featureStream, featureRust)

      val futureFeaturesLists: Future[(Seq[Feat], Seq[Feat])] = for {
        persistedFeaturesList <- futurePersistedFeatureList
        foundFeatures         <- persistentFeatureService.findAll()
      } yield foundFeatures -> persistedFeaturesList

      whenReady(futureFeaturesLists, Timeout(3.seconds)) {
        case (persisted, found) =>
          found.forall(persisted.contains) should be(true)
      }

      val findSlug: Future[Seq[Feat]] = persistentFeatureService.find(
        start = Pagination.Start.zero,
        end = None,
        sort = None,
        order = None,
        slug = Some("-1")
      )

      whenReady(findSlug, Timeout(3.seconds)) { features =>
        features.map(_.slug).toSet shouldBe Set("field-1-help", "stream-1")
      }

      whenReady(persistentFeatureService.count(Some("-1")), Timeout(3.seconds)) { total =>
        total shouldBe 2
      }
    }

    Scenario("Get features by featureIds") {
      val futurePersistedFeatureList: Future[Unit] = for {
        _ <- persistentFeatureService.persist(
          Feat(featureId = FeatureId("feature-1"), slug = "feature-1", name = "feature name")
        )
        _ <- persistentFeatureService.persist(
          Feat(featureId = FeatureId("feature-2"), slug = "feature-2", name = "feature name")
        )
        _ <- persistentFeatureService.persist(
          Feat(featureId = FeatureId("feature-3"), slug = "feature-3", name = "feature name")
        )
      } yield {}

      val futureFeaturesLists: Future[Seq[Feat]] = for {
        _        <- futurePersistedFeatureList
        features <- persistentFeatureService.findByFeatureIds(Seq(FeatureId("feature-1"), FeatureId("feature-2")))
      } yield features

      whenReady(futureFeaturesLists, Timeout(3.seconds)) { result =>
        result.size should be(2)
        result.map(_.featureId) should contain(FeatureId("feature-1"))
        result.map(_.featureId) should contain(FeatureId("feature-2"))
      }
    }
  }

  Feature("One feature can be updated") {
    Scenario("Update feature") {
      val futureFeature: Future[Option[Feat]] = for {
        _            <- persistentFeatureService.persist(feature)
        featureStark <- persistentFeatureService.update(feature.copy(slug = "new tully"))
      } yield featureStark

      whenReady(futureFeature, Timeout(3.seconds)) { result =>
        result.map(_.featureId.value) shouldBe Some(feature.featureId.value)
        result.map(_.slug) shouldBe Some("new tully")
      }
    }

    Scenario("Update feature that does not exists") {
      val futureFeatureId: Future[Option[Feat]] = persistentFeatureService.update(newFeature("fake"))

      whenReady(futureFeatureId, Timeout(3.seconds)) { result =>
        result shouldBe None
      }
    }
  }

  Feature("One feature can be deleted") {
    Scenario("Delete feature") {
      val futureFeaturePersisted: Future[Option[Feat]] = for {
        _              <- persistentFeatureService.persist(deleted)
        featureDeleted <- persistentFeatureService.get(deleted.featureId)
      } yield featureDeleted

      whenReady(futureFeaturePersisted, Timeout(3.seconds)) { result =>
        result.map(_.featureId.value) shouldBe Some(deleted.featureId.value)
      }

      val futureFeature: Future[Option[Feat]] = for {
        _              <- persistentFeatureService.remove(deleted.featureId)
        featureDeleted <- persistentFeatureService.get(deleted.featureId)
      } yield featureDeleted

      whenReady(futureFeature, Timeout(3.seconds)) { result =>
        result shouldBe None
      }
    }
  }
}
