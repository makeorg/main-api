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

import org.make.core.feature.{ActiveFeature, ActiveFeatureId, FeatureId}
import org.make.core.question.QuestionId
import org.make.core.Order
import org.make.core.technical.Pagination._

import scala.concurrent.Future

trait PersistentActiveFeatureServiceComponent {
  def persistentActiveFeatureService: PersistentActiveFeatureService
}

trait PersistentActiveFeatureService {
  def get(activeFeatureId: ActiveFeatureId): Future[Option[ActiveFeature]]
  def persist(activeFeature: ActiveFeature): Future[ActiveFeature]
  def remove(activeFeatureId: ActiveFeatureId): Future[Unit]
  def find(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    maybeQuestionIds: Option[Seq[QuestionId]],
    featureIds: Option[Seq[FeatureId]]
  ): Future[Seq[ActiveFeature]]
  def count(maybeQuestionId: Option[QuestionId]): Future[Int]
}
