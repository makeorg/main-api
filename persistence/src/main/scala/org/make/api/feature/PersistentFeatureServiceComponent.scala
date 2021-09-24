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

import org.make.core.feature.{Feature, FeatureId, FeatureSlug}
import org.make.core.Order

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait PersistentFeatureServiceComponent {
  def persistentFeatureService: PersistentFeatureService
}

trait PersistentFeatureService {
  def get(featureId: FeatureId): Future[Option[Feature]]
  def findBySlug(slug: FeatureSlug): Future[Seq[Feature]]
  def persist(feature: Feature): Future[Feature]
  def update(feature: Feature): Future[Option[Feature]]
  def remove(featureId: FeatureId): Future[Unit]
  def findAll(): Future[Seq[Feature]]
  def findByFeatureIds(featureIds: Seq[FeatureId]): Future[Seq[Feature]]
  def find(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    slug: Option[String]
  ): Future[Seq[Feature]]
  def count(slug: Option[String]): Future[Int]
}
