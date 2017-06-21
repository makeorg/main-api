package org.make.api.user

import java.time.{LocalDate, ZoneOffset, ZonedDateTime}

import org.make.api.technical.ShortenedNames
import org.make.core.profile.{Gender, Profile}
import org.make.core.user.{Role, User, UserId}
import scalikejdbc._
import scalikejdbc.interpolation.SQLSyntax._

import scala.concurrent.{ExecutionContext, Future}
trait PersistentUserServiceComponent {

  def persistentUserService: PersistentUserService
  def readExecutionContext: ExecutionContext
  def writeExecutionContext: ExecutionContext

  val ROLE_SEPARATOR = ","

  case class PersistentUser(id: UserId,
                            createdAt: ZonedDateTime,
                            updatedAt: ZonedDateTime,
                            email: String,
                            firstName: String,
                            lastName: String,
                            lastIp: String,
                            hashedPassword: String,
                            salt: String,
                            enabled: Boolean,
                            verified: Boolean,
                            lastConnection: ZonedDateTime,
                            verificationToken: String,
                            roles: Seq[Role],
                            profile: Profile,
                            dateOfBirth: LocalDate,
                            avatarUrl: String,
                            profession: String,
                            phoneNumber: String,
                            twitterId: String,
                            facebookId: String,
                            googleId: String,
                            gender: Gender,
                            genderName: String,
                            departmentNumber: String,
                            karmaLevel: Int,
                            locale: String,
                            optInNewsletter: Boolean)

  object PersistentUser extends SQLSyntaxSupport[PersistentUser] with ShortenedNames {

    private val profileColumnNames: Seq[String] = Seq(
      "date_of_birth",
      "avatar_url",
      "profession",
      "phone_number",
      "twitter_id",
      "facebook_id",
      "google_id",
      "gender",
      "gender_name",
      "department_number",
      "karma_level",
      "locale",
      "opt_in_newsletter"
    )

    private val userColumnNames: Seq[String] = Seq(
      "id",
      "created_at",
      "updated_at",
      "email",
      "first_name",
      "last_name",
      "last_ip",
      "hashed_password",
      "salt",
      "enabled",
      "verified",
      "last_connection",
      "verification_token",
      "roles"
    )

    override val columnNames: Seq[String] = userColumnNames ++ profileColumnNames

    override val tableName: String = "make_user"

    lazy val user: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentUser], PersistentUser] = syntax("u")

    def toRole: (String)   => Option[Role] = Role.matchRole
    def toGender: (String) => Option[Gender] = Gender.matchGender

    def toProfile(resultSet: WrappedResultSet): Option[Profile] = {
      Profile.parseProfile(
        dateOfBirth = resultSet.localDateOpt(column.dateOfBirth),
        avatarUrl = resultSet.stringOpt(column.avatarUrl),
        profession = resultSet.stringOpt(column.profession),
        phoneNumber = resultSet.stringOpt(column.phoneNumber),
        twitterId = resultSet.stringOpt(column.twitterId),
        facebookId = resultSet.stringOpt(column.facebookId),
        googleId = resultSet.stringOpt(column.googleId),
        gender = toGender(resultSet.string(column.gender)),
        genderName = resultSet.stringOpt(column.genderName),
        departmentNumber = resultSet.stringOpt(column.departmentNumber),
        karmaLevel = resultSet.intOpt(column.karmaLevel),
        locale = resultSet.stringOpt(column.locale),
        optInNewsletter = resultSet.boolean(column.optInNewsletter)
      )
    }

    def toUser(resultSet: WrappedResultSet): User = {
      User(
        userId = UserId(resultSet.string(column.id)),
        email = resultSet.string(column.email),
        firstName = resultSet.string(column.firstName),
        lastName = resultSet.string(column.lastName),
        createdAt = resultSet.zonedDateTime(column.updatedAt),
        updatedAt = resultSet.zonedDateTime(column.updatedAt),
        lastIp = resultSet.string(column.lastIp),
        hashedPassword = resultSet.string(column.hashedPassword),
        salt = resultSet.string(column.salt),
        enabled = resultSet.boolean(column.enabled),
        verified = resultSet.boolean(column.verified),
        lastConnection = resultSet.zonedDateTime(column.lastConnection),
        verificationToken = resultSet.string(column.verificationToken),
        roles = resultSet.string(column.roles).split(ROLE_SEPARATOR).flatMap(role => toRole(role).toList),
        profile = toProfile(resultSet)
      )
    }
  }

  class PersistentUserService extends ShortenedNames {

    private val userSql = PersistentUser.user
    private val column = PersistentUser.column

    def get(id: UserId): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select(userSql.*)
            .from(PersistentUser.as(userSql))
            .where(sqls.eq(userSql.id, id.value))
        }.map(PersistentUser.toUser).single.apply
      })
    }

    def findByEmailAndHashedPassword(email: String, hashedPassword: String): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select(userSql.*)
            .from(PersistentUser.as(userSql))
            .where(
              sqls
                .eq(userSql.email, email)
                .and(sqls.eq(userSql.hashedPassword, hashedPassword))
            )
        }.map(PersistentUser.toUser).single().apply()
      })
    }

    def emailExists(email: String): Future[Boolean] = {
      implicit val ctx = readExecutionContext
      Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select(count(userSql.asterisk))
            .from(PersistentUser.as(userSql))
            .where(sqls.eq(userSql.email, email))
        }.map(resultSet => resultSet.int(1) > 0).single().apply()
      }).map(_.getOrElse(false))
    }

    def persist(user: User): Future[User] = {
      implicit val ctx = writeExecutionContext
      Future(NamedDB('WRITE).localTx { implicit session =>
        withSQL {
          insert
            .into(PersistentUser)
            .namedValues(
              column.id -> user.userId.value,
              column.createdAt -> user.createdAt,
              column.updatedAt -> user.updatedAt,
              column.email -> user.email,
              column.firstName -> user.firstName,
              column.lastName -> user.lastName,
              column.lastIp -> user.lastIp,
              column.hashedPassword -> user.hashedPassword,
              column.salt -> user.salt,
              column.enabled -> user.enabled,
              column.verified -> user.verified,
              column.lastConnection -> user.lastConnection,
              column.verificationToken -> user.verificationToken,
              column.roles -> user.roles.map(_.shortName).mkString(ROLE_SEPARATOR),
              column.avatarUrl -> user.profile.map(_.avatarUrl),
              column.profession -> user.profile.map(_.profession),
              column.phoneNumber -> user.profile.map(_.phoneNumber),
              column.twitterId -> user.profile.map(_.twitterId),
              column.facebookId -> user.profile.map(_.facebookId),
              column.googleId -> user.profile.map(_.googleId),
              column.gender -> user.profile.map(_.gender.map(_.shortName)),
              column.genderName -> user.profile.map(_.genderName),
              column.departmentNumber -> user.profile.map(_.departmentNumber),
              column.karmaLevel -> user.profile.map(_.karmaLevel),
              column.locale -> user.profile.map(_.locale),
              column.dateOfBirth -> user.profile.map(_.dateOfBirth.map(_.atStartOfDay(ZoneOffset.UTC))),
              column.optInNewsletter -> user.profile.map(_.optInNewsletter).getOrElse(false)
            )
        }.execute().apply()
      }).map(_ => user)
    }
  }

}
