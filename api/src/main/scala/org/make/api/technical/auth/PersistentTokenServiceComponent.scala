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

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.api.technical.auth.PersistentClientServiceComponent.PersistentClient
import org.make.api.technical.auth.PersistentTokenServiceComponent.PersistentToken
import org.make.api.user.PersistentUserServiceComponent
import org.make.api.user.PersistentUserServiceComponent.PersistentUser
import org.make.core.DateHelper
import org.make.core.auth.Token
import org.make.core.user.UserId
import scalikejdbc._

import scala.concurrent.Future

trait PersistentTokenServiceComponent {
  def persistentTokenService: PersistentTokenService
}

object PersistentTokenServiceComponent {

  final case class PersistentToken(
    accessToken: String,
    refreshToken: Option[String],
    scope: Option[String],
    createdAt: Option[ZonedDateTime],
    updatedAt: Option[ZonedDateTime],
    expiresIn: Int,
    makeUserUuid: PersistentUser,
    clientUuid: PersistentClient
  ) {
    def toToken: Token = {
      Token(
        accessToken = accessToken,
        refreshToken = refreshToken,
        scope = scope,
        createdAt = createdAt,
        updatedAt = updatedAt,
        expiresIn = expiresIn,
        user = makeUserUuid.toUserRights,
        client = clientUuid.toClient
      )
    }
  }

  object PersistentToken extends SQLSyntaxSupport[PersistentToken] with ShortenedNames with StrictLogging {

    override val columnNames: Seq[String] =
      Seq(
        "access_token",
        "refresh_token",
        "scope",
        "created_at",
        "updated_at",
        "expires_in",
        "make_user_uuid",
        "client_uuid"
      )

    override val tableName: String = "access_token"

    lazy val tokenAlias: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentToken], PersistentToken] = syntax("t")

    def apply(
      tokenResultName: ResultName[PersistentToken] = tokenAlias.resultName,
      userResultName: ResultName[PersistentUser] = PersistentUser.alias.resultName,
      clientResultName: ResultName[PersistentClient] = PersistentClient.alias.resultName
    )(resultSet: WrappedResultSet): PersistentToken = {
      val persistentUser = PersistentUser(userResultName)(resultSet)
      val persistentClient = PersistentClient(clientResultName)(resultSet)
      PersistentToken(
        accessToken = resultSet.string(tokenResultName.accessToken),
        refreshToken = resultSet.stringOpt(tokenResultName.refreshToken),
        scope = resultSet.stringOpt(tokenResultName.scope),
        createdAt = resultSet.zonedDateTimeOpt(tokenResultName.createdAt),
        updatedAt = resultSet.zonedDateTimeOpt(tokenResultName.updatedAt),
        expiresIn = resultSet.int(tokenResultName.expiresIn),
        makeUserUuid = persistentUser,
        clientUuid = persistentClient
      )
    }
  }

}

trait PersistentTokenService {
  def accessTokenExists(token: String): Future[Boolean]
  def refreshTokenExists(token: String): Future[Boolean]
  def findByRefreshToken(token: String): Future[Option[Token]]
  def get(accessToken: String): Future[Option[Token]]
  def findByUserId(userId: UserId): Future[Option[Token]]
  def persist(token: Token): Future[Token]
  def deleteByAccessToken(accessToken: String): Future[Int]
  def deleteByUserId(userId: UserId): Future[Int]
}

trait DefaultPersistentTokenServiceComponent extends PersistentTokenServiceComponent {
  self: MakeDBExecutionContextComponent with PersistentUserServiceComponent with PersistentClientServiceComponent =>

  override lazy val persistentTokenService: PersistentTokenService = new DefaultPersistentTokenService

  class DefaultPersistentTokenService extends PersistentTokenService with ShortenedNames with StrictLogging {

    private val tokenAlias = PersistentToken.tokenAlias
    private val column = PersistentToken.column

    override def accessTokenExists(token: String): Future[Boolean] = {
      get(token).map(_.isDefined)(ECGlobal)
    }

    override def refreshTokenExists(token: String): Future[Boolean] = {
      findByRefreshToken(token).map(_.isDefined)(ECGlobal)
    }

    override def findByRefreshToken(token: String): Future[Option[Token]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentToken: Future[Option[PersistentToken]] = Future(NamedDB("READ").retryableTx {
        implicit session =>
          val userAlias = PersistentUser.alias
          val clientAlias = PersistentClient.alias
          withSQL {
            val req: scalikejdbc.SQLBuilder[PersistentUser] = select
              .from(PersistentToken.as(tokenAlias))
              .innerJoin(PersistentUser.as(userAlias))
              .on(userAlias.uuid, tokenAlias.makeUserUuid)
              .innerJoin(PersistentClient.as(clientAlias))
              .on(clientAlias.uuid, tokenAlias.clientUuid)
              .where(sqls.eq(tokenAlias.refreshToken, token))
            req
          }.map(
              PersistentToken(
                tokenResultName = tokenAlias.resultName,
                userResultName = userAlias.resultName,
                clientResultName = clientAlias.resultName
              )
            )
            .single
            .apply
      })

      futurePersistentToken.map(_.map(_.toToken))
    }

    override def get(accessToken: String): Future[Option[Token]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentToken: Future[Option[PersistentToken]] = Future(NamedDB("READ").retryableTx {
        implicit session =>
          val userAlias = PersistentUser.alias
          val clientAlias = PersistentClient.alias
          withSQL {
            select
              .from(PersistentToken.as(tokenAlias))
              .innerJoin(PersistentUser.as(userAlias))
              .on(userAlias.uuid, tokenAlias.makeUserUuid)
              .innerJoin(PersistentClient.as(clientAlias))
              .on(clientAlias.uuid, tokenAlias.clientUuid)
              .where(sqls.eq(tokenAlias.accessToken, accessToken))
          }.map(PersistentToken.apply(tokenAlias.resultName, userAlias.resultName, clientAlias.resultName)).single.apply
      })

      futurePersistentToken.map(_.map(_.toToken))
    }

    override def findByUserId(userId: UserId): Future[Option[Token]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentToken = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentToken.as(tokenAlias))
            .innerJoin(PersistentUser.as(PersistentUser.alias))
            .on(PersistentUser.alias.uuid, tokenAlias.makeUserUuid)
            .innerJoin(PersistentClient.as(PersistentClient.alias))
            .on(PersistentClient.alias.uuid, tokenAlias.clientUuid)
            .where(sqls.eq(tokenAlias.makeUserUuid, userId.value))
            .orderBy(tokenAlias.updatedAt)
            .desc
            .limit(1)
        }.map(PersistentToken.apply()).single.apply
      })

      futurePersistentToken.map(_.map(_.toToken))
    }

    override def persist(token: Token): Future[Token] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentToken)
            .namedValues(
              column.accessToken -> token.accessToken,
              column.refreshToken -> token.refreshToken,
              column.scope -> token.scope,
              column.createdAt -> DateHelper.now(),
              column.updatedAt -> DateHelper.now(),
              column.expiresIn -> token.expiresIn,
              column.makeUserUuid -> token.user.userId.value,
              column.clientUuid -> token.client.clientId.value
            )
        }.execute().apply()
      }).map(_ => token)
    }

    override def deleteByAccessToken(accessToken: String): Future[Int] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          delete
            .from(PersistentToken.as(tokenAlias))
            .where
            .eq(tokenAlias.accessToken, accessToken)
        }.update().apply()
      })
    }

    override def deleteByUserId(userId: UserId): Future[Int] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          delete
            .from(PersistentToken.as(tokenAlias))
            .where
            .eq(tokenAlias.makeUserUuid, userId.value)
        }.update().apply()
      })
    }

  }
}
