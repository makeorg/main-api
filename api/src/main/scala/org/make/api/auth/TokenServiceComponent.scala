package org.make.api.auth

import java.time.ZonedDateTime

import org.make.core.citizen.CitizenId
import scalikejdbc._
import scalikejdbc.async._
import scalikejdbc.async.FutureImplicits._

import scala.concurrent.Future

trait TokenServiceComponent {

  def tokenService: TokenService

  class TokenService extends ShortenedNames {

    private val t = PersistentToken.t
    private val column = PersistentToken.column

    def getToken(id: String)(implicit cxt: EC = ECGlobal): Future[Option[Token]] = {
      implicit val session: AsyncDBSession = NamedAsyncDB('READ).sharedSession
      withSQL {
        select(t.*)
          .from(PersistentToken as t)
          .where(sqls.eq(t.id, id))
      }.single().map(PersistentToken.toToken(t))
    }

    def getTokenByRefreshToken(refreshToken: String): Future[Option[Token]] = {
      implicit val session: AsyncDBSession = NamedAsyncDB('READ).sharedSession
      withSQL {
        select(t.*)
          .from(PersistentToken as t)
          .where(sqls.eq(t.refreshToken, refreshToken))
      }.single().map(PersistentToken.toToken(t))
    }

    def latestTokenForUser(citizenId: CitizenId): Future[Option[Token]] = {
      implicit val session: AsyncDBSession = NamedAsyncDB('READ).sharedSession
      withSQL {
        select(t.*)
          .from(PersistentToken as t)
          .where(sqls.eq(t.citizenId, citizenId.value))
          .orderBy(t.creationDate.desc)
          .limit(1)
      }.single().map(PersistentToken.toToken(t))
    }

    def insert(token: Token)(implicit cxt: EC = ECGlobal): Future[Token] = {
      implicit val session: AsyncDBSession = NamedAsyncDB('WRITE).sharedSession
      withSQL {
        insertInto(PersistentToken)
          .namedValues(
            column.id -> token.id,
            column.refreshToken -> token.refreshToken,
            column.citizenId -> token.citizenId.value,
            column.scope -> token.scope,
            column.creationDate -> token.creationDate,
            column.validityDurationSeconds -> token.validityDurationSeconds,
            column.parameters -> token.parameters
          )
      }.execute().map(_ => token)
    }
  }


  case class Token(
                    id: String,
                    refreshToken: String,
                    citizenId: CitizenId,
                    scope: String,
                    creationDate: ZonedDateTime,
                    validityDurationSeconds: Int,
                    parameters: String)

  object PersistentToken extends SQLSyntaxSupport[Token] with ShortenedNames {
    override def tableName: String = "token"

    override def columnNames: Seq[String] = Seq(
      "id", "refresh_token", "citizen_id", "scope", "creation_date", "validity_duration_seconds", "parameters"
    )

    lazy val t: scalikejdbc.QuerySQLSyntaxProvider[scalikejdbc.SQLSyntaxSupport[Token], Token] = syntax("t")

    def toToken(c: SyntaxProvider[Token])(rs: WrappedResultSet): Token = toToken(t.resultName)(rs)

    def toToken(c: ResultName[Token])(rs: WrappedResultSet): Token = {
      Token(
        id = rs.string(column.id),
        citizenId = CitizenId(rs.string(column.citizenId)),
        refreshToken = rs.string(column.refreshToken),
        scope = rs.string(column.scope),
        creationDate = ZonedDateTime.parse(rs.string(column.creationDate)),
        validityDurationSeconds = rs.int(column.validityDurationSeconds),
        parameters = rs.string(column.parameters)
      )
    }
  }


}
