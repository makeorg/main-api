package org.make.api.citizen

import java.time.ZoneOffset

import org.make.api.technical.ShortenedNames
import org.make.core.citizen.{Citizen, CitizenId}
import scalikejdbc._
import scalikejdbc.interpolation.SQLSyntax._

import scala.concurrent.{ExecutionContext, Future}
trait PersistentCitizenServiceComponent {

  def persistentCitizenService: PersistentCitizenService
  def readExecutionContext: ExecutionContext
  def writeExecutionContext: ExecutionContext

  case class PersistentCitizen(id: String,
                               email: String,
                               firstName: String,
                               lastName: String,
                               hashedPassword: String,
                               dateOfBirth: String)

  object PersistentCitizen extends SQLSyntaxSupport[PersistentCitizen] with ShortenedNames {

    override val columnNames = Seq("id", "email", "first_name", "last_name", "hashed_password", "date_of_birth")
    override val tableName: String = "citizen"

    lazy val c: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentCitizen], PersistentCitizen] = syntax("c")

    def toCitizen(rs: WrappedResultSet): Citizen = {
      Citizen(
        citizenId = CitizenId(rs.string(column.id)),
        email = rs.string(column.email),
        firstName = rs.string(column.firstName),
        lastName = rs.string(column.lastName),
        dateOfBirth = rs.date(column.dateOfBirth).toLocalDate
      )
    }
  }

  class PersistentCitizenService extends ShortenedNames {

    private val c = PersistentCitizen.c
    private val column = PersistentCitizen.column

    def get(id: CitizenId): Future[Option[Citizen]] = {
      Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select(c.*)
            .from(PersistentCitizen.as(c))
            .where(sqls.eq(c.id, id.value))
        }.map(PersistentCitizen.toCitizen).single.apply
      })(readExecutionContext)
    }

    def find(email: String, password: String): Future[Option[Citizen]] = {
      implicit val cxt: EC = readExecutionContext
      Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select(c.*)
            .from(PersistentCitizen.as(c))
            .where(sqls.eq(c.email, email).and(sqls.eq(c.hashedPassword, password)))
        }.map(PersistentCitizen.toCitizen).single().apply()
      })
    }

    def emailExists(email: String): Future[Boolean] = {
      implicit val ctx = readExecutionContext
      Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select(count(c.asterisk))
            .from(PersistentCitizen.as(c))
            .where(sqls.eq(c.email, email))
        }.map(rs => rs.int(1) > 0).single().apply()
      }).map(_.getOrElse(false))
    }

    def persist(citizen: Citizen, hashedPassword: String): Future[Citizen] = {
      implicit val ctx = writeExecutionContext
      Future(NamedDB('WRITE).localTx { implicit session =>
        withSQL {
          insert
            .into(PersistentCitizen)
            .namedValues(
              column.id -> citizen.citizenId.value,
              column.email -> citizen.email,
              column.dateOfBirth -> citizen.dateOfBirth.atStartOfDay(ZoneOffset.UTC),
              column.firstName -> citizen.firstName,
              column.lastName -> citizen.lastName,
              column.hashedPassword -> hashedPassword
            )
        }.execute().apply()
      }).map { _ =>
        citizen
      }
    }
  }

}
