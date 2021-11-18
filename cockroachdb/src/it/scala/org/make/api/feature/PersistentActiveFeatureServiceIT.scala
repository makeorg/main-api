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
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.core.feature.{Feature => Feat, _}
import org.make.core.question.QuestionId
import org.make.core.technical.Pagination.Start
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentActiveFeatureServiceIT
    extends DatabaseTest
    with DefaultPersistentActiveFeatureServiceComponent
    with DefaultPersistentFeatureServiceComponent
    with DefaultPersistentQuestionServiceComponent
    with DefaultIdGeneratorComponent {

  val postalCode: ActiveFeature = newActiveFeature(FeatureId("postalCode"), None)
  val noVotes: ActiveFeature = newActiveFeature(FeatureId("noVotes"), Some(QuestionId("question-1")))
  val fieldHelp: ActiveFeature = newActiveFeature(FeatureId("fieldHelp"), Some(QuestionId("question-1")))
  val stream: ActiveFeature = newActiveFeature(FeatureId("stream"), Some(QuestionId("question-1")))
  val rust: ActiveFeature = newActiveFeature(FeatureId("rust"), Some(QuestionId("question-2")))
  val deleted: ActiveFeature = newActiveFeature(FeatureId("feature-delete"), None)

  override def beforeAll(): Unit = {
    super.beforeAll()
    val futurePersistQuestions: Future[Unit] = for {
      _ <- persistentQuestionService.persist(question(QuestionId("question-1"), slug = "question-1"))
      _ <- persistentQuestionService.persist(question(QuestionId("question-2"), slug = "question-2"))
    } yield {}
    val futurePersistFeatures = for {
      _ <- persistentFeatureService.persist(
        Feat(featureId = FeatureId("postalCode"), name = "Postal code", slug = FeatureSlug("postal-code"))
      )
      _ <- persistentFeatureService.persist(
        Feat(featureId = FeatureId("noVotes"), name = "No votes", slug = FeatureSlug("no-votes"))
      )
      _ <- persistentFeatureService.persist(
        Feat(featureId = FeatureId("fieldHelp"), name = "Field help", slug = FeatureSlug("field-help"))
      )
      _ <- persistentFeatureService.persist(
        Feat(featureId = FeatureId("stream"), name = "Stream", slug = FeatureSlug("stream"))
      )
      _ <- persistentFeatureService.persist(
        Feat(featureId = FeatureId("rust"), name = "Rust", slug = FeatureSlug("rust"))
      )
      _ <- persistentFeatureService.persist(
        Feat(featureId = FeatureId("feature-delete"), name = "To delete", slug = FeatureSlug("delete"))
      )
    } yield {}
    whenReady(futurePersistQuestions, Timeout(10.seconds)) { _ =>
      ()
    }
    whenReady(futurePersistFeatures, Timeout(10.seconds)) { _ =>
      ()
    }

    val futurePersistActiveFeatures = for {
      _ <- persistentActiveFeatureService.persist(noVotes)
      _ <- persistentActiveFeatureService.persist(fieldHelp)
      _ <- persistentActiveFeatureService.persist(stream)
      _ <- persistentActiveFeatureService.persist(rust)
    } yield {}

    whenReady(futurePersistActiveFeatures, Timeout(10.seconds)) { _ =>
      ()
    }
  }

  def newActiveFeature(featureId: FeatureId, maybeQuestionId: Option[QuestionId]): ActiveFeature =
    ActiveFeature(
      activeFeatureId = idGenerator.nextActiveFeatureId(),
      featureId = featureId,
      maybeQuestionId = maybeQuestionId
    )

  Feature("One active feature can be persisted and retrieved") {
    Scenario("Get activeFeature by activeFeatureId") {
      val futureFeature: Future[Option[ActiveFeature]] = for {
        _            <- persistentActiveFeatureService.persist(postalCode)
        featureStark <- persistentActiveFeatureService.get(postalCode.activeFeatureId)
      } yield featureStark

      whenReady(futureFeature, Timeout(3.seconds)) { result =>
        result.map(_.activeFeatureId) shouldBe Some(postalCode.activeFeatureId)
      }
    }

    Scenario("Get active feature by activeFeatureId that does not exists") {
      val futureFeatureId: Future[Option[ActiveFeature]] = persistentActiveFeatureService.get(ActiveFeatureId("fake"))

      whenReady(futureFeatureId, Timeout(3.seconds)) { result =>
        result shouldBe None
      }
    }
  }

  Feature("A list of features can be retrieved") {
    Scenario("Get a list of all enabled features") {
      val futureFeaturesLists: Future[Seq[ActiveFeature]] =
        persistentActiveFeatureService.find(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          maybeQuestionIds = None,
          featureIds = None
        )

      whenReady(futureFeaturesLists, Timeout(3.seconds)) { expectedActiveFeatures =>
        Seq(fieldHelp, stream, rust).foreach { activeFeature =>
          expectedActiveFeatures should contain(activeFeature)
        }
      }
    }

    Scenario("Get a list of features by questionIds") {

      val futureFeaturesLists: Future[Seq[ActiveFeature]] =
        persistentActiveFeatureService.find(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          maybeQuestionIds = Some(Seq(QuestionId("question-1"), QuestionId("question-2"))),
          featureIds = None
        )

      whenReady(futureFeaturesLists, Timeout(3.seconds)) { found =>
        found.size should be(4)
      }
    }

    Scenario("Get a list of features by featureIds") {

      val futureFeaturesLists: Future[Seq[ActiveFeature]] = for {
        _ <- persistentActiveFeatureService.persist(newActiveFeature(FeatureId("rust"), Some(QuestionId("question-1"))))
        listFeatures <- persistentActiveFeatureService.find(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          maybeQuestionIds = None,
          featureIds = Some(Seq(FeatureId("stream"), FeatureId("rust")))
        )
      } yield listFeatures

      whenReady(futureFeaturesLists, Timeout(3.seconds)) { found =>
        found.size should be(3)
      }
    }
  }

  Feature("One active feature can be deleted") {
    Scenario("Delete active feature") {
      val futureFeaturePersisted: Future[Option[ActiveFeature]] = for {
        _              <- persistentActiveFeatureService.persist(deleted)
        featureDeleted <- persistentActiveFeatureService.get(deleted.activeFeatureId)
      } yield featureDeleted

      whenReady(futureFeaturePersisted, Timeout(3.seconds)) { result =>
        result.map(_.activeFeatureId.value) shouldBe Some(deleted.activeFeatureId.value)
      }

      val futureFeature: Future[Option[ActiveFeature]] = for {
        _              <- persistentActiveFeatureService.remove(deleted.activeFeatureId)
        featureDeleted <- persistentActiveFeatureService.get(deleted.activeFeatureId)
      } yield featureDeleted

      whenReady(futureFeature, Timeout(3.seconds)) { result =>
        result shouldBe None
      }
    }
  }
}
