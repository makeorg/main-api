package org.make.api.technical.auth

import java.time.ZonedDateTime

import org.make.api.Predef._
import org.make.api.technical.ShortenedNames
import org.make.core.user.UserId
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future}

trait TokenServiceComponent {

  def tokenService: TokenService

  def readExecutionContext: ExecutionContext

  def writeExecutionContext: ExecutionContext

  class TokenService extends ShortenedNames {

    private val t = PersistentToken.t
    private val column = PersistentToken.column

    def getToken(id: String): Future[Option[Token]] = {
      implicit val ctx = writeExecutionContext
      Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select(t.*)
            .from(PersistentToken.as(t))
            .where(sqls.eq(t.id, id))
        }.map(PersistentToken.toToken).single().apply()
      })
    }

    def getTokenByRefreshToken(refreshToken: String): Future[Option[Token]] = {
      implicit val ctx = writeExecutionContext
      Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select(t.*)
            .from(PersistentToken.as(t))
            .where(sqls.eq(t.refreshToken, refreshToken))
        }.map(PersistentToken.toToken).single().apply()
      })
    }

    def latestTokenForUser(userId: UserId): Future[Option[Token]] = {
      implicit val ctx = writeExecutionContext
      Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select(t.*)
            .from(PersistentToken.as(t))
            .where(sqls.eq(t.userId, userId.value))
            .orderBy(t.creationDate.desc)
            .limit(1)
        }.map(PersistentToken.toToken).single().apply()
      })
    }

    def insert(token: Token): Future[Token] = {
      implicit val ctx = writeExecutionContext
      Future(NamedDB('WRITE).localTx { implicit session =>
        withSQL {
          insertInto(PersistentToken)
            .namedValues(
              column.id -> token.id,
              column.refreshToken -> token.refreshToken,
              column.userId -> token.userId.value,
              column.scope -> token.scope,
              column.creationDate -> token.creationDate,
              column.validityDurationSeconds -> token.validityDurationSeconds,
              column.parameters -> token.parameters
            )
        }.execute().apply()
      }).map(_ => token)
    }
  }

  case class Token(id: String,
                   refreshToken: String,
                   userId: UserId,
                   scope: String,
                   creationDate: ZonedDateTime,
                   validityDurationSeconds: Int,
                   parameters: String)

  object PersistentToken extends SQLSyntaxSupport[Token] with ShortenedNames {
    override def tableName: String = "token"

    override def columnNames: Seq[String] =
      Seq("id", "refresh_token", "user_id", "scope", "creation_date", "validity_duration_seconds", "parameters")

    lazy val t: scalikejdbc.QuerySQLSyntaxProvider[scalikejdbc.SQLSyntaxSupport[Token], Token] = syntax("t")

    def toToken(rs: WrappedResultSet): Token = {
      Token(
        id = rs.string(column.id),
        userId = UserId(rs.string(column.userId)),
        refreshToken = rs.string(column.refreshToken),
        scope = rs.string(column.scope),
        creationDate = rs.jodaDateTime(column.creationDate).toJavaTime,
        validityDurationSeconds = rs.int(column.validityDurationSeconds),
        parameters = rs.string(column.parameters)
      )
    }
  }

}
