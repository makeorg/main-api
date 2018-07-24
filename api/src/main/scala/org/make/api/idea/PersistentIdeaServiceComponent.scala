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

package org.make.api.idea

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.idea.DefaultPersistentIdeaServiceComponent.PersistentIdea
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.core.DateHelper
import org.make.core.idea.{Idea, IdeaId}
import org.make.core.operation.OperationId
import org.make.core.reference.{Country, Language, ThemeId}
import scalikejdbc._

import scala.concurrent.Future

trait PersistentIdeaServiceComponent {
  def persistentIdeaService: PersistentIdeaService
}

trait PersistentIdeaService {
  def findOne(ideaId: IdeaId): Future[Option[Idea]]
  def findOneByName(name: String): Future[Option[Idea]]
  def findAll(ideaFilters: IdeaFiltersRequest): Future[Seq[Idea]]
  def findAllByIdeaIds(ids: Seq[IdeaId]): Future[Seq[Idea]]
  def persist(idea: Idea): Future[Idea]
  def modify(ideaId: IdeaId, name: String): Future[Int]
}

trait DefaultPersistentIdeaServiceComponent extends PersistentIdeaServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentIdeaService: PersistentIdeaService =
    new PersistentIdeaService with ShortenedNames with StrictLogging {

      private val ideaAlias = PersistentIdea.ideaAlias
      private val column = PersistentIdea.column

      override def findOne(ideaId: IdeaId): Future[Option[Idea]] = {
        implicit val context: EC = readExecutionContext
        val futurePersistentTag = Future(NamedDB('READ).retryableTx { implicit session =>
          withSQL {
            select
              .from(PersistentIdea.as(ideaAlias))
              .where(sqls.eq(ideaAlias.id, ideaId.value))
          }.map(PersistentIdea.apply()).single.apply
        })

        futurePersistentTag.map(_.map(_.toIdea))
      }

      override def findOneByName(name: String): Future[Option[Idea]] = {
        implicit val context: EC = readExecutionContext
        val futurePersistentTag = Future(NamedDB('READ).retryableTx { implicit session =>
          withSQL {
            select
              .from(PersistentIdea.as(ideaAlias))
              .where(sqls.eq(ideaAlias.name, name))
          }.map(PersistentIdea.apply()).single.apply
        })

        futurePersistentTag.map(_.map(_.toIdea))
      }

      override def findAll(ideaFilters: IdeaFiltersRequest): Future[Seq[Idea]] = {
        implicit val context: EC = readExecutionContext

        val futurePersistentIdeas = Future(NamedDB('READ).retryableTx { implicit session =>
          withSQL {
            select
              .from(PersistentIdea.as(ideaAlias))
              .where(
                sqls.toAndConditionOpt(
                  ideaFilters.language.map(language       => sqls.eq(ideaAlias.language, language.value)),
                  ideaFilters.country.map(country         => sqls.eq(ideaAlias.country, country.value)),
                  ideaFilters.operationId.map(operationId => sqls.eq(ideaAlias.operationId, operationId.value)),
                  ideaFilters.question.map(question       => sqls.eq(ideaAlias.question, question))
                )
              )
          }.map(PersistentIdea.apply()).list.apply
        })

        futurePersistentIdeas.map(_.map(_.toIdea))
      }

      def findAllByIdeaIds(ids: Seq[IdeaId]): Future[Seq[Idea]] = {
        implicit val cxt: EC = readExecutionContext
        val futurePersistentIdeas = Future(NamedDB('READ).retryableTx { implicit session =>
          withSQL {
            select
              .from(PersistentIdea.as(ideaAlias))
              .where(sqls.in(ideaAlias.id, ids.map(_.value)))
          }.map(PersistentIdea.apply()).list.apply
        })

        futurePersistentIdeas.map(_.map(_.toIdea))
      }

      override def persist(idea: Idea): Future[Idea] = {
        implicit val context: EC = writeExecutionContext
        Future(NamedDB('WRITE).retryableTx { implicit session =>
          withSQL {
            insert
              .into(PersistentIdea)
              .namedValues(
                column.id -> idea.ideaId.value,
                column.name -> idea.name,
                column.language -> idea.language.map(_.value),
                column.country -> idea.country.map(_.value),
                column.question -> idea.question,
                column.operationId -> idea.operationId.map(_.value),
                column.themeId -> idea.themeId.map(_.value),
                column.createdAt -> DateHelper.now,
                column.updatedAt -> DateHelper.now
              )
          }.execute().apply()
        }).map(_ => idea)
      }

      override def modify(ideaId: IdeaId, name: String): Future[Int] = {
        implicit val context: EC = writeExecutionContext
        Future(NamedDB('WRITE).retryableTx { implicit session =>
          withSQL {
            update(PersistentIdea)
              .set(column.name -> name, column.updatedAt -> DateHelper.now)
              .where(
                sqls
                  .eq(column.id, ideaId.value)
              )
          }.update().apply()
        })
      }
    }
}

object DefaultPersistentIdeaServiceComponent {

  case class PersistentIdea(id: String,
                            name: String,
                            language: Option[String],
                            country: Option[String],
                            question: Option[String],
                            operationId: Option[String],
                            themeId: Option[String],
                            createdAt: ZonedDateTime,
                            updatedAt: ZonedDateTime) {
    def toIdea: Idea =
      Idea(
        ideaId = IdeaId(id),
        name = name,
        language = language.map(Language(_)),
        country = country.map(Country(_)),
        question = question,
        operationId = operationId.map(operationId => OperationId(operationId)),
        themeId = themeId.map(themeId             => ThemeId(themeId)),
        createdAt = Some(createdAt),
        updatedAt = Some(updatedAt)
      )
  }

  object PersistentIdea extends SQLSyntaxSupport[PersistentIdea] with ShortenedNames with StrictLogging {

    override val columnNames: Seq[String] =
      Seq("id", "name", "language", "country", "operation_id", "theme_id", "question", "created_at", "updated_at")

    override val tableName: String = "idea"

    lazy val ideaAlias: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentIdea], PersistentIdea] = syntax("idea")

    def apply(
      ideaResultName: ResultName[PersistentIdea] = ideaAlias.resultName
    )(resultSet: WrappedResultSet): PersistentIdea = {
      PersistentIdea.apply(
        id = resultSet.string(ideaResultName.id),
        name = resultSet.string(ideaResultName.name),
        language = resultSet.stringOpt(ideaResultName.language),
        country = resultSet.stringOpt(ideaResultName.country),
        question = resultSet.stringOpt(ideaResultName.question),
        operationId = resultSet.stringOpt(ideaResultName.operationId),
        themeId = resultSet.stringOpt(ideaResultName.themeId),
        createdAt = resultSet.zonedDateTime(ideaResultName.createdAt),
        updatedAt = resultSet.zonedDateTime(ideaResultName.updatedAt)
      )
    }
  }
}
