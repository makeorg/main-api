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
import org.make.core.question.QuestionId
import org.make.core.technical.IdGenerator
import org.make.core.Order
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ActiveFeatureServiceTest
    extends MakeUnitTest
    with DefaultActiveFeatureServiceComponent
    with PersistentActiveFeatureServiceComponent
    with DefaultIdGeneratorComponent {

  override val persistentActiveFeatureService: PersistentActiveFeatureService = mock[PersistentActiveFeatureService]

  override lazy val idGenerator: IdGenerator = mock[IdGenerator]

  Feature("get activeFeature") {
    Scenario("get activeFeature from ActiveFeatureId") {
      activeFeatureService.getActiveFeature(ActiveFeatureId("valid-activeFeature"))

      verify(persistentActiveFeatureService).get(ActiveFeatureId("valid-activeFeature"))
    }
  }

  Feature("create activeFeature") {
    Scenario("creating a activeFeature success") {

      val activeFeature =
        ActiveFeature(
          activeFeatureId = ActiveFeatureId("new-activeFeature"),
          featureId = FeatureId("feature"),
          maybeQuestionId = Some(QuestionId("question"))
        )

      when(persistentActiveFeatureService.persist(any[ActiveFeature]))
        .thenReturn(Future.successful(activeFeature))

      when(idGenerator.nextActiveFeatureId()).thenReturn(ActiveFeatureId("new-activeFeature"))

      val futureNewActiveFeature: Future[ActiveFeature] =
        activeFeatureService.createActiveFeature(FeatureId("feature"), Some(QuestionId("question")))

      whenReady(futureNewActiveFeature, Timeout(3.seconds)) { _ =>
        verify(persistentActiveFeatureService)
          .persist(
            eqTo(
              ActiveFeature(
                activeFeatureId = ActiveFeatureId("new-activeFeature"),
                featureId = FeatureId("feature"),
                maybeQuestionId = Some(QuestionId("question"))
              )
            )
          )
      }
    }
  }

  Feature("find activeFeatures") {

    Scenario("find activeFeatures by featureId") {
      val activeFeature1 =
        ActiveFeature(
          activeFeatureId = ActiveFeatureId("find-activeFeature-1"),
          featureId = FeatureId("feature"),
          maybeQuestionId = Some(QuestionId("find-activeFeature-1"))
        )
      val activeFeature2 =
        ActiveFeature(
          activeFeatureId = ActiveFeatureId("find-activeFeature-2"),
          featureId = FeatureId("feature"),
          maybeQuestionId = None
        )
      reset(persistentActiveFeatureService)
      when(
        persistentActiveFeatureService.find(
          any[Int],
          any[Option[Int]],
          any[Option[String]],
          any[Option[Order]],
          eqTo(Some(QuestionId("find-activeFeature-1")))
        )
      ).thenReturn(Future.successful(Seq(activeFeature1)))

      val futureActiveFeatures = activeFeatureService.find(maybeQuestionId = Some(QuestionId("find-activeFeature-1")))

      whenReady(futureActiveFeatures, Timeout(3.seconds)) { activeFeatures =>
        activeFeatures.size shouldBe 1
        activeFeatures.map(_.activeFeatureId) should contain(activeFeature1.activeFeatureId)
        activeFeatures.map(_.activeFeatureId) should not contain activeFeature2.activeFeatureId
      }
    }
  }

}
