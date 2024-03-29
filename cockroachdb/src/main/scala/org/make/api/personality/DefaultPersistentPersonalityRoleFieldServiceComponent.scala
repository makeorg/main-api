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
import org.make.api.personality.DefaultPersistentPersonalityRoleFieldServiceComponent.PersistentPersonalityRoleField
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.PersistentServiceUtils.sortOrderQuery
import org.make.api.technical.Futures._
import org.make.api.technical.{PersistentCompanion, ShortenedNames}
import org.make.api.technical.ScalikeSupport._
import org.make.core.personality.{FieldType, PersonalityRoleField, PersonalityRoleFieldId, PersonalityRoleId}
import org.make.core.Order
import scalikejdbc._

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait DefaultPersistentPersonalityRoleFieldServiceComponent extends PersistentPersonalityRoleFieldServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentPersonalityRoleFieldService: DefaultPersistentPersonalityRoleFieldService =
    new DefaultPersistentPersonalityRoleFieldService

  class DefaultPersistentPersonalityRoleFieldService extends PersistentPersonalityRoleFieldService with ShortenedNames {

    private val personalityRoleFieldAlias = PersistentPersonalityRoleField.alias

    private val column = PersistentPersonalityRoleField.column

    override def persist(personalityRoleField: PersonalityRoleField): Future[PersonalityRoleField] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentPersonalityRoleField)
            .namedValues(
              column.id -> personalityRoleField.personalityRoleFieldId.value,
              column.personalityRoleId -> personalityRoleField.personalityRoleId.value,
              column.name -> personalityRoleField.name,
              column.fieldType -> personalityRoleField.fieldType,
              column.required -> personalityRoleField.required
            )
        }.execute().apply()
      }).map(_ => personalityRoleField)
    }

    override def modify(personalityRoleField: PersonalityRoleField): Future[PersonalityRoleField] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentPersonalityRoleField)
            .set(
              column.name -> personalityRoleField.name,
              column.fieldType -> personalityRoleField.fieldType,
              column.required -> personalityRoleField.required
            )
            .where(sqls.eq(column.id, personalityRoleField.personalityRoleFieldId.value))
        }.execute().apply()
      }).map(_ => personalityRoleField)
    }

    override def getById(
      personalityRoleFieldId: PersonalityRoleFieldId,
      personalityRoleId: PersonalityRoleId
    ): Future[Option[PersonalityRoleField]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentPersonalityRoleField.as(personalityRoleFieldAlias))
            .where(
              sqls
                .eq(column.id, personalityRoleFieldId.value)
                .and(sqls.eq(column.personalityRoleId, personalityRoleId.value))
            )
        }.map(PersistentPersonalityRoleField.apply()).single().apply()
      }).map(_.map(_.toPersonalityRoleField))
    }

    override def find(
      start: Start,
      end: Option[End],
      sort: Option[String],
      order: Option[Order],
      maybePersonalityRoleId: Option[PersonalityRoleId],
      maybeName: Option[String],
      maybeFieldType: Option[FieldType],
      maybeRequired: Option[Boolean]
    ): Future[Seq[PersonalityRoleField]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          val query: scalikejdbc.PagingSQLBuilder[PersistentPersonalityRoleField] = select
            .from(PersistentPersonalityRoleField.as(personalityRoleFieldAlias))
            .where(
              sqls.toAndConditionOpt(
                maybePersonalityRoleId.map(
                  personalityRoleId => sqls.eq(personalityRoleFieldAlias.personalityRoleId, personalityRoleId.value)
                ),
                maybeName.map(name           => sqls.eq(personalityRoleFieldAlias.name, name)),
                maybeFieldType.map(fieldType => sqls.eq(personalityRoleFieldAlias.fieldType, fieldType)),
                maybeRequired.map(required   => sqls.eq(personalityRoleFieldAlias.required, required))
              )
            )
          sortOrderQuery(start = start, end = end, sort = sort, order = order, query = query)
        }.map(PersistentPersonalityRoleField.apply()).list().apply()
      }).map(_.map(_.toPersonalityRoleField))
    }

    def count(
      maybePersonalityRoleId: Option[PersonalityRoleId],
      maybeName: Option[String],
      maybeFieldType: Option[FieldType],
      maybeRequired: Option[Boolean]
    ): Future[Int] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select(sqls.count)
            .from(PersistentPersonalityRoleField.as(personalityRoleFieldAlias))
            .where(
              sqls.toAndConditionOpt(
                maybePersonalityRoleId.map(
                  personalityRoleId => sqls.eq(personalityRoleFieldAlias.personalityRoleId, personalityRoleId.value)
                ),
                maybeName.map(name           => sqls.eq(personalityRoleFieldAlias.name, name)),
                maybeFieldType.map(fieldType => sqls.eq(personalityRoleFieldAlias.fieldType, fieldType)),
                maybeRequired.map(required   => sqls.eq(personalityRoleFieldAlias.required, required))
              )
            )
        }.map(_.int(1)).single().apply().getOrElse(0)
      })
    }

    override def delete(personalityRoleFieldId: PersonalityRoleFieldId): Future[Unit] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          deleteFrom(PersistentPersonalityRoleField)
            .where(sqls.eq(column.id, personalityRoleFieldId.value))
        }.execute().apply()
      }).toUnit
    }

  }
}

object DefaultPersistentPersonalityRoleFieldServiceComponent {

  final case class PersistentPersonalityRoleField(
    id: String,
    personalityRoleId: String,
    name: String,
    fieldType: String,
    required: Boolean
  ) {
    def toPersonalityRoleField: PersonalityRoleField = {
      PersonalityRoleField(
        personalityRoleFieldId = PersonalityRoleFieldId(id),
        personalityRoleId = PersonalityRoleId(personalityRoleId),
        name = name,
        fieldType = FieldType.withValue(fieldType),
        required = required
      )
    }
  }

  implicit object PersistentPersonalityRoleField
      extends PersistentCompanion[PersistentPersonalityRoleField, PersonalityRoleField] {
    override val columnNames: Seq[String] =
      Seq("id", "personality_role_id", "name", "field_type", "required")

    override val tableName: String = "personality_role_field"

    override lazy val alias: SyntaxProvider[PersistentPersonalityRoleField] = syntax("personality_role_field")

    override lazy val defaultSortColumns: NonEmptyList[SQLSyntax] = NonEmptyList.of(alias.name)

    def apply(
      personalityResultName: ResultName[PersistentPersonalityRoleField] = alias.resultName
    )(resultSet: WrappedResultSet): PersistentPersonalityRoleField = {
      PersistentPersonalityRoleField.apply(
        id = resultSet.string(personalityResultName.id),
        personalityRoleId = resultSet.string(personalityResultName.personalityRoleId),
        name = resultSet.string(personalityResultName.name),
        fieldType = resultSet.string(personalityResultName.fieldType),
        required = resultSet.boolean(personalityResultName.required)
      )
    }
  }

}
