package org.make.api.user

import java.time.ZoneOffset

import org.make.api.technical.ShortenedNames
import org.make.core.user.{User, UserId}
import scalikejdbc._
import scalikejdbc.interpolation.SQLSyntax._

import scala.concurrent.{ExecutionContext, Future}
trait PersistentUserServiceComponent {

  def persistentUserService: PersistentUserService
  def readExecutionContext: ExecutionContext
  def writeExecutionContext: ExecutionContext

  case class PersistentUser(id: String,
                            email: String,
                            firstName: String,
                            lastName: String,
                            hashedPassword: String,
                            dateOfBirth: String)

  object PersistentUser extends SQLSyntaxSupport[PersistentUser] with ShortenedNames {

    override val columnNames = Seq("id", "email", "first_name", "last_name", "hashed_password", "date_of_birth")
    override val tableName: String = "user"

    lazy val c: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentUser], PersistentUser] = syntax("c")

    def toUser(rs: WrappedResultSet): User = {
      User(
        userId = UserId(rs.string(column.id)),
        email = rs.string(column.email),
        firstName = rs.string(column.firstName),
        lastName = rs.string(column.lastName),
        dateOfBirth = rs.date(column.dateOfBirth).toLocalDate
      )
    }
  }

  class PersistentUserService extends ShortenedNames {

    private val c = PersistentUser.c
    private val column = PersistentUser.column

    def get(id: UserId): Future[Option[User]] = {
      Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select(c.*)
            .from(PersistentUser.as(c))
            .where(sqls.eq(c.id, id.value))
        }.map(PersistentUser.toUser).single.apply
      })(readExecutionContext)
    }

    def find(email: String, password: String): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select(c.*)
            .from(PersistentUser.as(c))
            .where(sqls.eq(c.email, email).and(sqls.eq(c.hashedPassword, password)))
        }.map(PersistentUser.toUser).single().apply()
      })
    }

    def emailExists(email: String): Future[Boolean] = {
      implicit val ctx = readExecutionContext
      Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select(count(c.asterisk))
            .from(PersistentUser.as(c))
            .where(sqls.eq(c.email, email))
        }.map(rs => rs.int(1) > 0).single().apply()
      }).map(_.getOrElse(false))
    }

    def persist(user: User, hashedPassword: String): Future[User] = {
      implicit val ctx = writeExecutionContext
      Future(NamedDB('WRITE).localTx { implicit session =>
        withSQL {
          insert
            .into(PersistentUser)
            .namedValues(
              column.id -> user.userId.value,
              column.email -> user.email,
              column.dateOfBirth -> user.dateOfBirth.atStartOfDay(ZoneOffset.UTC),
              column.firstName -> user.firstName,
              column.lastName -> user.lastName,
              column.hashedPassword -> hashedPassword
            )
        }.execute().apply()
      }).map { _ =>
        user
      }
    }
  }

}
