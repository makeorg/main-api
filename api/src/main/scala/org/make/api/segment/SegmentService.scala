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

import org.make.api.feature.{ActiveFeatureServiceComponent, FeatureServiceComponent}
import org.make.core.RequestContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SegmentService {
  def resolveSegment(requestContext: RequestContext): Future[Option[String]]
}

trait SegmentServiceComponent {
  def segmentService: SegmentService
}

trait DefaultSegmentServiceComponent extends SegmentServiceComponent {
  self: ActiveFeatureServiceComponent with FeatureServiceComponent =>

  override lazy val segmentService: DefaultSegmentService = new DefaultSegmentService

  class DefaultSegmentService extends SegmentService {
    type SegmentResolver = RequestContext => Future[Option[String]]

    val defaultSegmentResolver: SegmentResolver = { _ =>
      Future.successful(None)
    }

    val segmentResolverFromDepartment: SegmentResolver = { requestContext =>
      val customData = requestContext.customData
      Future.successful(
        customData
          .get("declared_department")
          .orElse(customData.get("detected_department"))
      )
    }

    val segmentResolvers: Map[String, SegmentResolver] = Map(
      "consultation-department-compulsory" -> segmentResolverFromDepartment,
      "segment_from_department" -> segmentResolverFromDepartment
    )

    override def resolveSegment(requestContext: RequestContext): Future[Option[String]] = {
      requestContext.questionId.map { _ =>
        activeFeatureService
          .find(maybeQuestionId = requestContext.questionId)
          .flatMap { activeFeatures =>
            featureService.findByFeatureIds(activeFeatures.map(_.featureId))
          }
          .flatMap { features =>
            features
              .flatMap(feature => segmentResolvers.get(feature.slug))
              .headOption
              .getOrElse(defaultSegmentResolver)(requestContext)
          }
      }.getOrElse(Future.successful(None))
    }
  }

}
