/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.user

import java.time.{LocalDate, ZoneOffset, ZonedDateTime}

import cats.data.NonEmptyList
import com.github.t3hnar.bcrypt._
import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.PersistentServiceUtils.sortOrderQuery
import org.make.api.technical.{PersistentCompanion, ShortenedNames}
import org.make.api.technical.ScalikeSupport._
import org.make.api.user.DefaultPersistentUserServiceComponent.UpdateFailed
import org.make.api.user.PersistentUserServiceComponent.{FollowedUsers, PersistentUser}
import org.make.core.{DateHelper, Order}
import org.make.core.auth.UserRights
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.user._
import scalikejdbc._
import scalikejdbc.interpolation.SQLSyntax._

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait PersistentUserServiceComponent {
  def persistentUserService: PersistentUserService
}

object PersistentUserServiceComponent {

  val ROLE_SEPARATOR = ","

  @SuppressWarnings(Array("org.wartremover.warts.ArrayEquals"))
  final case class PersistentUser(
    uuid: String,
    createdAt: ZonedDateTime,
    updatedAt: ZonedDateTime,
    email: String,
    firstName: Option[String],
    lastName: Option[String],
    lastIp: Option[String],
    hashedPassword: String,
    enabled: Boolean,
    emailVerified: Boolean,
    userType: String,
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
    description: Option[String],
    twitterId: Option[String],
    facebookId: Option[String],
    googleId: Option[String],
    gender: String,
    genderName: Option[String],
    postalCode: Option[String],
    country: String,
    karmaLevel: Option[Int],
    locale: Option[String],
    optInNewsletter: Boolean,
    isHardBounce: Boolean,
    lastMailingErrorDate: Option[ZonedDateTime],
    lastMailingErrorMessage: Option[String],
    organisationName: Option[String],
    publicProfile: Boolean,
    socioProfessionalCategory: String,
    registerQuestionId: Option[String],
    optInPartner: Option[Boolean],
    availableQuestions: Array[String],
    reconnectToken: Option[String],
    reconnectTokenCreatedAt: Option[ZonedDateTime],
    anonymousParticipation: Boolean,
    politicalParty: Option[String],
    website: Option[String],
    legalMinorConsent: Option[Boolean],
    legalAdvisorApproval: Option[Boolean]
  ) {
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
        userType = UserType(userType),
        lastConnection = lastConnection,
        verificationToken = verificationToken,
        verificationTokenExpiresAt = verificationTokenExpiresAt,
        resetToken = resetToken,
        resetTokenExpiresAt = resetTokenExpiresAt,
        roles = roles.split(ROLE_SEPARATOR).toIndexedSeq.map(Role.apply),
        country = Country(country),
        profile = toProfile,
        isHardBounce = isHardBounce,
        lastMailingError = lastMailingErrorMessage.flatMap { message =>
          lastMailingErrorDate.map { date =>
            MailingErrorLog(error = message, date = date)
          }
        },
        organisationName = organisationName,
        publicProfile = publicProfile,
        availableQuestions = availableQuestions.toSeq.map(QuestionId.apply),
        anonymousParticipation = anonymousParticipation
      )
    }

    def toUserRights: UserRights = {
      UserRights(
        userId = UserId(uuid),
        roles = roles.split(ROLE_SEPARATOR).toIndexedSeq.map(Role.apply),
        availableQuestions = availableQuestions.toSeq.map(QuestionId.apply),
        emailVerified = emailVerified
      )
    }

    private def toGender: String                    => Option[Gender] = Gender.withValueOpt
    private def toSocioProfessionalCategory: String => Option[SocioProfessionalCategory] =
      SocioProfessionalCategory.withValueOpt

    private def toProfile: Option[Profile] = {
      Profile.parseProfile(
        dateOfBirth = dateOfBirth,
        avatarUrl = avatarUrl,
        profession = profession,
        phoneNumber = phoneNumber,
        description = description,
        twitterId = twitterId,
        facebookId = facebookId,
        googleId = googleId,
        gender = toGender(gender),
        genderName = genderName,
        postalCode = postalCode,
        karmaLevel = karmaLevel,
        locale = locale,
        optInNewsletter = optInNewsletter,
        socioProfessionalCategory = toSocioProfessionalCategory(socioProfessionalCategory),
        registerQuestionId = registerQuestionId.map(QuestionId.apply),
        optInPartner = optInPartner,
        politicalParty = politicalParty,
        website = website,
        legalMinorConsent = legalMinorConsent,
        legalAdvisorApproval = legalAdvisorApproval
      )
    }
  }

  implicit object PersistentUser
      extends PersistentCompanion[PersistentUser, User]
      with ShortenedNames
      with StrictLogging {

    private val profileColumnNames: Seq[String] = Seq(
      "date_of_birth",
      "avatar_url",
      "profession",
      "phone_number",
      "description",
      "twitter_id",
      "facebook_id",
      "google_id",
      "gender",
      "gender_name",
      "postal_code",
      "karma_level",
      "locale",
      "opt_in_newsletter",
      "socio_professional_category",
      "register_question_id",
      "opt_in_partner",
      "political_party",
      "website",
      "legal_minor_consent",
      "legal_advisor_approval"
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
      "user_type",
      "last_connection",
      "verification_token",
      "verification_token_expires_at",
      "reset_token",
      "reset_token_expires_at",
      "roles",
      "country",
      "is_hard_bounce",
      "last_mailing_error_date",
      "last_mailing_error_message",
      "organisation_name",
      "public_profile",
      "available_questions",
      "reconnect_token",
      "reconnect_token_created_at",
      "anonymous_participation"
    )

    override val columnNames: Seq[String] = userColumnNames ++ profileColumnNames

    override val tableName: String = "make_user"

    override lazy val alias: SyntaxProvider[PersistentUser] = syntax("u")

    override lazy val defaultSortColumns: NonEmptyList[SQLSyntax] = NonEmptyList.of(alias.email)

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    def apply(
      userResultName: ResultName[PersistentUser] = alias.resultName
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
        userType = resultSet.string(userResultName.userType),
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
        description = resultSet.stringOpt(userResultName.description),
        twitterId = resultSet.stringOpt(userResultName.twitterId),
        facebookId = resultSet.stringOpt(userResultName.facebookId),
        googleId = resultSet.stringOpt(userResultName.googleId),
        gender = resultSet.string(userResultName.gender),
        genderName = resultSet.stringOpt(userResultName.genderName),
        postalCode = resultSet.stringOpt(userResultName.postalCode),
        country = resultSet.string(userResultName.country),
        karmaLevel = resultSet.intOpt(userResultName.karmaLevel),
        locale = resultSet.stringOpt(userResultName.locale),
        optInNewsletter = resultSet.boolean(userResultName.optInNewsletter),
        isHardBounce = resultSet.boolean(userResultName.isHardBounce),
        lastMailingErrorDate = resultSet.zonedDateTimeOpt(userResultName.lastMailingErrorDate),
        lastMailingErrorMessage = resultSet.stringOpt(userResultName.lastMailingErrorMessage),
        organisationName = resultSet.stringOpt(userResultName.organisationName),
        publicProfile = resultSet.boolean(userResultName.publicProfile),
        socioProfessionalCategory = resultSet.string(userResultName.socioProfessionalCategory),
        registerQuestionId = resultSet.stringOpt(userResultName.registerQuestionId),
        optInPartner = resultSet.booleanOpt(userResultName.optInPartner),
        availableQuestions = resultSet
          .arrayOpt(userResultName.availableQuestions)
          .map(_.getArray.asInstanceOf[Array[String]])
          .getOrElse(Array()),
        reconnectToken = resultSet.stringOpt(userResultName.reconnectToken),
        reconnectTokenCreatedAt = resultSet.zonedDateTimeOpt(userResultName.reconnectTokenCreatedAt),
        anonymousParticipation = resultSet.boolean(userResultName.anonymousParticipation),
        politicalParty = resultSet.stringOpt(userResultName.politicalParty),
        website = resultSet.stringOpt(userResultName.website),
        legalMinorConsent = resultSet.booleanOpt(userResultName.legalMinorConsent),
        legalAdvisorApproval = resultSet.booleanOpt(userResultName.legalAdvisorApproval)
      )
    }
  }

  final case class FollowedUsers(userId: String, followedUserId: String, date: ZonedDateTime)

  object FollowedUsers extends SQLSyntaxSupport[FollowedUsers] with ShortenedNames with StrictLogging {

    override val columnNames: Seq[String] = Seq("user_id", "followed_user_id", "date")

    override val tableName: String = "followed_user"

    lazy val followedUsersAlias: QuerySQLSyntaxProvider[SQLSyntaxSupport[FollowedUsers], FollowedUsers] = syntax(
      "followed_user"
    )

    def apply(
      followedUsersResultName: ResultName[FollowedUsers] = followedUsersAlias.resultName
    )(resultSet: WrappedResultSet): FollowedUsers = {
      FollowedUsers.apply(
        userId = resultSet.string(followedUsersResultName.userId),
        followedUserId = resultSet.string(followedUsersResultName.followedUserId),
        date = resultSet.zonedDateTime(followedUsersResultName.date)
      )
    }
  }

}

trait PersistentUserService {
  def get(uuid: UserId): Future[Option[User]]
  def findAllByUserIds(ids: Seq[UserId]): Future[Seq[User]]
  def findByEmailAndPassword(email: String, hashedPassword: String): Future[Option[User]]
  def findByUserIdAndPassword(userId: UserId, hashedPassword: Option[String]): Future[Option[User]]
  def findByUserIdAndUserType(userId: UserId, userType: UserType): Future[Option[User]]
  def findByEmail(email: String): Future[Option[User]]
  def adminFindUsers(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    email: Option[String],
    firstName: Option[String],
    lastName: Option[String],
    maybeRole: Option[Role],
    maybeUserType: Option[UserType]
  ): Future[Seq[User]]
  def findAllOrganisations(): Future[Seq[User]]
  def findOrganisations(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    organisationName: Option[String]
  ): Future[Seq[User]]
  def findUserIdByEmail(email: String): Future[Option[UserId]]
  def findUserByUserIdAndResetToken(userId: UserId, resetToken: String): Future[Option[User]]
  def findUserByUserIdAndVerificationToken(userId: UserId, verificationToken: String): Future[Option[User]]
  def findByReconnectTokenAndPassword(
    reconnectToken: String,
    password: String,
    validityReconnectToken: Int
  ): Future[Option[User]]
  def emailExists(email: String): Future[Boolean]
  def verificationTokenExists(verificationToken: String): Future[Boolean]
  def resetTokenExists(resetToken: String): Future[Boolean]
  def persist(user: User): Future[User]
  def updateUser(user: User): Future[User]
  def modifyOrganisation(organisation: User): Future[Either[UpdateFailed, User]]
  def requestResetPassword(
    userId: UserId,
    resetToken: String,
    resetTokenExpiresAt: Option[ZonedDateTime]
  ): Future[Boolean]
  def updatePassword(userId: UserId, resetToken: Option[String], hashedPassword: String): Future[Boolean]
  def validateEmail(verificationToken: String): Future[Boolean]
  def updateOptInNewsletter(userId: UserId, optInNewsletter: Boolean): Future[Boolean]
  def updateIsHardBounce(userId: UserId, isHardBounce: Boolean): Future[Boolean]
  def updateLastMailingError(userId: UserId, lastMailingError: Option[MailingErrorLog]): Future[Boolean]
  def updateOptInNewsletter(email: String, optInNewsletter: Boolean): Future[Boolean]
  def updateIsHardBounce(email: String, isHardBounce: Boolean): Future[Boolean]
  def updateLastMailingError(email: String, lastMailingError: Option[MailingErrorLog]): Future[Boolean]
  def updateSocialUser(user: User): Future[Boolean]
  def findUsersForCrmSynchro(
    optIn: Option[Boolean],
    hardBounce: Option[Boolean],
    offset: Int,
    limit: Int
  ): Future[Seq[User]]
  def findUsersWithoutRegisterQuestion: Future[Seq[User]]
  def getFollowedUsers(userId: UserId): Future[Seq[String]]
  def removeAnonymizedUserFromFollowedUserTable(userId: UserId): Future[Unit]
  def followUser(followedUserId: UserId, userId: UserId): Future[Unit]
  def unfollowUser(followedUserId: UserId, userId: UserId): Future[Unit]
  def countOrganisations(organisationName: Option[String]): Future[Int]
  def adminCountUsers(
    email: Option[String],
    firstName: Option[String],
    lastName: Option[String],
    maybeRole: Option[Role],
    maybeUserType: Option[UserType]
  ): Future[Int]
  def findAllByEmail(emails: Seq[String]): Future[Seq[User]]
  def updateReconnectToken(
    userId: UserId,
    reconnectToken: String,
    reconnectTokenCreatedAt: ZonedDateTime
  ): Future[Boolean]
}

trait DefaultPersistentUserServiceComponent
    extends PersistentUserServiceComponent
    with ShortenedNames
    with StrictLogging {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentUserService: PersistentUserService = new DefaultPersistentUserService

  class DefaultPersistentUserService extends PersistentUserService {

    private val userAlias = PersistentUser.alias
    private val followedUsersAlias = FollowedUsers.followedUsersAlias
    private val column = PersistentUser.column
    private val followedUsersColumn = FollowedUsers.column

    private def defaultEnd(start: Start): Option[End] = Some(End(start.value + 10))

    override def getFollowedUsers(userId: UserId): Future[Seq[String]] = {
      implicit val cxt: EC = readExecutionContext
      val futureUserFollowed = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(FollowedUsers.as(followedUsersAlias))
            .leftJoin(PersistentUser.as(userAlias))
            .on(userAlias.uuid, followedUsersAlias.followedUserId)
            .where(sqls.eq(followedUsersAlias.userId, userId.value).and(sqls.eq(userAlias.publicProfile, true)))
        }.map(FollowedUsers.apply()).list().apply()
      })

      futureUserFollowed.map(_.map(_.followedUserId))
    }

    override def get(uuid: UserId): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.uuid, uuid.value))
        }.map(PersistentUser.apply()).single().apply()
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    def findAllByUserIds(ids: Seq[UserId]): Future[Seq[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUsers = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.in(userAlias.uuid, ids.map(_.value)))
        }.map(PersistentUser.apply()).list().apply()
      })

      futurePersistentUsers.map(_.map(_.toUser))
    }

    override def findByEmailAndPassword(email: String, password: String): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.email, email))
        }.map(PersistentUser.apply()).single().apply()
      }).map(_.filter { persistentUser =>
        persistentUser.hashedPassword != null && password.isBcrypted(persistentUser.hashedPassword)
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    override def findByUserIdAndPassword(userId: UserId, password: Option[String]): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.uuid, userId.value))
        }.map(PersistentUser.apply()).single().apply()
      }).map(_.filter { persistentUser =>
        persistentUser.hashedPassword == null || password.exists(_.isBcrypted(persistentUser.hashedPassword))
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    override def findByUserIdAndUserType(userId: UserId, userType: UserType): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.uuid, userId.value).and(sqls.eq(userAlias.userType, userType)))
        }.map(PersistentUser.apply()).single().apply()
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    override def findByEmail(email: String): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.email, email))
        }.map(PersistentUser.apply()).single().apply()
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    override def findByReconnectTokenAndPassword(
      reconnectToken: String,
      password: String,
      validityReconnectToken: Int
    ): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.reconnectToken, reconnectToken))
        }.map(PersistentUser.apply()).single().apply()
      }).map(_.filter { persistentUser =>
        persistentUser.hashedPassword != null && password.isBcrypted(persistentUser.hashedPassword) &&
        persistentUser.reconnectTokenCreatedAt.exists(_.plusMinutes(validityReconnectToken).isAfter(DateHelper.now()))
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    override def adminFindUsers(
      start: Start,
      end: Option[End],
      sort: Option[String],
      order: Option[Order],
      email: Option[String],
      firstName: Option[String],
      lastName: Option[String],
      maybeRole: Option[Role],
      maybeUserType: Option[UserType]
    ): Future[Seq[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          val query: scalikejdbc.PagingSQLBuilder[PersistentUser] = select
            .from(PersistentUser.as(userAlias))
            .where(
              sqls.toAndConditionOpt(
                email.map(email => sqls.like(userAlias.email, s"%${email.replace("%", "\\%")}%")),
                firstName.map(
                  firstName =>
                    sqls.like(sqls.lower(userAlias.firstName), s"%${firstName.replace("%", "\\%").toLowerCase}%")
                ),
                lastName.map(
                  lastName =>
                    sqls.like(sqls.lower(userAlias.lastName), s"%${lastName.replace("%", "\\%").toLowerCase}%")
                ),
                maybeRole.map(role         => sqls.like(userAlias.roles, s"%${role.value}%")),
                maybeUserType.map(userType => sqls.eq(userAlias.userType, userType))
              )
            )
          sortOrderQuery(start, end.orElse(defaultEnd(start)), sort, order, query)
        }.map(PersistentUser.apply()).list().apply()
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    override def findAllOrganisations(): Future[Seq[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUsers: Future[List[PersistentUser]] = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.userType, UserType.UserTypeOrganisation))
        }.map(PersistentUser.apply()).list().apply()
      })

      futurePersistentUsers.map(_.map(_.toUser))

    }

    override def findOrganisations(
      start: Start,
      end: Option[End],
      sort: Option[String],
      order: Option[Order],
      organisationName: Option[String]
    ): Future[Seq[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUsers: Future[List[PersistentUser]] = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          val query: scalikejdbc.PagingSQLBuilder[PersistentUser] =
            select
              .from(PersistentUser.as(userAlias))
              .where(
                sqls
                  .eq(userAlias.userType, UserType.UserTypeOrganisation)
                  .and(
                    organisationName.map(
                      organisationName =>
                        sqls.like(
                          sqls.lower(userAlias.organisationName),
                          s"%${organisationName.replace("%", "\\%").toLowerCase}%"
                        )
                    )
                  )
              )
          sortOrderQuery(start, end.orElse(defaultEnd(start)), sort.orElse(Some("organisationName")), order, query)
        }.map(PersistentUser.apply()).list().apply()
      })

      futurePersistentUsers.map(_.map(_.toUser))
    }

    override def findUserByUserIdAndResetToken(userId: UserId, resetToken: String): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.uuid, userId.value).and(sqls.eq(userAlias.resetToken, resetToken)))
        }.map(PersistentUser.apply()).single().apply()
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    override def findUserByUserIdAndVerificationToken(
      userId: UserId,
      verificationToken: String
    ): Future[Option[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUser = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.uuid, userId.value).and(sqls.eq(userAlias.verificationToken, verificationToken)))
        }.map(PersistentUser.apply()).single().apply()
      })

      futurePersistentUser.map(_.map(_.toUser))
    }

    override def findUserIdByEmail(email: String): Future[Option[UserId]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUserId = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select(userAlias.result.uuid)
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.email, email))
        }.map(_.string(userAlias.resultName.uuid)).single().apply()
      })

      futurePersistentUserId.map(_.map(UserId(_)))
    }

    override def findUsersForCrmSynchro(
      optIn: Option[Boolean],
      hardBounce: Option[Boolean],
      offset: Int,
      limit: Int
    ): Future[Seq[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUsers = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(
              sqls.toAndConditionOpt(
                hardBounce.map(sqls.eq(userAlias.isHardBounce, _)),
                optIn.map(sqls.eq(userAlias.optInNewsletter, _)),
                Some(sqls.notLike(userAlias.email, "yopmail+%@make.org"))
              )
            )
            .orderBy(userAlias.createdAt.asc, userAlias.email.asc)
            .limit(limit)
            .offset(offset)

        }.map(PersistentUser.apply()).list().apply()
      })

      futurePersistentUsers.map(_.map(_.toUser))
    }

    override def findUsersWithoutRegisterQuestion: Future[Seq[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUsers = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.isNull(userAlias.registerQuestionId))
        }.map(PersistentUser.apply()).list().apply()
      })

      futurePersistentUsers.map(_.map(_.toUser))
    }

    override def emailExists(email: String): Future[Boolean] = {
      implicit val ctx: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select(count(userAlias.email))
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.email, email))
        }.map(_.int(1) > 0).single().apply()
      }).map(_.getOrElse(false))
    }

    override def verificationTokenExists(verificationToken: String): Future[Boolean] = {
      implicit val ctx: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select(count(userAlias.verificationToken))
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.verificationToken, verificationToken))
        }.map(_.int(1) > 0).single().apply()
      }).map(_.getOrElse(false))
    }

    override def resetTokenExists(resetToken: String): Future[Boolean] = {
      implicit val ctx: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select(count(userAlias.resetToken))
            .from(PersistentUser.as(userAlias))
            .where(sqls.eq(userAlias.resetToken, resetToken))
        }.map(_.int(1) > 0).single().apply()
      }).map(_.getOrElse(false))
    }

    override def persist(user: User): Future[User] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
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
              column.userType -> user.userType,
              column.lastConnection -> user.lastConnection,
              column.verificationToken -> user.verificationToken,
              column.verificationTokenExpiresAt -> user.verificationTokenExpiresAt,
              column.resetToken -> user.resetToken,
              column.resetTokenExpiresAt -> user.resetTokenExpiresAt,
              column.roles -> user.roles.map(_.value).mkString(PersistentUserServiceComponent.ROLE_SEPARATOR),
              column.avatarUrl -> user.profile.flatMap(_.avatarUrl),
              column.profession -> user.profile.flatMap(_.profession),
              column.phoneNumber -> user.profile.flatMap(_.phoneNumber),
              column.description -> user.profile.flatMap(_.description),
              column.twitterId -> user.profile.flatMap(_.twitterId),
              column.facebookId -> user.profile.flatMap(_.facebookId),
              column.googleId -> user.profile.flatMap(_.googleId),
              column.gender -> user.profile.flatMap(_.gender),
              column.genderName -> user.profile.flatMap(_.genderName),
              column.postalCode -> user.profile.flatMap(_.postalCode),
              column.country -> user.country.value,
              column.karmaLevel -> user.profile.flatMap(_.karmaLevel),
              column.locale -> user.profile.flatMap(_.locale),
              column.dateOfBirth -> user.profile.flatMap(_.dateOfBirth.map(_.atStartOfDay(ZoneOffset.UTC))),
              column.optInNewsletter -> user.profile.forall(_.optInNewsletter),
              column.isHardBounce -> user.isHardBounce,
              column.lastMailingErrorDate -> user.lastMailingError.map(_.date),
              column.lastMailingErrorMessage -> user.lastMailingError.map(_.error),
              column.organisationName -> user.organisationName,
              column.publicProfile -> user.publicProfile,
              column.socioProfessionalCategory -> user.profile.flatMap(_.socioProfessionalCategory),
              column.registerQuestionId -> user.profile.flatMap(_.registerQuestionId.map(_.value)),
              column.optInPartner -> user.profile.flatMap(_.optInPartner),
              column.availableQuestions -> session.connection
                .createArrayOf("VARCHAR", user.availableQuestions.map(_.value).toArray),
              column.anonymousParticipation -> user.anonymousParticipation,
              column.politicalParty -> user.profile.flatMap(_.politicalParty),
              column.website -> user.profile.flatMap(_.website),
              column.legalMinorConsent -> user.profile.flatMap(_.legalMinorConsent),
              column.legalAdvisorApproval -> user.profile.flatMap(_.legalAdvisorApproval)
            )
        }.execute().apply()
      }).map(_ => user)
    }

    override def modifyOrganisation(organisation: User): Future[Either[UpdateFailed, User]] = {
      implicit val ctx: EC = writeExecutionContext
      val nowDate: ZonedDateTime = DateHelper.now()
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentUser)
            .set(
              column.organisationName -> organisation.organisationName,
              column.email -> organisation.email,
              column.avatarUrl -> organisation.profile.flatMap(_.avatarUrl),
              column.description -> organisation.profile.flatMap(_.description),
              column.website -> organisation.profile.flatMap(_.website),
              column.optInNewsletter -> organisation.profile.forall(_.optInNewsletter),
              column.updatedAt -> nowDate
            )
            .where(
              sqls
                .eq(column.uuid, organisation.userId.value)
            )
        }.executeUpdate().apply()
      }).flatMap {
        case 1 => Future.successful(Right(organisation.copy(updatedAt = Some(nowDate))))
        case 0 =>
          logger.error(s"Organisation '${organisation.userId.value}' not found")
          Future.successful(Left(UpdateFailed()))
        case _ =>
          logger.error(s"Update of organisation '${organisation.userId.value}' failed - not found")
          Future.successful(Left(UpdateFailed()))
      }
    }

    override def updateUser(user: User): Future[User] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentUser)
            .set(
              column.createdAt -> user.createdAt,
              column.email -> user.email,
              column.firstName -> user.firstName,
              column.lastName -> user.lastName,
              column.lastIp -> user.lastIp,
              column.enabled -> user.enabled,
              column.emailVerified -> user.emailVerified,
              column.userType -> user.userType,
              column.lastConnection -> user.lastConnection,
              column.verificationToken -> user.verificationToken,
              column.verificationTokenExpiresAt -> user.verificationTokenExpiresAt,
              column.resetToken -> user.resetToken,
              column.resetTokenExpiresAt -> user.resetTokenExpiresAt,
              column.roles -> user.roles.map(_.value).mkString(PersistentUserServiceComponent.ROLE_SEPARATOR),
              column.avatarUrl -> user.profile.flatMap(_.avatarUrl),
              column.profession -> user.profile.flatMap(_.profession),
              column.phoneNumber -> user.profile.flatMap(_.phoneNumber),
              column.description -> user.profile.flatMap(_.description),
              column.twitterId -> user.profile.flatMap(_.twitterId),
              column.facebookId -> user.profile.flatMap(_.facebookId),
              column.googleId -> user.profile.flatMap(_.googleId),
              column.gender -> user.profile.flatMap(_.gender),
              column.genderName -> user.profile.flatMap(_.genderName),
              column.postalCode -> user.profile.flatMap(_.postalCode),
              column.country -> user.country.value,
              column.karmaLevel -> user.profile.flatMap(_.karmaLevel),
              column.locale -> user.profile.flatMap(_.locale),
              column.dateOfBirth -> user.profile.flatMap(_.dateOfBirth.map(_.atStartOfDay(ZoneOffset.UTC))),
              column.optInNewsletter -> user.profile.forall(_.optInNewsletter),
              column.isHardBounce -> user.isHardBounce,
              column.lastMailingErrorDate -> user.lastMailingError.map(_.date),
              column.lastMailingErrorMessage -> user.lastMailingError.map(_.error),
              column.organisationName -> user.organisationName,
              column.publicProfile -> user.publicProfile,
              column.socioProfessionalCategory -> user.profile.flatMap(_.socioProfessionalCategory),
              column.registerQuestionId -> user.profile.flatMap(_.registerQuestionId.map(_.value)),
              column.optInPartner -> user.profile.flatMap(_.optInPartner),
              column.availableQuestions -> session.connection
                .createArrayOf("VARCHAR", user.availableQuestions.map(_.value).toArray),
              column.anonymousParticipation -> user.anonymousParticipation,
              column.politicalParty -> user.profile.flatMap(_.politicalParty),
              column.website -> user.profile.flatMap(_.website),
              column.legalMinorConsent -> user.profile.flatMap(_.legalMinorConsent),
              column.legalAdvisorApproval -> user.profile.flatMap(_.legalAdvisorApproval)
            )
            .where(
              sqls
                .eq(column.uuid, user.userId.value)
            )
        }.executeUpdate().apply() match {
          case 1 => user.copy(updatedAt = Some(DateHelper.now()))
          case _ =>
            logger.error(s"Update of user '${user.userId.value}' failed - not found")
            user
        }
      })
    }

    override def requestResetPassword(
      userId: UserId,
      resetToken: String,
      resetTokenExpiresAt: Option[ZonedDateTime]
    ): Future[Boolean] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
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

    override def updatePassword(userId: UserId, resetToken: Option[String], hashedPassword: String): Future[Boolean] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          val query = update(PersistentUser)
            .set(column.hashedPassword -> hashedPassword, column.resetToken -> None, column.resetTokenExpiresAt -> None)
          resetToken match {
            case Some(token) =>
              query.where(
                sqls
                  .eq(column.uuid, userId.value)
                  .and(sqls.eq(column.resetToken, token))
              )
            case _ =>
              query.where(
                sqls
                  .eq(column.uuid, userId.value)
              )
          }
        }.executeUpdate().apply() match {
          case 1 => true
          case _ => false
        }
      })
    }

    override def validateEmail(verificationToken: String): Future[Boolean] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
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
      Future(NamedDB("WRITE").retryableTx { implicit session =>
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
      Future(NamedDB("WRITE").retryableTx { implicit session =>
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
      Future(NamedDB("WRITE").retryableTx { implicit session =>
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
      Future(NamedDB("WRITE").retryableTx { implicit session =>
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
      Future(NamedDB("WRITE").retryableTx { implicit session =>
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
      Future(NamedDB("WRITE").retryableTx { implicit session =>
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

    override def updateSocialUser(user: User): Future[Boolean] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentUser)
            .set(
              column.updatedAt -> DateHelper.now(),
              column.firstName -> user.firstName,
              column.lastName -> user.lastName,
              column.dateOfBirth -> user.profile.flatMap(_.dateOfBirth),
              column.lastIp -> user.lastIp,
              column.lastConnection -> DateHelper.now(),
              column.avatarUrl -> user.profile.flatMap(_.avatarUrl),
              column.facebookId -> user.profile.flatMap(_.facebookId),
              column.googleId -> user.profile.flatMap(_.googleId),
              column.gender -> user.profile.flatMap(_.gender),
              column.genderName -> user.profile.flatMap(_.genderName),
              column.country -> user.country.value,
              column.socioProfessionalCategory -> user.profile.flatMap(_.socioProfessionalCategory),
              column.emailVerified -> user.emailVerified,
              column.hashedPassword -> user.hashedPassword
            )
            .where(sqls.eq(column.uuid, user.userId.value))
        }.executeUpdate().apply() match {
          case 1 => true
          case _ => false
        }
      })
    }

    override def removeAnonymizedUserFromFollowedUserTable(userId: UserId): Future[Unit] = {
      implicit val cxt: EC = readExecutionContext
      val futureUserFollowed = Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          delete
            .from(FollowedUsers.as(followedUsersAlias))
            .where(
              sqls
                .eq(followedUsersAlias.userId, userId.value)
                .or(sqls.eq(followedUsersAlias.followedUserId, userId.value))
            )
        }.executeUpdate().apply()
      })

      futureUserFollowed.map(_ => {})
    }

    override def followUser(followedUserId: UserId, userId: UserId): Future[Unit] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(FollowedUsers)
            .namedValues(
              followedUsersColumn.userId -> userId.value,
              followedUsersColumn.followedUserId -> followedUserId.value,
              followedUsersColumn.date -> DateHelper.now()
            )
        }.execute().apply()
        () // TODO check success
      })
    }

    override def unfollowUser(followedUserId: UserId, userId: UserId): Future[Unit] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          delete
            .from(FollowedUsers)
            .where(
              sqls
                .eq(followedUsersAlias.userId, userId.value)
                .and(sqls.eq(followedUsersAlias.followedUserId, followedUserId.value))
            )
        }.executeUpdate().apply()
        () // TODO check success
      })
    }

    override def countOrganisations(organisationName: Option[String]): Future[Int] = {
      implicit val ctx: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select(sqls.count)
            .from(PersistentUser.as(userAlias))
            .where(
              sqls
                .eq(userAlias.userType, UserType.UserTypeOrganisation)
                .and(
                  organisationName.map(
                    organisationName =>
                      sqls.like(
                        sqls.lower(userAlias.organisationName),
                        s"%${organisationName.replace("%", "\\%").toLowerCase}%"
                      )
                  )
                )
            )
        }.map(_.int(1)).single().apply().getOrElse(0)
      })
    }

    override def adminCountUsers(
      email: Option[String],
      firstName: Option[String],
      lastName: Option[String],
      maybeRole: Option[Role],
      maybeUserType: Option[UserType]
    ): Future[Int] = {
      implicit val ctx: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select(sqls.count)
            .from(PersistentUser.as(userAlias))
            .where(
              sqls.toAndConditionOpt(
                email.map(email            => sqls.like(userAlias.email, s"%${email.replace("%", "\\%")}%")),
                firstName.map(firstName    => sqls.like(userAlias.firstName, s"%${firstName.replace("%", "\\%")}%")),
                lastName.map(lastName      => sqls.like(userAlias.lastName, s"%${lastName.replace("%", "\\%")}%")),
                maybeRole.map(role         => sqls.like(userAlias.roles, s"%${role.value}%")),
                maybeUserType.map(userType => sqls.eq(userAlias.userType, userType))
              )
            )
        }.map(_.int(1)).single().apply().getOrElse(0)
      })
    }

    override def findAllByEmail(emails: Seq[String]): Future[Seq[User]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentUsers = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentUser.as(userAlias))
            .where(sqls.in(userAlias.email, emails))
        }.map(PersistentUser.apply()).list().apply()
      })

      futurePersistentUsers.map(_.map(_.toUser))
    }

    override def updateReconnectToken(
      userId: UserId,
      reconnectToken: String,
      reconnectTokenCreatedAt: ZonedDateTime
    ): Future[Boolean] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentUser)
            .set(column.reconnectToken -> reconnectToken, column.reconnectTokenCreatedAt -> reconnectTokenCreatedAt)
            .where(sqls.eq(column.uuid, userId.value))
        }.executeUpdate().apply() match {
          case 1 => true
          case _ => false
        }
      })
    }
  }
}

object DefaultPersistentUserServiceComponent {
  final case class UpdateFailed() extends Exception
}
