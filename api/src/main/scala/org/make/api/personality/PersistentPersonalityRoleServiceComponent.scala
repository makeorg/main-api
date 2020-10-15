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

package org.make.api.personality

import cats.data.NonEmptyList
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.personality.DefaultPersistentPersonalityRoleServiceComponent.PersistentPersonalityRole
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.PersistentServiceUtils.sortOrderQuery
import org.make.api.technical.{PersistentCompanion, ShortenedNames}
import org.make.core.personality.{PersonalityRole, PersonalityRoleId}
import org.make.core.Order
import scalikejdbc._

import scala.concurrent.Future

trait PersistentPersonalityRoleServiceComponent {
  def persistentPersonalityRoleService: PersistentPersonalityRoleService
}

trait PersistentPersonalityRoleService {
  def persist(personalityRole: PersonalityRole): Future[PersonalityRole]
  def modify(personalityRole: PersonalityRole): Future[PersonalityRole]
  def getById(personalityRoleId: PersonalityRoleId): Future[Option[PersonalityRole]]
  def find(
    start: Int,
    end: Option[Int],
    sort: Option[String],
    order: Option[Order],
    maybeRoleIds: Option[Seq[PersonalityRoleId]],
    maybeName: Option[String]
  ): Future[Seq[PersonalityRole]]
  def count(maybeRoleIds: Option[Seq[PersonalityRoleId]], maybeName: Option[String]): Future[Int]
  def delete(personalityRoleId: PersonalityRoleId): Future[Unit]
}

trait DefaultPersistentPersonalityRoleServiceComponent extends PersistentPersonalityRoleServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentPersonalityRoleService: DefaultPersistentPersonalityRoleService =
    new DefaultPersistentPersonalityRoleService

  class DefaultPersistentPersonalityRoleService extends PersistentPersonalityRoleService with ShortenedNames {

    private val personalityRoleAlias = PersistentPersonalityRole.alias

    private val column = PersistentPersonalityRole.column

    override def persist(personalityRole: PersonalityRole): Future[PersonalityRole] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentPersonalityRole)
            .namedValues(column.id -> personalityRole.personalityRoleId.value, column.name -> personalityRole.name)
        }.execute().apply()
      }).map(_ => personalityRole)
    }

    override def modify(personalityRole: PersonalityRole): Future[PersonalityRole] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentPersonalityRole)
            .set(column.name -> personalityRole.name)
            .where(sqls.eq(column.id, personalityRole.personalityRoleId.value))
        }.execute().apply()
      }).map(_ => personalityRole)
    }

    override def getById(personalityRoleId: PersonalityRoleId): Future[Option[PersonalityRole]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentPersonalityRole.as(personalityRoleAlias))
            .where(sqls.eq(personalityRoleAlias.id, personalityRoleId.value))
        }.map(PersistentPersonalityRole.apply()).single().apply()
      }).map(_.map(_.toPersonalityRole))
    }

    override def find(
      start: Int,
      end: Option[Int],
      sort: Option[String],
      order: Option[Order],
      maybeRoleIds: Option[Seq[PersonalityRoleId]],
      maybeName: Option[String]
    ): Future[Seq[PersonalityRole]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          val query: scalikejdbc.PagingSQLBuilder[PersistentPersonalityRole] = select
            .from(PersistentPersonalityRole.as(personalityRoleAlias))
            .where(
              sqls.toAndConditionOpt(
                maybeName.map(name       => sqls.eq(personalityRoleAlias.name, name)),
                maybeRoleIds.map(roleIds => sqls.in(personalityRoleAlias.id, roleIds.map(_.value)))
              )
            )
          sortOrderQuery(start, end, sort, order, query)
        }.map(PersistentPersonalityRole.apply()).list().apply()
      }).map(_.map(_.toPersonalityRole))
    }

    override def count(maybeRoleIds: Option[Seq[PersonalityRoleId]], maybeName: Option[String]): Future[Int] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select(sqls.count)
            .from(PersistentPersonalityRole.as(personalityRoleAlias))
            .where(
              sqls.toAndConditionOpt(
                maybeName.map(name       => sqls.eq(personalityRoleAlias.name, name)),
                maybeRoleIds.map(roleIds => sqls.in(personalityRoleAlias.id, roleIds.map(_.value)))
              )
            )
        }.map(_.int(1)).single().apply().getOrElse(0)
      })
    }

    override def delete(personalityRoleId: PersonalityRoleId): Future[Unit] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          deleteFrom(PersistentPersonalityRole)
            .where(sqls.eq(column.id, personalityRoleId.value))
        }.execute().apply()
      }).map(_ => ())
    }

  }
}

object DefaultPersistentPersonalityRoleServiceComponent {

  final case class PersistentPersonalityRole(id: String, name: String) {
    def toPersonalityRole: PersonalityRole = {
      PersonalityRole(personalityRoleId = PersonalityRoleId(id), name = name)
    }
  }

  implicit object PersistentPersonalityRole extends PersistentCompanion[PersistentPersonalityRole, PersonalityRole] {
    override val columnNames: Seq[String] =
      Seq("id", "name")

    override val tableName: String = "personality_role"

    override lazy val alias: SyntaxProvider[PersistentPersonalityRole] = syntax("personality_role")

    override lazy val defaultSortColumns: NonEmptyList[SQLSyntax] = NonEmptyList.of(alias.name)

    def apply(
      personalityResultName: ResultName[PersistentPersonalityRole] = alias.resultName
    )(resultSet: WrappedResultSet): PersistentPersonalityRole = {
      PersistentPersonalityRole.apply(
        id = resultSet.string(personalityResultName.id),
        name = resultSet.string(personalityResultName.name)
      )
    }
  }

}
