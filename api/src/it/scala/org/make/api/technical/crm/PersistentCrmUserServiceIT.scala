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

import org.make.api.DatabaseTest
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import scala.concurrent.duration.DurationInt

class PersistentCrmUserServiceIT extends DatabaseTest with DefaultPersistentCrmUserServiceComponent {
  val defaultUser: PersistentCrmUser = PersistentCrmUser(
    userId = "test-crm-user",
    fullName = "Toto la Carotte",
    email = "test@make.org",
    firstname = "the user",
    zipcode = Some("12345"),
    dateOfBirth = Some("1970-01-01"),
    emailHardbounceStatus = false,
    emailValidationStatus = false,
    unsubscribeStatus = false,
    accountCreationCountry = Some("FR"),
    accountCreationDate = Some("2019-07-01T16:16:16Z"),
    accountCreationOperation = Some("weeuropeans-fr"),
    accountCreationOrigin = Some("origin"),
    accountCreationSource = Some("source"),
    accountCreationLocation = Some("location"),
    countriesActivity = Some("FR,DE"),
    lastCountryActivity = Some("FR"),
    totalNumberProposals = Some(5),
    totalNumberVotes = Some(42),
    firstContributionDate = Some("2019-07-01T16:16:16Z"),
    lastContributionDate = Some("2019-07-01T16:16:16Z"),
    operationActivity = Some("weeuropeans-fr,weeuropeans-de"),
    sourceActivity = Some("source"),
    daysOfActivity = Some(20),
    daysOfActivity30d = Some(2),
    userType = Some("B2C"),
    accountType = Some("USER"),
    daysBeforeDeletion = Some(3),
    lastActivityDate = Some("2019-07-01T16:16:16Z"),
    sessionsCount = Some(42),
    eventsCount = Some(124)
  )

  Feature("persist crm users") {
    Scenario("persist a user") {

      whenReady(persistentCrmUserService.persist(Seq(defaultUser)), Timeout(5.seconds)) { _ =>
        ()
      }

      whenReady(
        persistentCrmUserService
          .list(maybeUnsubscribed = Some(false), hardBounce = false, offset = 0, numberPerPage = 1000),
        Timeout(5.seconds)
      ) { users =>
        val user = users.find(_.userId == defaultUser.userId)
        user should contain(defaultUser)
      }
    }
  }

  Feature("truncate table") {
    Scenario("truncate table") {

      whenReady(persistentCrmUserService.persist(Seq(defaultUser.copy(userId = "truncate table"))), Timeout(5.seconds)) {
        _ =>
          ()
      }

      whenReady(
        persistentCrmUserService
          .list(maybeUnsubscribed = Some(false), hardBounce = false, offset = 0, numberPerPage = 1000),
        Timeout(5.seconds)
      ) { users =>
        val user = users.find(_.userId == "truncate table")
        user should be(defined)
      }

      whenReady(persistentCrmUserService.truncateCrmUsers(), Timeout(5.seconds)) { _ =>
        ()
      }

      whenReady(
        persistentCrmUserService
          .list(maybeUnsubscribed = Some(false), hardBounce = false, offset = 0, numberPerPage = 1000),
        Timeout(5.seconds)
      ) { users =>
        val user = users.find(_.userId == "truncate table")
        user should be(empty)
      }
    }
  }

  Feature("list users") {
    Scenario("list users") {

      val insertUsers =
        persistentCrmUserService.persist(
          Seq(
            defaultUser.copy(
              userId = "inserted-1",
              emailHardbounceStatus = true,
              unsubscribeStatus = true,
              accountCreationDate = Some("2019-07-01T01:01:01Z")
            ),
            defaultUser.copy(
              userId = "inserted-2",
              emailHardbounceStatus = true,
              unsubscribeStatus = true,
              accountCreationDate = Some("2019-07-01T03:03:013")
            ),
            defaultUser.copy(
              userId = "inserted-3",
              emailHardbounceStatus = true,
              unsubscribeStatus = true,
              accountCreationDate = Some("2019-07-01T02:02:02Z")
            ),
            defaultUser.copy(
              userId = "inserted-4",
              emailHardbounceStatus = true,
              unsubscribeStatus = false,
              accountCreationDate = Some("2019-07-01T01:01:01Z")
            ),
            defaultUser.copy(
              userId = "inserted-5",
              emailHardbounceStatus = true,
              unsubscribeStatus = false,
              accountCreationDate = Some("2019-07-01T02:02:02Z")
            )
          )
        )

      whenReady(insertUsers, Timeout(5.seconds)) { _ =>
        ()
      }

      whenReady(
        persistentCrmUserService
          .list(maybeUnsubscribed = Some(true), hardBounce = true, offset = 0, numberPerPage = 1000),
        Timeout(5.seconds)
      ) { results =>
        results.map(_.userId) should be(Seq("inserted-1", "inserted-3", "inserted-2"))
      }

      whenReady(
        persistentCrmUserService
          .list(maybeUnsubscribed = Some(false), hardBounce = true, offset = 0, numberPerPage = 1000),
        Timeout(5.seconds)
      ) { results =>
        results.map(_.userId) should be(Seq("inserted-4", "inserted-5"))
      }

    }

    Scenario("list inactive users") {

      val insertUsers =
        persistentCrmUserService.persist(
          Seq(
            defaultUser.copy(
              userId = "inserted-6",
              emailHardbounceStatus = true,
              unsubscribeStatus = true,
              accountCreationDate = Some("2019-07-01T01:01:01Z"),
              daysBeforeDeletion = Some(0)
            ),
            defaultUser.copy(
              userId = "inserted-7",
              emailHardbounceStatus = true,
              unsubscribeStatus = true,
              accountCreationDate = Some("2019-07-01T03:03:013")
            ),
            defaultUser.copy(
              userId = "inserted-8",
              emailHardbounceStatus = true,
              unsubscribeStatus = true,
              accountCreationDate = Some("2019-07-01T02:02:02Z"),
              daysBeforeDeletion = Some(-42)
            ),
            defaultUser.copy(
              userId = "inserted-9",
              emailHardbounceStatus = true,
              unsubscribeStatus = false,
              accountCreationDate = Some("2019-07-01T01:01:01Z"),
              daysBeforeDeletion = Some(0)
            ),
            defaultUser.copy(
              userId = "inserted-10",
              emailHardbounceStatus = true,
              unsubscribeStatus = false,
              accountCreationDate = Some("2019-07-01T02:02:02Z")
            )
          )
        )

      whenReady(insertUsers, Timeout(5.seconds)) { _ =>
        ()
      }

      whenReady(persistentCrmUserService.findInactiveUsers(0, 5), Timeout(5.seconds)) { results =>
        results.map(_.userId) should be(Seq("inserted-6", "inserted-8", "inserted-9"))
      }

    }
  }
}
