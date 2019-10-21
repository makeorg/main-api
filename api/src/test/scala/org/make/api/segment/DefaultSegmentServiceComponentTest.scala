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
import org.make.core.feature.{ActiveFeature, ActiveFeatureId, Feature, FeatureId}
import org.make.core.question.QuestionId
import org.mockito.Mockito
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

  feature("segment resolution") {

    scenario("no question in request context") {
      whenReady(segmentService.resolveSegment(RequestContext.empty), Timeout(2.seconds)) { _ should be(None) }
    }

    scenario("no feature for the question") {
      Mockito
        .when(activeFeatureService.find(maybeQuestionId = Some(QuestionId("no-feature"))))
        .thenReturn(Future.successful(Seq.empty))

      Mockito
        .when(featureService.findByFeatureIds(Seq.empty))
        .thenReturn(Future.successful(Seq.empty))

      whenReady(
        segmentService.resolveSegment(RequestContext.empty.copy(questionId = Some(QuestionId("no-feature")))),
        Timeout(2.seconds)
      ) { _ should be(None) }
    }

    scenario("feature flip segment_from_department declared but no data in custom data") {
      val featureId = FeatureId("got-feature")

      Mockito
        .when(activeFeatureService.find(maybeQuestionId = Some(QuestionId("got-feature"))))
        .thenReturn(
          Future
            .successful(Seq(ActiveFeature(ActiveFeatureId("who cares?"), featureId, Some(QuestionId("got-feature")))))
        )

      Mockito
        .when(featureService.findByFeatureIds(Seq(featureId)))
        .thenReturn(
          Future.successful(Seq(Feature(featureId = featureId, name = "blabla", slug = "segment_from_department")))
        )

      whenReady(
        segmentService.resolveSegment(RequestContext.empty.copy(questionId = Some(QuestionId("got-feature")))),
        Timeout(2.seconds)
      ) { _ should be(None) }
    }

    scenario("feature flip segment_from_department declared and declared_department custom data") {
      val featureId = FeatureId("got-feature2")

      Mockito
        .when(activeFeatureService.find(maybeQuestionId = Some(QuestionId("got-feature2"))))
        .thenReturn(
          Future
            .successful(Seq(ActiveFeature(ActiveFeatureId("who cares?"), featureId, Some(QuestionId("got-feature2")))))
        )

      Mockito
        .when(featureService.findByFeatureIds(Seq(featureId)))
        .thenReturn(
          Future.successful(Seq(Feature(featureId = featureId, name = "blabla", slug = "segment_from_department")))
        )

      whenReady(
        segmentService.resolveSegment(
          RequestContext.empty
            .copy(questionId = Some(QuestionId("got-feature2")), customData = Map("declared_department" -> "29"))
        ),
        Timeout(2.seconds)
      ) { _ should be(Some("29")) }
    }

    scenario("feature flip segment_from_department declared and detected_department custom data") {
      val featureId = FeatureId("got-feature3")

      Mockito
        .when(activeFeatureService.find(maybeQuestionId = Some(QuestionId("got-feature3"))))
        .thenReturn(
          Future
            .successful(Seq(ActiveFeature(ActiveFeatureId("who cares?"), featureId, Some(QuestionId("got-feature3")))))
        )

      Mockito
        .when(featureService.findByFeatureIds(Seq(featureId)))
        .thenReturn(
          Future.successful(Seq(Feature(featureId = featureId, name = "blabla", slug = "segment_from_department")))
        )

      whenReady(
        segmentService.resolveSegment(
          RequestContext.empty
            .copy(questionId = Some(QuestionId("got-feature3")), customData = Map("detected_department" -> "35"))
        ),
        Timeout(2.seconds)
      ) { _ should be(Some("35")) }
    }

    scenario("feature flip segment_from_department declared and declared_department + detected_department custom data") {
      val featureId = FeatureId("got-feature4")

      Mockito
        .when(activeFeatureService.find(maybeQuestionId = Some(QuestionId("got-feature4"))))
        .thenReturn(
          Future
            .successful(Seq(ActiveFeature(ActiveFeatureId("who cares?"), featureId, Some(QuestionId("got-feature4")))))
        )

      Mockito
        .when(featureService.findByFeatureIds(Seq(featureId)))
        .thenReturn(
          Future.successful(Seq(Feature(featureId = featureId, name = "blabla", slug = "segment_from_department")))
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
