package org.make.api.user

import java.time.{LocalDate, ZoneOffset, ZonedDateTime}

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.ShortenedNames
import org.make.core.profile.{Gender, Profile}
import org.make.core.user.{Role, User, UserId}
import scalikejdbc._
import scalikejdbc.interpolation.SQLSyntax._

import scala.concurrent.Future

trait PersistentUserServiceComponent { this: MakeDBExecutionContextComponent =>

  def persistentUserService: PersistentUserService

  val ROLE_SEPARATOR = ","

  case class PersistentUser(uuid: String,
                            createdAt: ZonedDateTime,
                            updatedAt: ZonedDateTime,
                            email: String,
                            firstName: Option[String],
                            lastName: Option[String],
                            lastIp: Option[String],
                            hashedPassword: String,
                            salt: String,
                            enabled: Boolean,
                            verified: Boolean,
                            lastConnection: ZonedDateTime,
                            verificationToken: Option[String],
                            verificationTokenExpiresAt: Option[ZonedDateTime],
                            resetToken: Option[String],
                            resetTokenExpiresAt: Option[ZonedDateTime],
                            roles: String,
                            dateOfBirth: Option[LocalDate],
                            avatarUrl: Option[String],
                            profession: Option[String],
                            phoneNumber: Option[String],
                            twitterId: Option[String],
                            facebookId: Option[String],
                            googleId: Option[String],
                            gender: String,
                            genderName: Option[String],
                            departmentNumber: Option[String],
                            karmaLevel: Option[Int],
                            locale: Option[String],
                            optInNewsletter: Boolean) {
    def toUser: User = {
      User(
        userId = UserId(uuid),
        email = email,
        firstName = firstName,
        lastName = lastName,
        lastIp = lastIp,
        hashedPassword = Some(hashedPassword),
        salt = Some(salt),
        enabled = enabled,
        verified = verified,
        lastConnection = lastConnection,
        verificationToken = verificationToken,
        verificationTokenExpiresAt = verificationTokenExpiresAt,
        resetToken = resetToken,
        resetTokenExpiresAt = resetTokenExpiresAt,
        roles = roles.split(ROLE_SEPARATOR).flatMap(role => toRole(role).toList),
        profile = toProfile
      )
    }

    private def toRole: (String)   => Option[Role] = Role.matchRole
    private def toGender: (String) => Option[Gender] = Gender.matchGender

    private def toProfile: Option[Profile] = {
      Profile.parseProfile(
        dateOfBirth = dateOfBirth,
        avatarUrl = avatarUrl,
        profession = profession,
        phoneNumber = phoneNumber,
        twitterId = twitterId,
        facebookId = facebookId,
        googleId = googleId,
        gender = toGender(gender),
        genderName = genderName,
        departmentNumber = departmentNumber,
        karmaLevel = karmaLevel,
        locale = locale,
        optInNewsletter = optInNewsletter
      )
    }
  }

  object PersistentUser extends SQLSyntaxSupport[PersistentUser] with ShortenedNames with StrictLogging {

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
      "uuid",
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
      "verification_token_expires_at",
      "reset_token",
      "reset_token_expires_at",
      "roles"
    )

    override val columnNames: Seq[String] = userColumnNames ++ profileColumnNames

    override val tableName: String = "make_user"

    lazy val userAlias: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentUser], PersistentUser] = syntax("u")

    def apply(
      userResultName: ResultName[PersistentUser] = userAlias.resultName
    )(resultSet: WrappedResultSet): PersistentUser = {
      PersistentUser(
        uuid = resultSet.string(userResultName.uuid),
        email = resultSet.string(userResultName.email),
        firstName = resultSet.stringOpt(userResultName.firstName),
        lastName = resultSet.stringOpt(userResultName.lastName),
        createdAt = resultSet.zonedDateTime(userResultName.updatedAt),
        updatedAt = resultSet.zonedDateTime(userResultName.updatedAt),
        lastIp = resultSet.stringOpt(userResultName.lastIp),
        hashedPassword = resultSet.string(userResultName.hashedPassword),
        salt = resultSet.string(userResultName.salt),
        enabled = resultSet.boolean(userResultName.enabled),
        verified = resultSet.boolean(userResultName.verified),
        lastConnection = resultSet.zonedDateTime(userResultName.lastConnection),
        verificationToken = resultSet.stringOpt(userResultName.verificationToken),
        verificationTokenExpiresAt = resultSet.zonedDateTimeOpt(userResultName.verificationTokenExpiresAt),
        resetToken = resultSet.stringOpt(userResultName.resetToken),
        resetTokenExpiresAt = resultSet.zonedDateTimeOpt(userResultName.resetTokenExpiresAt),
        roles = resultSet.string(userResultName.roles),
        dateOfBirth = resultSet.localDateOpt(userResultName.dateOfBirth),
        avatarUrl = resultSet.stringOpt(userResultName.avatarUrl),
        profession = resultSet.stringOpt(userResultName.profession),
        phoneNumber = resultSet.stringOpt(userResultName.phoneNumber),
        twitterId = resultSet.stringOpt(userResultName.twitterId),
        facebookId = resultSet.stringOpt(userResultName.facebookId),
        googleId = resultSet.stringOpt(userResultName.googleId),
        gender = resultSet.string(userResultName.gender),
        genderName = resultSet.stringOpt(userResultName.genderName),
        departmentNumber = resultSet.stringOpt(userResultName.departmentNumber),
        karmaLevel = resultSet.intOpt(userResultName.karmaLevel),
        locale = resultSet.stringOpt(userResultName.locale),
        optInNewsletter = resultSet.boolean(userResultName.optInNewsletter)
      )
    }
  }

  class PersistentUserService extends ShortenedNames with StrictLogging {

    private val userAlias = PersistentUser.userAlias
    private val column = PersistentUser.column

    def get(uuid: UserId): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.uuid, uuid.value))
        }.map(PersistentUser.apply()).single.apply
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    def findByEmailAndHashedPassword(email: String, hashedPassword: String): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(
              sqls
                .eq(userAlias.email, email)
                .and(sqls.eq(userAlias.hashedPassword, hashedPassword))
            )
        }.map(PersistentUser.apply()).single.apply
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    def findByEmail(email: String): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.email, email))
        }.map(PersistentUser.apply()).single.apply
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    def emailExists(email: String): Future[Boolean] = {
      implicit val ctx = readExecutionContext
      Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select(count(userAlias.result.email))
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.email, email))
        }.map(_.int(1) > 0).single.apply
      }).map(_.getOrElse(false))
    }

    def verificationTokenExists(verificationToken: String): Future[Boolean] = {
      implicit val ctx = readExecutionContext
      Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select(count(userAlias.result.verificationToken))
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.verificationToken, verificationToken))
        }.map(_.int(1) > 0).single.apply
      }).map(_.getOrElse(false))
    }

    def resetTokenExists(resetToken: String): Future[Boolean] = {
      implicit val ctx = readExecutionContext
      Future(NamedDB('READ).localTx { implicit session =>
        withSQL {
          select(count(userAlias.result.resetToken))
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.resetToken, resetToken))
        }.map(_.int(1) > 0).single.apply
      }).map(_.getOrElse(false))
    }

    def persist(user: User): Future[User] = {
      implicit val ctx = writeExecutionContext
      Future(NamedDB('WRITE).localTx { implicit session =>
        withSQL {
          insert
            .into(PersistentUser)
            .namedValues(
              column.uuid -> user.userId.value,
              column.createdAt -> ZonedDateTime.now,
              column.updatedAt -> ZonedDateTime.now,
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
              column.verificationTokenExpiresAt -> user.verificationTokenExpiresAt,
              column.resetToken -> user.resetToken,
              column.resetTokenExpiresAt -> user.resetTokenExpiresAt,
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
              column.optInNewsletter -> user.profile.exists(_.optInNewsletter == true)
            )
        }.execute().apply()
      }).map(_ => user)
    }
  }
}
