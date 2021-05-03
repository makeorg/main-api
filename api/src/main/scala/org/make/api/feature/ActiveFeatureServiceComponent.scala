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

import org.make.api.technical._
import org.make.core.feature._
import org.make.core.question.QuestionId
import org.make.core.Order

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait ActiveFeatureServiceComponent {
  def activeFeatureService: ActiveFeatureService
}

trait ActiveFeatureService extends ShortenedNames {
  def getActiveFeature(slug: ActiveFeatureId): Future[Option[ActiveFeature]]
  def createActiveFeature(featureId: FeatureId, maybeQuestionId: Option[QuestionId]): Future[ActiveFeature]
  def deleteActiveFeature(activeFeatureId: ActiveFeatureId): Future[Unit]
  def find(
    start: Start = Start.zero,
    end: Option[End] = None,
    sort: Option[String] = None,
    order: Option[Order] = None,
    maybeQuestionId: Option[Seq[QuestionId]] = None,
    featureId: Option[Seq[FeatureId]] = None
  ): Future[Seq[ActiveFeature]]
  def count(maybeQuestionId: Option[QuestionId]): Future[Int]
}

trait DefaultActiveFeatureServiceComponent extends ActiveFeatureServiceComponent with ShortenedNames {
  this: PersistentActiveFeatureServiceComponent with IdGeneratorComponent =>

  override lazy val activeFeatureService: ActiveFeatureService = new DefaultActiveFeatureService

  class DefaultActiveFeatureService extends ActiveFeatureService {

    override def getActiveFeature(activeFeatureId: ActiveFeatureId): Future[Option[ActiveFeature]] = {
      persistentActiveFeatureService.get(activeFeatureId)
    }

    override def createActiveFeature(
      featureId: FeatureId,
      maybeQuestionId: Option[QuestionId]
    ): Future[ActiveFeature] = {
      persistentActiveFeatureService.persist(
        ActiveFeature(
          activeFeatureId = idGenerator.nextActiveFeatureId(),
          featureId = featureId,
          maybeQuestionId = maybeQuestionId
        )
      )
    }

    override def deleteActiveFeature(activeFeatureId: ActiveFeatureId): Future[Unit] = {
      persistentActiveFeatureService.remove(activeFeatureId)
    }

    override def find(
      start: Start = Start.zero,
      end: Option[End] = None,
      sort: Option[String] = None,
      order: Option[Order] = None,
      maybeQuestionId: Option[Seq[QuestionId]] = None,
      featureId: Option[Seq[FeatureId]] = None
    ): Future[Seq[ActiveFeature]] = {

      persistentActiveFeatureService.find(start, end, sort, order, maybeQuestionId, featureId)

    }

    override def count(maybeQuestionId: Option[QuestionId]): Future[Int] = {
      persistentActiveFeatureService.count(maybeQuestionId)
    }
  }
}
