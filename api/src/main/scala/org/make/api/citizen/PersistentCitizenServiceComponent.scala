package org.make.api.citizen

import java.time.ZonedDateTime

import org.make.core.citizen.{Citizen, CitizenId}
import scalikejdbc._
import scalikejdbc.async.FutureImplicits._
import scalikejdbc.async._
import scalikejdbc.interpolation.SQLSyntax._

import scala.concurrent.Future


trait PersistentCitizenServiceComponent {

  def persistentCitizenService: PersistentCitizenService


  case class PersistentCitizen(
                                id: String,
                                email: String,
                                firstName: String,
                                lastName: String,
                                hashedPassword: String,
                                dateOfBirth: String)

  object PersistentCitizen extends SQLSyntaxSupport[PersistentCitizen] with ShortenedNames {

    override val columnNames = Seq("id", "email", "first_name", "last_name", "hashed_password", "date_of_birth")
    override val tableName: String = "citizen"

    lazy val c: scalikejdbc.QuerySQLSyntaxProvider[scalikejdbc.SQLSyntaxSupport[PersistentCitizen], PersistentCitizen] = syntax("c")

    def toCitizen(c: SyntaxProvider[PersistentCitizen])(rs: WrappedResultSet): Citizen = toCitizen(c.resultName)(rs)

    def toCitizen(c: ResultName[PersistentCitizen])(rs: WrappedResultSet): Citizen = {
      Citizen(
        citizenId = CitizenId(rs.string(column.id)),
        email = rs.string(column.email),
        firstName = rs.string(column.firstName),
        lastName = rs.string(column.lastName),
        dateOfBirth = ZonedDateTime.parse(rs.string(column.dateOfBirth)).toLocalDate
      )
    }
  }

  class PersistentCitizenService extends ShortenedNames {

    private val c = PersistentCitizen.c
    private val column = PersistentCitizen.column


    def get(id: CitizenId)(implicit cxt: EC = ECGlobal): Future[Option[Citizen]] = {
      implicit val session: AsyncDBSession = NamedAsyncDB('READ).sharedSession
      withSQL {
        select(c.*)
          .from(PersistentCitizen as c)
          .where(sqls.eq(c.id, id.value))
      }.single.map(PersistentCitizen.toCitizen(c))
    }

    def find(email: String, password: String)(implicit cxt: EC = ECGlobal): Future[Option[Citizen]] = {
      implicit val session: AsyncDBSession = NamedAsyncDB('READ).sharedSession
      withSQL {
        select(c.*)
          .from(PersistentCitizen as c)
          .where(sqls.eq(c.email, email) and sqls.eq(c.hashedPassword, password))
      }.map(PersistentCitizen.toCitizen(c))
    }

    def emailExists(email: String): Future[Boolean] = {
      implicit val session: AsyncDBSession = NamedAsyncDB('READ).sharedSession
      withSQL {
        select(count(c.asterisk))
          .from(PersistentCitizen as c)
          .where(sqls.eq(c.email, email))
      }.single().map(rs => rs.int(1) > 0).execute()
    }

    def persist(citizen: Citizen, hashedPassword: String)(implicit cxt: EC = ECGlobal): Future[Citizen] = {
      implicit val session: AsyncDBSession = NamedAsyncDB('WRITE).sharedSession
      withSQL {
        insert.into(PersistentCitizen).namedValues (
          column.id -> citizen.citizenId.value,
          column.email -> citizen.email,
          column.dateOfBirth -> citizen.dateOfBirth.toString,
          column.firstName -> citizen.firstName,
          column.lastName -> citizen.lastName,
          column.hashedPassword -> hashedPassword
        )
      }.execute().map {_ => citizen}
    }
  }


}