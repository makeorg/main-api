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

package org.make.api.user

import java.time.ZonedDateTime
import grizzled.slf4j.Logging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.RichFutures._
import org.make.api.technical.ShortenedNames
import org.make.api.user.DefaultPersistentUserToAnonymizeServiceComponent.PersistentUserToAnonymize
import org.make.core.DateHelper
import org.postgresql.util.{PSQLException, PSQLState}
import scalikejdbc._

import scala.concurrent.Future
import scala.util.Success

trait PersistentUserToAnonymizeServiceComponent {
  def persistentUserToAnonymizeService: PersistentUserToAnonymizeService
}

trait PersistentUserToAnonymizeService {
  def create(email: String): Future[Unit]
  def findAll(): Future[Seq[String]]
  def removeAll(): Future[Int]
  def removeAllByEmails(emails: Seq[String]): Future[Int]
}

trait DefaultPersistentUserToAnonymizeServiceComponent extends PersistentUserToAnonymizeServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentUserToAnonymizeService: PersistentUserToAnonymizeService =
    new DefaultPersistentUserToAnonymizeService

  class DefaultPersistentUserToAnonymizeService
      extends PersistentUserToAnonymizeService
      with ShortenedNames
      with Logging {

    private val userToAnonymizeAlias = PersistentUserToAnonymize.userToAnonymizeAlias
    private val column = PersistentUserToAnonymize.column

    override def create(email: String): Future[Unit] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentUserToAnonymize)
            .namedValues(column.email -> email, column.requestDate -> DateHelper.now())
        }.execute().apply()
      }).toUnit.recoverWith {
        case e: PSQLException if e.getSQLState == PSQLState.UNIQUE_VIOLATION.getState =>
          Future.unit
        case other => Future.failed(other)
      }
    }

    override def findAll(): Future[Seq[String]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUserToAnonymize.as(userToAnonymizeAlias))
            .orderBy(userToAnonymizeAlias.requestDate)
            .asc
        }.map(PersistentUserToAnonymize.apply()).list().apply()
      }).map(_.map(_.email))
    }

    override def removeAll(): Future[Int] = {
      implicit val context: EC = writeExecutionContext
      val result: Future[Int] = Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          delete
            .from(PersistentUserToAnonymize.as(userToAnonymizeAlias))
        }.update().apply()
      })

      result.onComplete {
        case Success(rows) =>
          logger.info(s"Removed $rows from ${PersistentUserToAnonymize.tableName} successfully")
        case _ =>
      }

      result
    }

    override def removeAllByEmails(emails: Seq[String]): Future[Int] = {
      implicit val context: EC = writeExecutionContext
      val result: Future[Int] = Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          delete
            .from(PersistentUserToAnonymize.as(userToAnonymizeAlias))
            .where(sqls.in(userToAnonymizeAlias.email, emails))
        }.update().apply()
      })

      result.onComplete {
        case Success(rows) =>
          logger.info(s"Removed $rows from ${PersistentUserToAnonymize.tableName} successfully")
        case _ =>
      }

      result
    }
  }
}

object DefaultPersistentUserToAnonymizeServiceComponent {

  final case class PersistentUserToAnonymize(email: String, requestDate: ZonedDateTime)
  object PersistentUserToAnonymize
      extends SQLSyntaxSupport[PersistentUserToAnonymize]
      with ShortenedNames
      with Logging {

    override val columnNames: Seq[String] = Seq("email", "request_date")

    override val tableName: String = "user_to_anonymize"

    lazy val userToAnonymizeAlias
      : QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentUserToAnonymize], PersistentUserToAnonymize] = syntax("tt")

    def apply(
      userToAnonymizeResultName: ResultName[PersistentUserToAnonymize] = userToAnonymizeAlias.resultName
    )(resultSet: WrappedResultSet): PersistentUserToAnonymize = {
      PersistentUserToAnonymize.apply(
        email = resultSet.string(userToAnonymizeResultName.email),
        requestDate = resultSet.zonedDateTime(userToAnonymizeResultName.requestDate)
      )
    }
  }

}
