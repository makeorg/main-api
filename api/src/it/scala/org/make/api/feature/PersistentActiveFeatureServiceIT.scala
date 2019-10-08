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
import org.make.core.feature._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentActiveFeatureServiceIT
    extends DatabaseTest
    with DefaultPersistentActiveFeatureServiceComponent
    with DefaultPersistentFeatureServiceComponent
    with DefaultPersistentQuestionServiceComponent
    with DefaultIdGeneratorComponent {

  override protected val cockroachExposedPort: Int = 40009

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    persistentQuestionService.persist(
      Question(
        questionId = QuestionId("question-1"),
        slug = "question-1",
        country = Country("FR"),
        language = Language("fr"),
        question = "question ?",
        operationId = None,
        themeId = None
      )
    )
    persistentFeatureService.persist(Feature(featureId = FeatureId("feature"), name = "Feature", slug = "feature"))
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

  feature("One active feature can be persisted and retrieved") {
    scenario("Get activeFeature by activeFeatureId") {
      val futureFeature: Future[Option[ActiveFeature]] = for {
        _            <- persistentActiveFeatureService.persist(postalCode)
        featureStark <- persistentActiveFeatureService.get(postalCode.activeFeatureId)
      } yield featureStark

      whenReady(futureFeature, Timeout(3.seconds)) { result =>
        result.map(_.activeFeatureId) shouldBe Some(postalCode.activeFeatureId)
      }
    }

    scenario("Get active feature by activeFeatureId that does not exists") {
      val futureFeatureId: Future[Option[ActiveFeature]] = persistentActiveFeatureService.get(ActiveFeatureId("fake"))

      whenReady(futureFeatureId, Timeout(3.seconds)) { result =>
        result shouldBe None
      }
    }
  }

  feature("A list of features can be retrieved") {
    scenario("Get a list of all enabled features") {
      val futurePersistedFeatureList: Future[Seq[ActiveFeature]] = for {
        _                <- persistentActiveFeatureService.persist(noVotes)
        featureLannister <- persistentActiveFeatureService.persist(fieldHelp)
        featureBolton    <- persistentActiveFeatureService.persist(stream)
        featureGreyjoy   <- persistentActiveFeatureService.persist(rust)
      } yield Seq(featureLannister, featureBolton, featureGreyjoy)

      val futureFeaturesLists: Future[(Seq[ActiveFeature], Seq[ActiveFeature])] = for {
        persistedFeaturesList <- futurePersistedFeatureList
        foundFeatures <- persistentActiveFeatureService.find(
          start = 0,
          end = None,
          sort = None,
          order = None,
          maybeQuestionId = None
        )
      } yield foundFeatures -> persistedFeaturesList

      whenReady(futureFeaturesLists, Timeout(3.seconds)) {
        case (expectedActiveFeatures, allFeaturesPersisted) =>
          allFeaturesPersisted.foreach { activeFeature =>
            expectedActiveFeatures should contain(activeFeature)
          }
      }
    }

    scenario("Get a list of features by questionId") {

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
        listFeatures <- persistentActiveFeatureService.find(
          start = 0,
          end = None,
          sort = None,
          order = None,
          maybeQuestionId = Some(QuestionId("question-1"))
        )
      } yield listFeatures

      whenReady(futureFeaturesLists, Timeout(3.seconds)) { found =>
        found.size should be(3)
      }
    }
  }

  feature("One active feature can be deleted") {
    scenario("Delete active feature") {
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
