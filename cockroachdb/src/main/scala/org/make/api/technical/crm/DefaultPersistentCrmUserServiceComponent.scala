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

package org.make.api.technical.crm

import grizzled.slf4j.Logging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.ShortenedNames
import scalikejdbc.{ResultName, WrappedResultSet, _}

import scala.concurrent.Future
import org.make.api.technical.DatabaseTransactions._

trait DefaultPersistentCrmUserServiceComponent extends PersistentCrmUserServiceComponent with ShortenedNames {
  self: MakeDBExecutionContextComponent =>

  override lazy val persistentCrmUserService: DefaultPersistentCrmUserService = new DefaultPersistentCrmUserService

  class DefaultPersistentCrmUserService extends PersistentCrmUserService {

    private val PersistentCrmUser = PersistentCrmUserCompanion

    override def persist(users: Seq[PersistentCrmUser]): Future[Seq[PersistentCrmUser]] = {
      implicit val cxt: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        users.foreach {
          user =>
            withSQL {
              insert
                .into(PersistentCrmUser)
                .namedValues(
                  PersistentCrmUser.column.userId -> user.userId,
                  PersistentCrmUser.column.fullName -> user.fullName,
                  PersistentCrmUser.column.accountCreationCountry -> user.accountCreationCountry,
                  PersistentCrmUser.column.accountCreationDate -> user.accountCreationDate,
                  PersistentCrmUser.column.accountCreationOperation -> user.accountCreationOperation,
                  PersistentCrmUser.column.accountCreationOrigin -> user.accountCreationOrigin,
                  PersistentCrmUser.column.accountCreationSource -> user.accountCreationSource,
                  PersistentCrmUser.column.accountCreationLocation -> user.accountCreationLocation,
                  PersistentCrmUser.column.countriesActivity -> user.countriesActivity,
                  PersistentCrmUser.column.dateOfBirth -> user.dateOfBirth,
                  PersistentCrmUser.column.daysBeforeDeletion -> user.daysBeforeDeletion,
                  PersistentCrmUser.column.daysOfActivity -> user.daysOfActivity,
                  PersistentCrmUser.column.daysOfActivity30d -> user.daysOfActivity30d,
                  PersistentCrmUser.column.email -> user.email,
                  PersistentCrmUser.column.emailHardbounceStatus -> user.emailHardbounceStatus,
                  PersistentCrmUser.column.emailValidationStatus -> user.emailValidationStatus,
                  PersistentCrmUser.column.eventsCount -> user.eventsCount,
                  PersistentCrmUser.column.firstContributionDate -> user.firstContributionDate,
                  PersistentCrmUser.column.firstname -> user.firstname,
                  PersistentCrmUser.column.lastActivityDate -> user.lastActivityDate,
                  PersistentCrmUser.column.lastContributionDate -> user.lastContributionDate,
                  PersistentCrmUser.column.lastCountryActivity -> user.lastCountryActivity,
                  PersistentCrmUser.column.operationActivity -> user.operationActivity,
                  PersistentCrmUser.column.sessionsCount -> user.sessionsCount,
                  PersistentCrmUser.column.sourceActivity -> user.sourceActivity,
                  PersistentCrmUser.column.totalNumberProposals -> user.totalNumberProposals,
                  PersistentCrmUser.column.totalNumberVotes -> user.totalNumberVotes,
                  PersistentCrmUser.column.unsubscribeStatus -> user.unsubscribeStatus,
                  PersistentCrmUser.column.userType -> user.userType,
                  PersistentCrmUser.column.accountType -> user.accountType,
                  PersistentCrmUser.column.zipcode -> user.zipcode
                )
            }.execute().apply()
        }
      }).map(_ => users)
    }

    override def list(
      maybeUnsubscribed: Option[Boolean],
      hardBounce: Boolean,
      offset: Int,
      numberPerPage: Int
    ): Future[Seq[PersistentCrmUser]] = {
      implicit val cxt: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select.all
            .from(PersistentCrmUser.as(PersistentCrmUser.alias))
            .where(
              sqls
                .eq(PersistentCrmUser.alias.emailHardbounceStatus, hardBounce)
                .and(
                  maybeUnsubscribed
                    .map(unsubscribed => sqls.eq(PersistentCrmUser.alias.unsubscribeStatus, unsubscribed))
                )
            )
            .orderBy(PersistentCrmUser.alias.accountCreationDate.asc, PersistentCrmUser.alias.email.asc)
            .limit(numberPerPage)
            .offset(offset)
        }.map(PersistentCrmUser.apply()).list().apply()
      })
    }

    override def findInactiveUsers(offset: Int, numberPerPage: Int): Future[Seq[PersistentCrmUser]] = {
      implicit val cxt: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select.all
            .from(PersistentCrmUser.as(PersistentCrmUser.alias))
            .where(sqls.le(PersistentCrmUser.alias.daysBeforeDeletion, 0))
            .orderBy(PersistentCrmUser.alias.email.asc)
            .limit(numberPerPage)
            .offset(offset)
        }.map(PersistentCrmUser.apply()).list().apply()
      })
    }

    override def truncateCrmUsers(): Future[Unit] = {
      implicit val cxt: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        SQL(s"truncate table ${PersistentCrmUser.tableName}").execute().apply()
        ()
      })
    }
  }
}

object PersistentCrmUserCompanion extends SQLSyntaxSupport[PersistentCrmUser] with ShortenedNames with Logging {
  override val columnNames: Seq[String] =
    Seq(
      "user_id",
      "email",
      "full_name",
      "firstname",
      "zipcode",
      "date_of_birth",
      "email_validation_status",
      "email_hardbounce_status",
      "unsubscribe_status",
      "account_creation_date",
      "account_creation_source",
      "account_creation_origin",
      "account_creation_operation",
      "account_creation_country",
      "account_creation_location",
      "countries_activity",
      "last_country_activity",
      "total_number_proposals",
      "total_number_votes",
      "first_contribution_date",
      "last_contribution_date",
      "operation_activity",
      "source_activity",
      "days_of_activity",
      "days_of_activity30d",
      "user_type",
      "account_type",
      "days_before_deletion",
      "last_activity_date",
      "sessions_count",
      "events_count"
    )

  override val tableName: String = "crm_user"

  lazy val alias: SyntaxProvider[PersistentCrmUser] = syntax("crm_user")

  def apply(
    resultName: ResultName[PersistentCrmUser] = alias.resultName
  )(resultSet: WrappedResultSet): PersistentCrmUser = {
    PersistentCrmUser(
      userId = resultSet.string(resultName.userId),
      email = resultSet.string(resultName.email),
      fullName = resultSet.string(resultName.fullName),
      firstname = resultSet.string(resultName.firstname),
      zipcode = resultSet.stringOpt(resultName.zipcode),
      dateOfBirth = resultSet.stringOpt(resultName.dateOfBirth),
      emailValidationStatus = resultSet.boolean(resultName.emailValidationStatus),
      emailHardbounceStatus = resultSet.boolean(resultName.emailHardbounceStatus),
      unsubscribeStatus = resultSet.boolean(resultName.unsubscribeStatus),
      accountCreationDate = resultSet.stringOpt(resultName.accountCreationDate),
      accountCreationSource = resultSet.stringOpt(resultName.accountCreationSource),
      accountCreationOrigin = resultSet.stringOpt(resultName.accountCreationOrigin),
      accountCreationOperation = resultSet.stringOpt(resultName.accountCreationOperation),
      accountCreationCountry = resultSet.stringOpt(resultName.accountCreationCountry),
      accountCreationLocation = resultSet.stringOpt(resultName.accountCreationLocation),
      countriesActivity = resultSet.stringOpt(resultName.countriesActivity),
      lastCountryActivity = resultSet.stringOpt(resultName.lastCountryActivity),
      totalNumberProposals = resultSet.intOpt(resultName.totalNumberProposals),
      totalNumberVotes = resultSet.intOpt(resultName.totalNumberVotes),
      firstContributionDate = resultSet.stringOpt(resultName.firstContributionDate),
      lastContributionDate = resultSet.stringOpt(resultName.lastContributionDate),
      operationActivity = resultSet.stringOpt(resultName.operationActivity),
      sourceActivity = resultSet.stringOpt(resultName.sourceActivity),
      daysOfActivity = resultSet.intOpt(resultName.daysOfActivity),
      daysOfActivity30d = resultSet.intOpt(resultName.daysOfActivity30d),
      userType = resultSet.stringOpt(resultName.userType),
      accountType = resultSet.stringOpt(resultName.accountType),
      daysBeforeDeletion = resultSet.intOpt(resultName.daysBeforeDeletion),
      lastActivityDate = resultSet.stringOpt(resultName.lastActivityDate),
      sessionsCount = resultSet.intOpt(resultName.sessionsCount),
      eventsCount = resultSet.intOpt(resultName.eventsCount)
    )
  }

}
