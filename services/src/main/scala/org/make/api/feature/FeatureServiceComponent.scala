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

import org.make.core.feature._
import org.make.core.Order

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait FeatureServiceComponent {
  def featureService: FeatureService
}

trait FeatureService {
  def getFeature(featureId: FeatureId): Future[Option[Feature]]
  def createFeature(slug: FeatureSlug, name: String): Future[Feature]
  def findBySlug(slug: FeatureSlug): Future[Seq[Feature]]
  def updateFeature(featureId: FeatureId, slug: FeatureSlug, name: String): Future[Option[Feature]]
  def deleteFeature(featureId: FeatureId): Future[Unit]
  def findByFeatureIds(featureIds: Seq[FeatureId]): Future[Seq[Feature]]
  def find(
    start: Start = Start.zero,
    end: Option[End] = None,
    sort: Option[String] = None,
    order: Option[Order] = None,
    slug: Option[String]
  ): Future[Seq[Feature]]
  def count(slug: Option[String]): Future[Int]
}
