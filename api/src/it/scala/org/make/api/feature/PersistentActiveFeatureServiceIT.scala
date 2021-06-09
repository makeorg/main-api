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

  override def beforeAll(): Unit = {
    super.beforeAll()
    val futurePersistQuestions: Future[Unit] = for {
      _ <- persistentQuestionService.persist(question(QuestionId("question-1"), slug = "question-1"))
      _ <- persistentQuestionService.persist(question(QuestionId("question-2"), slug = "question-2"))
    } yield {}
    val futurePersistFeatures = for {
      _ <- persistentFeatureService.persist(Feat(featureId = FeatureId("feature"), name = "Feature", slug = "feature"))
      _ <- persistentFeatureService.persist(
        Feat(featureId = FeatureId("feature2"), name = "Feature2", slug = "feature2")
      )
      _ <- persistentFeatureService.persist(
        Feat(featureId = FeatureId("feature-search"), name = "Feature search", slug = "feature-search")
      )
      _ <- persistentFeatureService.persist(
        Feat(featureId = FeatureId("feature-search2"), name = "Feature search 2", slug = "feature-search2")
      )
    } yield {}

    whenReady(futurePersistQuestions, Timeout(10.seconds)) { _ =>
      ()
    }
    whenReady(futurePersistFeatures, Timeout(10.seconds)) { _ =>
      ()
    }
  }

  def newActiveFeature(featureId: FeatureId, maybeQuestionId: Option[QuestionId]): ActiveFeature =
    ActiveFeature(
      activeFeatureId = idGenerator.nextActiveFeatureId(),
      featureId = featureId,
      maybeQuestionId = maybeQuestionId
    )

  val postalCode: ActiveFeature = newActiveFeature(FeatureId("feature"), None)
  val noVotes: ActiveFeature = newActiveFeature(FeatureId("feature"), None)
  val fieldHelp: ActiveFeature = newActiveFeature(FeatureId("feature"), None)
  val stream: ActiveFeature = newActiveFeature(FeatureId("feature"), None)
  val rust: ActiveFeature = newActiveFeature(FeatureId("feature"), None)
  val feature: ActiveFeature = newActiveFeature(FeatureId("feature"), None)
  val deleted: ActiveFeature = newActiveFeature(FeatureId("feature"), None)
  val question: ActiveFeature = newActiveFeature(FeatureId("feature"), None)

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
      val futurePersistedFeatureList: Future[Seq[ActiveFeature]] = for {
        _                <- persistentActiveFeatureService.persist(noVotes)
        featureLannister <- persistentActiveFeatureService.persist(fieldHelp)
        featureBolton    <- persistentActiveFeatureService.persist(stream)
        featureGreyjoy   <- persistentActiveFeatureService.persist(rust)
      } yield Seq(featureLannister, featureBolton, featureGreyjoy)

      val futureFeaturesLists: Future[(Seq[ActiveFeature], Seq[ActiveFeature])] = for {
        persistedFeaturesList <- futurePersistedFeatureList
        foundFeatures <- persistentActiveFeatureService.find(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          maybeQuestionIds = None,
          featureIds = None
        )
      } yield foundFeatures -> persistedFeaturesList

      whenReady(futureFeaturesLists, Timeout(3.seconds)) {
        case (expectedActiveFeatures, allFeaturesPersisted) =>
          allFeaturesPersisted.foreach { activeFeature =>
            expectedActiveFeatures should contain(activeFeature)
          }
      }
    }

    Scenario("Get a list of features by questionIds") {

      val futureFeaturesLists: Future[Seq[ActiveFeature]] = for {
        _ <- persistentActiveFeatureService.persist(
          newActiveFeature(FeatureId("feature"), Some(QuestionId("question-1")))
        )
        _ <- persistentActiveFeatureService.persist(
          newActiveFeature(FeatureId("feature"), Some(QuestionId("question-1")))
        )
        _ <- persistentActiveFeatureService.persist(
          newActiveFeature(FeatureId("feature"), Some(QuestionId("question-1")))
        )
        _ <- persistentActiveFeatureService.persist(
          newActiveFeature(FeatureId("feature"), Some(QuestionId("question-2")))
        )
        listFeatures <- persistentActiveFeatureService.find(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          maybeQuestionIds = Some(Seq(QuestionId("question-1"), QuestionId("question-2"))),
          featureIds = None
        )
      } yield listFeatures

      whenReady(futureFeaturesLists, Timeout(3.seconds)) { found =>
        found.size should be(4)
      }
    }

    Scenario("Get a list of features by featureIds") {

      val futureFeaturesLists: Future[Seq[ActiveFeature]] = for {
        _ <- persistentActiveFeatureService.persist(
          newActiveFeature(FeatureId("feature-search"), Some(QuestionId("question-1")))
        )
        _ <- persistentActiveFeatureService.persist(
          newActiveFeature(FeatureId("feature-search2"), Some(QuestionId("question-1")))
        )
        _ <- persistentActiveFeatureService.persist(
          newActiveFeature(FeatureId("feature-search2"), Some(QuestionId("question-2")))
        )
        listFeatures <- persistentActiveFeatureService.find(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          maybeQuestionIds = None,
          featureIds = Some(Seq(FeatureId("feature-search"), FeatureId("feature-search2")))
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
