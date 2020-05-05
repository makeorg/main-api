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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FeatureServiceComponent {
  def featureService: FeatureService
}

trait FeatureService extends ShortenedNames {
  def getFeature(slug: FeatureId): Future[Option[Feature]]
  def createFeature(slug: String, name: String): Future[Feature]
  def findBySlug(partialSlug: String): Future[Seq[Feature]]
  def updateFeature(featureId: FeatureId, slug: String, name: String): Future[Option[Feature]]
  def deleteFeature(featureId: FeatureId): Future[Unit]
  def findByFeatureIds(featureIds: Seq[FeatureId]): Future[Seq[Feature]]
  def find(
    start: Int = 0,
    end: Option[Int] = None,
    sort: Option[String] = None,
    order: Option[String] = None,
    slug: Option[String]
  ): Future[Seq[Feature]]
  def count(slug: Option[String]): Future[Int]
}

trait DefaultFeatureServiceComponent extends FeatureServiceComponent with ShortenedNames {
  this: PersistentFeatureServiceComponent with IdGeneratorComponent =>

  override lazy val featureService: FeatureService = new DefaultFeatureService

  class DefaultFeatureService extends FeatureService {

    override def getFeature(featureId: FeatureId): Future[Option[Feature]] = {
      persistentFeatureService.get(featureId)
    }

    override def createFeature(slug: String, name: String): Future[Feature] = {
      persistentFeatureService.persist(Feature(featureId = idGenerator.nextFeatureId(), slug = slug, name = name))
    }

    override def findBySlug(partialSlug: String): Future[Seq[Feature]] = {
      if (partialSlug.isEmpty) {
        persistentFeatureService.findAll()
      } else {
        persistentFeatureService.findBySlug(partialSlug)
      }
    }

    override def updateFeature(featureId: FeatureId, slug: String, name: String): Future[Option[Feature]] = {
      persistentFeatureService.get(featureId).flatMap {
        case Some(feature) =>
          persistentFeatureService.update(feature.copy(slug = slug, name = name))
        case None => Future.successful(None)
      }
    }

    override def deleteFeature(featureId: FeatureId): Future[Unit] = {
      persistentFeatureService.remove(featureId)
    }

    override def findByFeatureIds(featureIds: Seq[FeatureId]): Future[Seq[Feature]] = {
      persistentFeatureService.findByFeatureIds(featureIds)
    }

    override def find(
      start: Int = 0,
      end: Option[Int] = None,
      sort: Option[String] = None,
      order: Option[String] = None,
      slug: Option[String]
    ): Future[Seq[Feature]] = {

      persistentFeatureService.find(start, end, sort, order, slug)

    }

    override def count(slug: Option[String]): Future[Int] = {
      persistentFeatureService.count(slug)
    }
  }
}
