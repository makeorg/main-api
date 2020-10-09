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

import cats.data.NonEmptyList
import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.feature.DefaultPersistentFeatureServiceComponent.PersistentFeature
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.PersistentServiceUtils.sortOrderQuery
import org.make.api.technical.{PersistentCompanion, ShortenedNames}
import org.make.core.feature.{Feature, FeatureId}
import org.make.core.Order
import scalikejdbc._

import scala.concurrent.Future

trait PersistentFeatureServiceComponent {
  def persistentFeatureService: PersistentFeatureService
}

trait PersistentFeatureService {
  def get(featureId: FeatureId): Future[Option[Feature]]
  def findBySlug(slug: String): Future[Seq[Feature]]
  def persist(feature: Feature): Future[Feature]
  def update(feature: Feature): Future[Option[Feature]]
  def remove(featureId: FeatureId): Future[Unit]
  def findAll(): Future[Seq[Feature]]
  def findByFeatureIds(featureIds: Seq[FeatureId]): Future[Seq[Feature]]
  def find(
    start: Int,
    end: Option[Int],
    sort: Option[String],
    order: Option[Order],
    slug: Option[String]
  ): Future[Seq[Feature]]
  def count(slug: Option[String]): Future[Int]
}

trait DefaultPersistentFeatureServiceComponent extends PersistentFeatureServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentFeatureService: PersistentFeatureService = new DefaultPersistentFeatureService

  class DefaultPersistentFeatureService extends PersistentFeatureService with ShortenedNames with StrictLogging {

    private val featureAlias = PersistentFeature.alias

    private val column = PersistentFeature.column

    override def get(featureId: FeatureId): Future[Option[Feature]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentFeature = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentFeature.as(featureAlias))
            .where(sqls.eq(featureAlias.id, featureId.value))
        }.map(PersistentFeature.apply()).single().apply()
      })

      futurePersistentFeature.map(_.map(_.toFeature))
    }

    def findBySlug(slug: String): Future[Seq[Feature]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentFeatures: Future[List[PersistentFeature]] = Future(NamedDB("READ").retryableTx {
        implicit session =>
          withSQL {
            select
              .from(PersistentFeature.as(featureAlias))
              .where(sqls.eq(featureAlias.slug, slug))
          }.map(PersistentFeature.apply()).list().apply()
      })

      futurePersistentFeatures.map(_.map(_.toFeature))
    }

    override def persist(feature: Feature): Future[Feature] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentFeature)
            .namedValues(column.id -> feature.featureId.value, column.slug -> feature.slug, column.name -> feature.name)
        }.execute().apply()
      }).map(_ => feature)
    }

    override def update(feature: Feature): Future[Option[Feature]] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          scalikejdbc
            .update(PersistentFeature)
            .set(column.slug -> feature.slug, column.name -> feature.name)
            .where(sqls.eq(column.id, feature.featureId.value))
        }.executeUpdate().apply()
      }).map {
        case 1 => Some(feature)
        case 0 => None
      }
    }

    override def remove(featureId: FeatureId): Future[Unit] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          delete
            .from(PersistentFeature.as(featureAlias))
            .where(sqls.eq(featureAlias.id, featureId.value))
        }.update().apply()
      }).map(_ => ())
    }

    override def findAll(): Future[Seq[Feature]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentFeatures: Future[List[PersistentFeature]] = Future(NamedDB("READ").retryableTx {
        implicit session =>
          withSQL {
            select
              .from(PersistentFeature.as(featureAlias))
              .orderBy(featureAlias.slug)
          }.map(PersistentFeature.apply()).list().apply()
      })

      futurePersistentFeatures.map(_.map(_.toFeature))
    }

    override def findByFeatureIds(featureIds: Seq[FeatureId]): Future[Seq[Feature]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentFeatures: Future[List[PersistentFeature]] = Future(NamedDB("READ").retryableTx {
        implicit session =>
          withSQL {
            select
              .from(PersistentFeature.as(featureAlias))
              .where(sqls.in(featureAlias.id, featureIds.map(_.value)))
              .orderBy(featureAlias.slug)
          }.map(PersistentFeature.apply()).list().apply()
      })

      futurePersistentFeatures.map(_.map(_.toFeature))
    }

    override def find(
      start: Int,
      end: Option[Int],
      sort: Option[String],
      order: Option[Order],
      maybeSlug: Option[String]
    ): Future[Seq[Feature]] = {
      implicit val context: EC = readExecutionContext

      val futurePersistentFeatures: Future[List[PersistentFeature]] = Future(NamedDB("READ").retryableTx {
        implicit session =>
          withSQL {

            val query: scalikejdbc.PagingSQLBuilder[PersistentFeature] =
              select
                .from(PersistentFeature.as(featureAlias))
                .where(sqls.toAndConditionOpt(maybeSlug.map(slug => sqls.eq(featureAlias.slug, slug))))

            sortOrderQuery(start, end, sort, order, query)
          }.map(PersistentFeature.apply()).list().apply()
      })

      futurePersistentFeatures.map(_.map(_.toFeature))
    }

    override def count(maybeSlug: Option[String]): Future[Int] = {
      implicit val context: EC = readExecutionContext

      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {

          select(sqls.count)
            .from(PersistentFeature.as(featureAlias))
            .where(sqls.toAndConditionOpt(maybeSlug.map(slug => sqls.eq(featureAlias.slug, slug))))
        }.map(_.int(1)).single().apply().getOrElse(0)
      })
    }
  }
}

object DefaultPersistentFeatureServiceComponent {

  final case class PersistentFeature(id: String, slug: String, name: String) {
    def toFeature: Feature =
      Feature(featureId = FeatureId(id), slug = slug, name = name)
  }

  implicit object PersistentFeature
      extends PersistentCompanion[PersistentFeature, Feature]
      with ShortenedNames
      with StrictLogging {

    override val columnNames: Seq[String] = Seq("id", "slug", "name")

    override val tableName: String = "feature"

    override lazy val alias: SyntaxProvider[PersistentFeature] = syntax("feature")

    override lazy val defaultSortColumns: NonEmptyList[SQLSyntax] = NonEmptyList.of(alias.slug)

    def apply(
      featureResultName: ResultName[PersistentFeature] = alias.resultName
    )(resultSet: WrappedResultSet): PersistentFeature = {
      PersistentFeature.apply(
        id = resultSet.string(featureResultName.id),
        slug = resultSet.string(featureResultName.slug),
        name = resultSet.string(featureResultName.name)
      )
    }
  }

}
