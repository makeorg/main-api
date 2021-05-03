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

package org.make.api.segment

import org.make.api.MakeUnitTest
import org.make.api.feature.{
  ActiveFeatureService,
  ActiveFeatureServiceComponent,
  FeatureService,
  FeatureServiceComponent
}
import org.make.core.RequestContext
import org.make.core.feature.{ActiveFeature, ActiveFeatureId, Feature => Feat, FeatureId}
import org.make.core.question.QuestionId
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class DefaultSegmentServiceComponentTest
    extends MakeUnitTest
    with DefaultSegmentServiceComponent
    with ActiveFeatureServiceComponent
    with FeatureServiceComponent {

  override val activeFeatureService: ActiveFeatureService = mock[ActiveFeatureService]
  override val featureService: FeatureService = mock[FeatureService]

  Feature("segment resolution") {

    Scenario("no question in request context") {
      whenReady(segmentService.resolveSegment(RequestContext.empty), Timeout(2.seconds)) { _ should be(None) }
    }

    Scenario("no feature for the question") {
      when(activeFeatureService.find(maybeQuestionId = Some(Seq(QuestionId("no-feature")))))
        .thenReturn(Future.successful(Seq.empty))

      when(featureService.findByFeatureIds(Seq.empty))
        .thenReturn(Future.successful(Seq.empty))

      whenReady(
        segmentService.resolveSegment(RequestContext.empty.copy(questionId = Some(QuestionId("no-feature")))),
        Timeout(2.seconds)
      ) { _ should be(None) }
    }

    Scenario("feature flip consultation-department-compulsory declared but no data in custom data") {
      val featureId = FeatureId("got-feature")

      when(activeFeatureService.find(maybeQuestionId = Some(Seq(QuestionId("got-feature")))))
        .thenReturn(
          Future
            .successful(Seq(ActiveFeature(ActiveFeatureId("who cares?"), featureId, Some(QuestionId("got-feature")))))
        )

      when(featureService.findByFeatureIds(Seq(featureId)))
        .thenReturn(
          Future
            .successful(Seq(Feat(featureId = featureId, name = "blabla", slug = "consultation-department-compulsory")))
        )

      whenReady(
        segmentService.resolveSegment(RequestContext.empty.copy(questionId = Some(QuestionId("got-feature")))),
        Timeout(2.seconds)
      ) { _ should be(None) }
    }

    Scenario("feature flip consultation-department-compulsory declared and declared_department custom data") {
      val featureId = FeatureId("got-feature2")

      when(activeFeatureService.find(maybeQuestionId = Some(Seq(QuestionId("got-feature2")))))
        .thenReturn(
          Future
            .successful(Seq(ActiveFeature(ActiveFeatureId("who cares?"), featureId, Some(QuestionId("got-feature2")))))
        )

      when(featureService.findByFeatureIds(Seq(featureId)))
        .thenReturn(
          Future
            .successful(Seq(Feat(featureId = featureId, name = "blabla", slug = "consultation-department-compulsory")))
        )

      whenReady(
        segmentService.resolveSegment(
          RequestContext.empty
            .copy(questionId = Some(QuestionId("got-feature2")), customData = Map("declared_department" -> "29"))
        ),
        Timeout(2.seconds)
      ) { _ should be(Some("29")) }
    }

    Scenario("feature flip consultation-department-compulsory declared and detected_department custom data") {
      val featureId = FeatureId("got-feature3")

      when(activeFeatureService.find(maybeQuestionId = Some(Seq(QuestionId("got-feature3")))))
        .thenReturn(
          Future
            .successful(Seq(ActiveFeature(ActiveFeatureId("who cares?"), featureId, Some(QuestionId("got-feature3")))))
        )

      when(featureService.findByFeatureIds(Seq(featureId)))
        .thenReturn(
          Future
            .successful(Seq(Feat(featureId = featureId, name = "blabla", slug = "consultation-department-compulsory")))
        )

      whenReady(
        segmentService.resolveSegment(
          RequestContext.empty
            .copy(questionId = Some(QuestionId("got-feature3")), customData = Map("detected_department" -> "35"))
        ),
        Timeout(2.seconds)
      ) { _ should be(Some("35")) }
    }

    Scenario(
      "feature flip consultation-department-compulsory declared and declared_department + detected_department custom data"
    ) {
      val featureId = FeatureId("got-feature4")

      when(activeFeatureService.find(maybeQuestionId = Some(Seq(QuestionId("got-feature4")))))
        .thenReturn(
          Future
            .successful(Seq(ActiveFeature(ActiveFeatureId("who cares?"), featureId, Some(QuestionId("got-feature4")))))
        )

      when(featureService.findByFeatureIds(Seq(featureId)))
        .thenReturn(
          Future
            .successful(Seq(Feat(featureId = featureId, name = "blabla", slug = "consultation-department-compulsory")))
        )

      whenReady(
        segmentService.resolveSegment(
          RequestContext.empty
            .copy(
              questionId = Some(QuestionId("got-feature4")),
              customData = Map("detected_department" -> "35", "declared_department" -> "29")
            )
        ),
        Timeout(2.seconds)
      ) { _ should be(Some("29")) }
    }

  }
}
