package org.make.api.citizen

import java.time.ZonedDateTime

import org.make.core.citizen.{Citizen, CitizenId}
import scalikejdbc._
import scalikejdbc.async.FutureImplicits._
import scalikejdbc.async._

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
        citizenId = CitizenId(rs.string(c.id)),
        email = rs.string(c.email),
        firstName = rs.string(c.firstName),
        lastName = rs.string(c.lastName),
        dateOfBirth = ZonedDateTime.parse(rs.string(c.dateOfBirth)).toLocalDate
      )
    }
  }

  class PersistentCitizenService extends ShortenedNames {

    def get(id: CitizenId)(implicit cxt: EC = ECGlobal): Future[Option[Citizen]] = {
      implicit val session: AsyncDBSession = NamedAsyncDB('READ).sharedSession
      withSQL {
        select.from(PersistentCitizen as PersistentCitizen.c)
          .where
          .append(sqls.eq(PersistentCitizen.column.id, id.value))
      }.single.map(PersistentCitizen.toCitizen(PersistentCitizen.c))
    }

    def find(email: String, password: String)(implicit cxt: EC = ECGlobal): Future[Option[Citizen]] = {
      implicit val session: AsyncDBSession = NamedAsyncDB('READ).sharedSession
      withSQL {
        select.from(PersistentCitizen as PersistentCitizen.c)
          .where
          .append(sqls.eq(PersistentCitizen.column.email, email))
          .append(sqls.eq(PersistentCitizen.column.hashedPassword, password))
      }.map(PersistentCitizen.toCitizen(PersistentCitizen.c))
    }

    def persist(citizen: Citizen, hashedPassword: String)(implicit cxt: EC = ECGlobal): Future[Citizen] = {
      implicit val session: AsyncDBSession = NamedAsyncDB('WRITE).sharedSession
      withSQL {
        insert.into(PersistentCitizen).namedValues (
          PersistentCitizen.column.id -> citizen.citizenId.value,
          PersistentCitizen.column.email -> citizen.email,
          PersistentCitizen.column.dateOfBirth -> citizen.dateOfBirth.toString,
          PersistentCitizen.column.firstName -> citizen.firstName,
          PersistentCitizen.column.lastName -> citizen.lastName,
          PersistentCitizen.column.hashedPassword -> hashedPassword
        )
      }.execute().map {_ => citizen}
    }
  }


}