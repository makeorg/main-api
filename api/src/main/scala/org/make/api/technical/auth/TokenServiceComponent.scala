/*

package org.make.api.technical.auth

//import org.make.api.Predef._
import org.make.api.technical.ShortenedNames
import org.make.core.user.{User, UserId}
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
              column.access_token -> token.accessToken,
              column.refresh_token -> token.refreshToken,
              column.scope -> token.scope,
              column.created_at -> token.createdAt,
              column.expires_in -> token.expiresIn,
              column.make_user_uuid -> token.user.userId,
              column.client_uuid -> token.client.clientId
            )
        }.execute().apply()
      }).map(_ => token)
    }
  }

  object PersistentToken extends SQLSyntaxSupport[Token] with ShortenedNames {
    override def tableName: String = "token"

    override def columnNames: Seq[String] =
      Seq("id", "refresh_token", "user_id", "scope", "creation_date", "validity_duration_seconds", "parameters")

    lazy val t: scalikejdbc.QuerySQLSyntaxProvider[scalikejdbc.SQLSyntaxSupport[Token], Token] = syntax("t")

    def apply(resultSet: WrappedResultSet): PersistentToken = {
      PersistentToken(
        user = resultSet.string(column.make_user_uuid),
        refreshToken = resultSet.string(column.refreshToken),
        scope = resultSet.string(column.scope),
        creationDate = resultSet.jodaDateTime(column.creationDate),
        validityDurationSeconds = resultSet.int(column.validityDurationSeconds),
        parameters = resultSet.string(column.parameters)
      )
    }
  }

}

*/