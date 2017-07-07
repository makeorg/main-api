package org.make.api.technical.auth

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.ShortenedNames
import org.make.api.user.PersistentUserServiceComponent
import org.make.core.auth.Token
import org.make.core.user.User
import scalikejdbc._

import scala.concurrent.Future

trait PersistentTokenServiceComponent
    extends MakeDBExecutionContextComponent
    with PersistentUserServiceComponent
    with PersistentClientServiceComponent {

  def persistentTokenService: PersistentTokenService

  case class PersistentToken(accessToken: String,
                             refreshToken: Option[String],
                             scope: Option[String],
                             createdAt: Option[ZonedDateTime],
                             updatedAt: Option[ZonedDateTime],
                             expiresIn: Int,
                             makeUserUuid: PersistentUser,
                             clientUuid: PersistentClient)
      extends StrictLogging {
    def toToken: Token = {
      Token(
        accessToken = accessToken,
        refreshToken = refreshToken,
        scope = scope,
        createdAt = createdAt,
        updatedAt = updatedAt,
        expiresIn = expiresIn,
        user = makeUserUuid.toUser,
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
      userResultName: ResultName[PersistentUser] = PersistentUser.userAlias.resultName,
      clientResultName: ResultName[PersistentClient] = PersistentClient.clientAlias.resultName
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

  class PersistentTokenService extends ShortenedNames with StrictLogging {

    private val tokenAlias = PersistentToken.tokenAlias
    private val column = PersistentToken.column

    def get(token: Token): Future[Option[Token]] = {
      get(token.accessToken)
    }

    def accessTokenExists(token: String): Future[Boolean] = {
      get(token).map(_.isDefined)(ECGlobal)
    }

    def refreshTokenExists(token: String): Future[Boolean] = {
      findByRefreshToken(token).map(_.isDefined)(ECGlobal)
    }

    def findByRefreshToken(token: String): Future[Option[Token]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentToken: Future[Option[PersistentToken]] = Future(NamedDB('READ).localTx { implicit session =>
        val userAlias = PersistentUser.userAlias
        val clientAlias = PersistentClient.clientAlias
        withSQL {
          val req: scalikejdbc.SQLBuilder[PersistentUser] = select
            .from(PersistentToken.as(tokenAlias))
            .innerJoin(PersistentUser.as(userAlias))
            .on(userAlias.uuid, tokenAlias.makeUserUuid)
            .innerJoin(PersistentClient.as(clientAlias))
            .on(clientAlias.uuid, tokenAlias.clientUuid)
            .where(sqls.eq(tokenAlias.refreshToken, token))
          req
        }.map(PersistentToken.apply(tokenAlias.resultName, userAlias.resultName, clientAlias.resultName)).single.apply
      })

      futurePersistentToken.map(_.map(_.toToken))
    }

    def findByAccessToken(token: String): Future[Option[Token]] = {
      get(token)
    }

    def get(accessToken: String): Future[Option[Token]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentToken: Future[Option[PersistentToken]] = Future(NamedDB('READ).localTx { implicit session =>
        val userAlias = PersistentUser.userAlias
        val clientAlias = PersistentClient.clientAlias
        withSQL {
          val req: scalikejdbc.SQLBuilder[PersistentUser] = select
            .from(PersistentToken.as(tokenAlias))
            .innerJoin(PersistentUser.as(userAlias))
            .on(userAlias.uuid, tokenAlias.makeUserUuid)
            .innerJoin(PersistentClient.as(clientAlias))
            .on(clientAlias.uuid, tokenAlias.clientUuid)
            .where(sqls.eq(tokenAlias.accessToken, accessToken))
          req
        }.map(PersistentToken.apply(tokenAlias.resultName, userAlias.resultName, clientAlias.resultName)).single.apply
      })

      futurePersistentToken.map(_.map(_.toToken))
    }

    def findByUser(user: User): Future[Option[Token]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentToken = Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select
            .from(PersistentToken.as(tokenAlias))
            .innerJoin(PersistentUser.as(PersistentUser.userAlias))
            .on(PersistentUser.userAlias.uuid, tokenAlias.makeUserUuid)
            .innerJoin(PersistentClient.as(PersistentClient.clientAlias))
            .on(PersistentClient.clientAlias.uuid, tokenAlias.clientUuid)
            .where(sqls.eq(tokenAlias.makeUserUuid, user.userId.value))
        }.map(PersistentToken.apply()).single.apply
      })

      futurePersistentToken.map(_.map(_.toToken))
    }

    def persist(token: Token): Future[Token] = {
      implicit val ctx = writeExecutionContext
      Future(NamedDB('WRITE).localTx { implicit session =>
        withSQL {
          insert
            .into(PersistentToken)
            .namedValues(
              column.accessToken -> token.accessToken,
              column.refreshToken -> token.refreshToken,
              column.scope -> token.scope,
              column.createdAt -> ZonedDateTime.now,
              column.updatedAt -> ZonedDateTime.now,
              column.expiresIn -> token.expiresIn,
              column.makeUserUuid -> token.user.userId.value,
              column.clientUuid -> token.client.clientId.value
            )
        }.execute().apply()
      }).map(_ => token)
    }

    def deleteByRefreshToken(refreshToken: String): Future[Int] = {
      implicit val ctx = writeExecutionContext
      Future(NamedDB('WRITE).localTx { implicit session =>
        withSQL {
          delete
            .from(PersistentToken)
            .where
            .eq(tokenAlias.refreshToken, refreshToken)
        }.update().apply()
      })
    }
  }
}
