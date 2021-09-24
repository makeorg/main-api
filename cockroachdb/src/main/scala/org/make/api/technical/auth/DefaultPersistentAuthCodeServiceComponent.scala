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

package org.make.api.technical.auth
import java.time.ZonedDateTime
import grizzled.slf4j.Logging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.ShortenedNames
import org.make.core.auth.{AuthCode, ClientId}
import org.make.core.user.UserId
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.Futures._
import scalikejdbc._

import scala.concurrent.Future

trait DefaultPersistentAuthCodeServiceComponent extends PersistentAuthCodeServiceComponent with ShortenedNames {
  self: MakeDBExecutionContextComponent =>

  override lazy val persistentAuthCodeService: PersistentAuthCodeService = new DefaultPersistentAuthCodeService

  private class DefaultPersistentAuthCodeService extends PersistentAuthCodeService {

    val alias
      : scalikejdbc.QuerySQLSyntaxProvider[scalikejdbc.SQLSyntaxSupport[PersistentAuthCode], PersistentAuthCode] =
      PersistentAuthCode.authCodeAlias

    val columns: scalikejdbc.ColumnName[PersistentAuthCode] = PersistentAuthCode.column

    override def persist(authCode: AuthCode): Future[AuthCode] = {
      implicit val cxt: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentAuthCode)
            .namedValues(
              columns.authorizationCode -> authCode.authorizationCode,
              columns.scope -> authCode.scope,
              columns.redirectUri -> authCode.redirectUri,
              columns.createdAt -> authCode.createdAt,
              columns.expiresIn -> authCode.expiresIn,
              columns.makeUserUuid -> authCode.user.value,
              columns.clientUuid -> authCode.client.value
            )
        }.execute().apply()
      }).map(_ => authCode)
    }

    override def findByCode(code: String): Future[Option[AuthCode]] = {
      implicit val cxt: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentAuthCode.as(alias))
            .where(sqls.eq(alias.authorizationCode, code))
        }.map(PersistentAuthCode.apply()).single().apply()
      }).map(_.map(_.toAuthCode))
    }

    override def deleteByCode(code: String): Future[Unit] = {
      implicit val cxt: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          delete
            .from(PersistentAuthCode.as(alias))
            .where(sqls.eq(alias.authorizationCode, code))
        }.map(PersistentAuthCode.apply()).update().apply()
      }).toUnit
    }
  }
}

final case class PersistentAuthCode(
  authorizationCode: String,
  scope: Option[String],
  redirectUri: Option[String],
  createdAt: ZonedDateTime,
  expiresIn: Int,
  makeUserUuid: String,
  clientUuid: String
) {
  def toAuthCode: AuthCode =
    AuthCode(authorizationCode, scope, redirectUri, createdAt, expiresIn, UserId(makeUserUuid), ClientId(clientUuid))
}

object PersistentAuthCode extends SQLSyntaxSupport[PersistentAuthCode] with ShortenedNames with Logging {
  override val columnNames: Seq[String] =
    Seq("authorization_code", "scope", "redirect_uri", "created_at", "expires_in", "make_user_uuid", "client_uuid")

  override val tableName: String = "auth_code"

  lazy val authCodeAlias: SyntaxProvider[PersistentAuthCode] = syntax("auth_code")

  def apply(
    authCodeResultName: ResultName[PersistentAuthCode] = authCodeAlias.resultName
  )(resultSet: WrappedResultSet): PersistentAuthCode = {
    PersistentAuthCode(
      authorizationCode = resultSet.string(authCodeResultName.authorizationCode),
      scope = resultSet.stringOpt(authCodeResultName.scope),
      redirectUri = resultSet.stringOpt(authCodeResultName.redirectUri),
      createdAt = resultSet.zonedDateTime(authCodeResultName.createdAt),
      expiresIn = resultSet.int(authCodeResultName.expiresIn),
      makeUserUuid = resultSet.string(authCodeResultName.makeUserUuid),
      clientUuid = resultSet.string(authCodeResultName.clientUuid)
    )
  }
}
