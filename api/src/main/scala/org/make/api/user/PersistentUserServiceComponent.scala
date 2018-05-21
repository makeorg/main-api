package org.make.api.user

import java.time.{LocalDate, ZoneOffset, ZonedDateTime}

import com.github.t3hnar.bcrypt._
import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.api.user.PersistentUserServiceComponent.PersistentUser
import org.make.core.DateHelper
import org.make.core.auth.UserRights
import org.make.core.profile.{Gender, Profile}
import org.make.core.user.{MailingErrorLog, Role, User, UserId}
import scalikejdbc._
import scalikejdbc.interpolation.SQLSyntax._

import scala.concurrent.Future

trait PersistentUserServiceComponent {
  def persistentUserService: PersistentUserService
}

object PersistentUserServiceComponent {

  val ROLE_SEPARATOR = ","

  case class PersistentUser(uuid: String,
                            createdAt: ZonedDateTime,
                            updatedAt: ZonedDateTime,
                            email: String,
                            firstName: Option[String],
                            lastName: Option[String],
                            lastIp: Option[String],
                            hashedPassword: String,
                            enabled: Boolean,
                            emailVerified: Boolean,
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
                            postalCode: Option[String],
                            country: String,
                            language: String,
                            karmaLevel: Option[Int],
                            locale: Option[String],
                            optInNewsletter: Boolean,
                            isHardBounce: Boolean,
                            lastMailingErrorDate: Option[ZonedDateTime],
                            lastMailingErrorMessage: Option[String],
                            organisationName: Option[String]) {
    def toUser: User = {
      User(
        userId = UserId(uuid),
        email = email,
        createdAt = Some(createdAt),
        updatedAt = Some(updatedAt),
        firstName = firstName,
        lastName = lastName,
        lastIp = lastIp,
        hashedPassword = Option(hashedPassword),
        enabled = enabled,
        emailVerified = emailVerified,
        lastConnection = lastConnection,
        verificationToken = verificationToken,
        verificationTokenExpiresAt = verificationTokenExpiresAt,
        resetToken = resetToken,
        resetTokenExpiresAt = resetTokenExpiresAt,
        roles = roles.split(ROLE_SEPARATOR).flatMap(role => toRole(role).toList),
        country = country,
        language = language,
        profile = toProfile,
        isHardBounce = isHardBounce,
        lastMailingError = lastMailingErrorMessage.flatMap { message =>
          lastMailingErrorDate.map { date =>
            MailingErrorLog(error = message, date = date)
          }
        },
        organisationName = organisationName
      )
    }

    def toUserRights: UserRights = {
      UserRights(userId = UserId(uuid), roles = roles.split(ROLE_SEPARATOR).flatMap(role => toRole(role).toSeq))
    }

    private def toRole: String   => Option[Role] = Role.matchRole
    private def toGender: String => Option[Gender] = Gender.matchGender

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
        postalCode = postalCode,
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
      "postal_code",
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
      "enabled",
      "email_verified",
      "last_connection",
      "verification_token",
      "verification_token_expires_at",
      "reset_token",
      "reset_token_expires_at",
      "roles",
      "country",
      "language",
      "is_hard_bounce",
      "last_mailing_error_date",
      "last_mailing_error_message",
      "organisation_name"
    )

    override val columnNames: Seq[String] = userColumnNames ++ profileColumnNames

    override val tableName: String = "make_user"

    lazy val userAlias: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentUser], PersistentUser] = syntax("u")

    def apply(
      userResultName: ResultName[PersistentUser] = userAlias.resultName
    )(resultSet: WrappedResultSet): PersistentUser = {
      PersistentUser.apply(
        uuid = resultSet.string(userResultName.uuid),
        email = resultSet.string(userResultName.email),
        firstName = resultSet.stringOpt(userResultName.firstName),
        lastName = resultSet.stringOpt(userResultName.lastName),
        createdAt = resultSet.zonedDateTime(userResultName.createdAt),
        updatedAt = resultSet.zonedDateTime(userResultName.updatedAt),
        lastIp = resultSet.stringOpt(userResultName.lastIp),
        hashedPassword = resultSet.string(userResultName.hashedPassword),
        enabled = resultSet.boolean(userResultName.enabled),
        emailVerified = resultSet.boolean(userResultName.emailVerified),
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
        postalCode = resultSet.stringOpt(userResultName.postalCode),
        country = resultSet.string(userResultName.country),
        language = resultSet.string(userResultName.language),
        karmaLevel = resultSet.intOpt(userResultName.karmaLevel),
        locale = resultSet.stringOpt(userResultName.locale),
        optInNewsletter = resultSet.boolean(userResultName.optInNewsletter),
        isHardBounce = resultSet.boolean(userResultName.isHardBounce),
        lastMailingErrorDate = resultSet.zonedDateTimeOpt(userResultName.lastMailingErrorDate),
        lastMailingErrorMessage = resultSet.stringOpt(userResultName.lastMailingErrorMessage),
        organisationName = resultSet.stringOpt(userResultName.organisationName)
      )
    }
  }

}

trait PersistentUserService {

  def get(uuid: UserId): Future[Option[User]]
  def findAllByUserIds(ids: Seq[UserId]): Future[Seq[User]]
  def findByEmailAndPassword(email: String, hashedPassword: String): Future[Option[User]]
  def findByEmail(email: String): Future[Option[User]]
  def findUserIdByEmail(email: String): Future[Option[UserId]]
  def findUserByUserIdAndResetToken(userId: UserId, resetToken: String): Future[Option[User]]
  def findUserByUserIdAndVerificationToken(userId: UserId, verificationToken: String): Future[Option[User]]
  def emailExists(email: String): Future[Boolean]
  def verificationTokenExists(verificationToken: String): Future[Boolean]
  def resetTokenExists(resetToken: String): Future[Boolean]
  def persist(user: User): Future[User]
  def requestResetPassword(userId: UserId,
                           resetToken: String,
                           resetTokenExpiresAt: Option[ZonedDateTime]): Future[Boolean]
  def updatePassword(userId: UserId, resetToken: String, hashedPassword: String): Future[Boolean]
  def validateEmail(verificationToken: String): Future[Boolean]
  def updateOptInNewsletter(userId: UserId, optInNewsletter: Boolean): Future[Boolean]
  def updateIsHardBounce(userId: UserId, isHardBounce: Boolean): Future[Boolean]
  def updateLastMailingError(userId: UserId, lastMailingError: Option[MailingErrorLog]): Future[Boolean]
  def updateOptInNewsletter(email: String, optInNewsletter: Boolean): Future[Boolean]
  def updateIsHardBounce(email: String, isHardBounce: Boolean): Future[Boolean]
  def updateLastMailingError(email: String, lastMailingError: Option[MailingErrorLog]): Future[Boolean]
  def findUsersWithHardBounce(page: Int, limit: Int): Future[Seq[User]]
  def findOptInUsers(page: Int, limit: Int): Future[Seq[User]]
  def findOptOutUsers(page: Int, limit: Int): Future[Seq[User]]
}

trait DefaultPersistentUserServiceComponent extends PersistentUserServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentUserService: PersistentUserService = new PersistentUserService with ShortenedNames
  with StrictLogging {

    private val userAlias = PersistentUser.userAlias
    private val column = PersistentUser.column

    override def get(uuid: UserId): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.uuid, uuid.value))
        }.map(PersistentUser.apply()).single.apply
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    def findAllByUserIds(ids: Seq[UserId]): Future[Seq[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUsers = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.in(userAlias.uuid, ids.map(_.value)))
        }.map(PersistentUser.apply()).list.apply
      })

      futurePersistentUsers.map(_.map(_.toUser))
    }

    override def findByEmailAndPassword(email: String, password: String): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.email, email))
        }.map(PersistentUser.apply()).single.apply
      }).map(_.filter { persistentUser =>
        persistentUser.hashedPassword != null && password.isBcrypted(persistentUser.hashedPassword)
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    override def findByEmail(email: String): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.email, email))
        }.map(PersistentUser.apply()).single.apply
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    override def findUserByUserIdAndResetToken(userId: UserId, resetToken: String): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.uuid, userId.value).and(sqls.eq(userAlias.resetToken, resetToken)))
        }.map(PersistentUser.apply()).single.apply
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    override def findUserByUserIdAndVerificationToken(userId: UserId,
                                                      verificationToken: String): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.uuid, userId.value).and(sqls.eq(userAlias.verificationToken, verificationToken)))
        }.map(PersistentUser.apply()).single.apply
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    override def findUserIdByEmail(email: String): Future[Option[UserId]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUserId = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select(userAlias.result.uuid)
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.email, email))
        }.map(_.string(userAlias.resultName.uuid)).single.apply
      })

      futurePersistentUserId.map(_.map(UserId(_)))
    }

    override def findUsersWithHardBounce(page: Int, limit: Int): Future[Seq[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUsers = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.isHardBounce, true))
            .orderBy(userAlias.createdAt)
            .desc
            .limit(limit)
            .offset(page * limit - limit)

        }.map(PersistentUser.apply()).list.apply
      })

      futurePersistentUsers.map(_.map(_.toUser))
    }

    override def findOptInUsers(page: Int, limit: Int): Future[Seq[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUsers = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.optInNewsletter, true).and(sqls.eq(userAlias.isHardBounce, false)))
            .orderBy(userAlias.createdAt)
            .desc
            .limit(limit)
            .offset(page * limit - limit)
        }.map(PersistentUser.apply()).list.apply
      })

      futurePersistentUsers.map(_.map(_.toUser))
    }

    override def findOptOutUsers(page: Int, limit: Int): Future[Seq[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUsers = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.optInNewsletter, false))
            .orderBy(userAlias.createdAt)
            .desc
            .limit(limit)
            .offset(page * limit - limit)
        }.map(PersistentUser.apply()).list.apply
      })

      futurePersistentUsers.map(_.map(_.toUser))
    }

    override def emailExists(email: String): Future[Boolean] = {
      implicit val ctx: EC = readExecutionContext
      Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select(count(userAlias.email))
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.email, email))
        }.map(_.int(1) > 0).single.apply
      }).map(_.getOrElse(false))
    }

    override def verificationTokenExists(verificationToken: String): Future[Boolean] = {
      implicit val ctx: EC = readExecutionContext
      Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select(count(userAlias.verificationToken))
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.verificationToken, verificationToken))
        }.map(_.int(1) > 0).single.apply
      }).map(_.getOrElse(false))
    }

    override def resetTokenExists(resetToken: String): Future[Boolean] = {
      implicit val ctx: EC = readExecutionContext
      Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select(count(userAlias.resetToken))
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.resetToken, resetToken))
        }.map(_.int(1) > 0).single.apply
      }).map(_.getOrElse(false))
    }

    override def persist(user: User): Future[User] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentUser)
            .namedValues(
              column.uuid -> user.userId.value,
              column.createdAt -> DateHelper.now(),
              column.updatedAt -> DateHelper.now(),
              column.email -> user.email,
              column.firstName -> user.firstName,
              column.lastName -> user.lastName,
              column.lastIp -> user.lastIp,
              column.hashedPassword -> user.hashedPassword,
              column.enabled -> user.enabled,
              column.emailVerified -> user.emailVerified,
              column.lastConnection -> user.lastConnection,
              column.verificationToken -> user.verificationToken,
              column.verificationTokenExpiresAt -> user.verificationTokenExpiresAt,
              column.resetToken -> user.resetToken,
              column.resetTokenExpiresAt -> user.resetTokenExpiresAt,
              column.roles -> user.roles.map(_.shortName).mkString(PersistentUserServiceComponent.ROLE_SEPARATOR),
              column.avatarUrl -> user.profile.flatMap(_.avatarUrl),
              column.profession -> user.profile.flatMap(_.profession),
              column.phoneNumber -> user.profile.flatMap(_.phoneNumber),
              column.twitterId -> user.profile.flatMap(_.twitterId),
              column.facebookId -> user.profile.flatMap(_.facebookId),
              column.googleId -> user.profile.flatMap(_.googleId),
              column.gender -> user.profile.flatMap(_.gender.map(_.shortName)),
              column.genderName -> user.profile.flatMap(_.genderName),
              column.postalCode -> user.profile.flatMap(_.postalCode),
              column.country -> user.country,
              column.language -> user.language,
              column.karmaLevel -> user.profile.flatMap(_.karmaLevel),
              column.locale -> user.profile.flatMap(_.locale),
              column.dateOfBirth -> user.profile.flatMap(_.dateOfBirth.map(_.atStartOfDay(ZoneOffset.UTC))),
              column.optInNewsletter -> user.profile.forall(_.optInNewsletter),
              column.isHardBounce -> user.isHardBounce,
              column.lastMailingErrorDate -> user.lastMailingError.map(_.date),
              column.lastMailingErrorMessage -> user.lastMailingError.map(_.error),
              column.organisationName -> user.organisationName
            )
        }.execute().apply()
      }).map(_ => user)
    }

    override def requestResetPassword(userId: UserId,
                                      resetToken: String,
                                      resetTokenExpiresAt: Option[ZonedDateTime]): Future[Boolean] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          update(PersistentUser)
            .set(column.resetToken -> resetToken, column.resetTokenExpiresAt -> resetTokenExpiresAt)
            .where(
              sqls
                .eq(column.uuid, userId.value)
            )
        }.executeUpdate().apply() match {
          case 1 => true
          case _ => false
        }
      })
    }

    override def updatePassword(userId: UserId, resetToken: String, hashedPassword: String): Future[Boolean] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          update(PersistentUser)
            .set(column.hashedPassword -> hashedPassword, column.resetToken -> None, column.resetTokenExpiresAt -> None)
            .where(
              sqls
                .eq(column.uuid, userId.value)
                .and(sqls.eq(column.resetToken, resetToken))
            )
        }.executeUpdate().apply() match {
          case 1 => true
          case _ => false
        }
      })
    }

    override def validateEmail(verificationToken: String): Future[Boolean] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          update(PersistentUser)
            .set(
              column.emailVerified -> true,
              column.verificationToken -> None,
              column.verificationTokenExpiresAt -> None
            )
            .where(sqls.eq(column.verificationToken, verificationToken))
        }.executeUpdate().apply() match {
          case 1 => true
          case _ => false
        }
      })
    }

    override def updateOptInNewsletter(userId: UserId, optInNewsletter: Boolean): Future[Boolean] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          update(PersistentUser)
            .set(column.optInNewsletter -> optInNewsletter)
            .where(sqls.eq(column.uuid, userId.value))
        }.executeUpdate().apply() match {
          case 1 => true
          case _ => false
        }
      })
    }

    override def updateIsHardBounce(userId: UserId, isHardBounce: Boolean): Future[Boolean] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          update(PersistentUser)
            .set(column.isHardBounce -> isHardBounce)
            .where(sqls.eq(column.uuid, userId.value))
        }.executeUpdate().apply() match {
          case 1 => true
          case _ => false
        }
      })
    }

    override def updateLastMailingError(userId: UserId, lastMailingError: Option[MailingErrorLog]): Future[Boolean] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          update(PersistentUser)
            .set(
              column.lastMailingErrorDate -> lastMailingError.map(_.date),
              column.lastMailingErrorMessage -> lastMailingError.map(_.error)
            )
            .where(sqls.eq(column.uuid, userId.value))
        }.executeUpdate().apply() match {
          case 1 => true
          case _ => false
        }
      })
    }

    override def updateOptInNewsletter(email: String, optInNewsletter: Boolean): Future[Boolean] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          update(PersistentUser)
            .set(column.optInNewsletter -> optInNewsletter)
            .where(sqls.eq(column.email, email))
        }.executeUpdate().apply() match {
          case 1 => true
          case _ => false
        }
      })
    }

    override def updateIsHardBounce(email: String, isHardBounce: Boolean): Future[Boolean] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          update(PersistentUser)
            .set(column.isHardBounce -> isHardBounce)
            .where(sqls.eq(column.email, email))
        }.executeUpdate().apply() match {
          case 1 => true
          case _ => false
        }
      })
    }

    override def updateLastMailingError(email: String, lastMailingError: Option[MailingErrorLog]): Future[Boolean] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          update(PersistentUser)
            .set(
              column.lastMailingErrorDate -> lastMailingError.map(_.date),
              column.lastMailingErrorMessage -> lastMailingError.map(_.error)
            )
            .where(sqls.eq(column.email, email))
        }.executeUpdate().apply() match {
          case 1 => true
          case _ => false
        }
      })
    }

  }
}
