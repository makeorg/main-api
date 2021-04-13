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

import org.make.api.DatabaseTest
import org.make.api.technical.Futures.RichFutures
import org.make.core.user._
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class PersistentCrmSynchroUserServiceIT
    extends DatabaseTest
    with DefaultPersistentUserServiceComponent
    with DefaultPersistentCrmSynchroUserServiceComponent {

  override protected val cockroachExposedPort: Int = 40026

  Feature("listing users") {
    val usersCount = 50
    Scenario("listing users") {
      Given(s"$usersCount users")
      val insertedUsers = (1 to usersCount).foldLeft(Future.unit) { (acc, i) =>
        val currentUser = user(UserId(s"user-$i"), email = s"test-$i@example.com")
        acc.flatMap { _ =>
          persistentUserService.persist(currentUser)
        }.toUnit
      }
      Await.result(insertedUsers, 5.minutes)

      When("I list them")
      whenReady(
        persistentCrmSynchroUserService.findUsersForCrmSynchro(Some(true), None, 0, usersCount),
        Timeout(5.seconds)
      ) { users =>
        Then("I should have the ones I wanted")
        users.size should be(usersCount)
      }
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
  }
}
