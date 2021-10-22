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

import scala.concurrent.Future

trait PersistentCrmUserServiceComponent {
  def persistentCrmUserService: PersistentCrmUserService
}

trait PersistentCrmUserService {

  def persist(users: Seq[PersistentCrmUser]): Future[Seq[PersistentCrmUser]]
  def list(
    unsubscribed: Option[Boolean],
    hardBounced: Boolean,
    page: Int,
    numberPerPage: Int
  ): Future[Seq[PersistentCrmUser]]
  def findInactiveUsers(offset: Int, numberPerPage: Int): Future[Seq[PersistentCrmUser]]
  def truncateCrmUsers(): Future[Unit]

}

final case class PersistentCrmUser(
  userId: String,
  email: String,
  fullName: String,
  firstname: String,
  zipcode: Option[String],
  dateOfBirth: Option[String],
  emailValidationStatus: Boolean,
  emailHardbounceStatus: Boolean,
  unsubscribeStatus: Boolean,
  accountCreationDate: Option[String],
  accountCreationSource: Option[String],
  accountCreationOrigin: Option[String],
  accountCreationOperation: Option[String],
  accountCreationCountry: Option[String],
  accountCreationLocation: Option[String],
  countriesActivity: Option[String],
  lastCountryActivity: Option[String],
  totalNumberProposals: Option[Int],
  totalNumberVotes: Option[Int],
  firstContributionDate: Option[String],
  lastContributionDate: Option[String],
  operationActivity: Option[String],
  sourceActivity: Option[String],
  daysOfActivity: Option[Int],
  daysOfActivity30d: Option[Int],
  userType: Option[String],
  accountType: Option[String],
  daysBeforeDeletion: Option[Int],
  lastActivityDate: Option[String],
  sessionsCount: Option[Int],
  eventsCount: Option[Int]
)
