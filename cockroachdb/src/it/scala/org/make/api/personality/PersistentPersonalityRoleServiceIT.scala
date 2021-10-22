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

package org.make.api.personality

import org.make.api.DatabaseTest
import org.make.core.personality.{PersonalityRole, PersonalityRoleId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import org.make.core.technical.Pagination.Start

class PersistentPersonalityRoleServiceIT extends DatabaseTest with DefaultPersistentPersonalityRoleServiceComponent {

  val personalityRole: PersonalityRole =
    PersonalityRole(personalityRoleId = PersonalityRoleId("candidate"), name = "CANDIDATE_TEST")

  Feature("get personality role by id") {
    Scenario("get existing personality role") {
      val futurePersonalityRole = for {
        _ <- persistentPersonalityRoleService.persist(
          PersonalityRole(PersonalityRoleId("candidate"), name = "CANDIDATE_TEST")
        )
        role <- persistentPersonalityRoleService.getById(PersonalityRoleId("candidate"))
      } yield role

      whenReady(futurePersonalityRole, Timeout(2.seconds)) { personalityRole =>
        personalityRole.map(_.personalityRoleId) should be(Some(PersonalityRoleId("candidate")))
      }
    }

    Scenario("get non existing personality role") {
      whenReady(persistentPersonalityRoleService.getById(PersonalityRoleId("not-found")), Timeout(2.seconds)) {
        personalityRole =>
          personalityRole should be(None)
      }
    }
  }

  Feature("search personality roles") {
    Scenario("search all") {
      val futurePersonalityRoles = for {
        _ <- persistentPersonalityRoleService.persist(
          personalityRole.copy(personalityRoleId = PersonalityRoleId("candidate-2"), name = "CANDIDATE_2")
        )
        _ <- persistentPersonalityRoleService.persist(
          personalityRole.copy(personalityRoleId = PersonalityRoleId("candidate-3"), name = "CANDIDATE_3")
        )
        personalityRoles <- persistentPersonalityRoleService.find(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          maybeRoleIds = None,
          maybeName = None
        )
      } yield personalityRoles

      whenReady(futurePersonalityRoles, Timeout(2.seconds)) { personalityRoles =>
        personalityRoles.map(_.personalityRoleId) should contain(PersonalityRoleId("candidate"))
        personalityRoles.map(_.personalityRoleId) should contain(PersonalityRoleId("candidate-2"))
        personalityRoles.map(_.personalityRoleId) should contain(PersonalityRoleId("candidate-3"))
      }
    }

    Scenario("search by name") {
      val futurePersonalityRole =
        persistentPersonalityRoleService.find(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          maybeRoleIds = None,
          maybeName = Some("CANDIDATE_2")
        )

      whenReady(futurePersonalityRole, Timeout(2.seconds)) { personalityRoles =>
        personalityRoles.map(_.personalityRoleId) should contain(PersonalityRoleId("candidate-2"))
      }
    }

    Scenario("search by ids") {
      val futurePersonalityRole =
        persistentPersonalityRoleService.find(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          maybeRoleIds = Some(Seq(PersonalityRoleId("candidate-2"), PersonalityRoleId("candidate-3"))),
          maybeName = None
        )

      whenReady(futurePersonalityRole, Timeout(2.seconds)) { personalityRoles =>
        personalityRoles.size should be(2)
        personalityRoles.map(_.personalityRoleId) should contain(PersonalityRoleId("candidate-2"))
        personalityRoles.map(_.personalityRoleId) should contain(PersonalityRoleId("candidate-3"))
      }
    }

  }

  Feature("count personality roles") {
    Scenario("count") {

      val futurePersonalityRoleCount = persistentPersonalityRoleService.count(maybeRoleIds = None, maybeName = None)

      whenReady(futurePersonalityRoleCount, Timeout(2.seconds)) { count =>
        count should be.>=(3)
      }
    }

  }

  Feature("update personality role") {
    Scenario("update existing personality role") {
      val updatedPersonalityRole =
        personalityRole.copy(name = "CANDIDATE_UPDATED")

      val futureUpdatedPersonalityRole = persistentPersonalityRoleService.modify(updatedPersonalityRole)

      whenReady(futureUpdatedPersonalityRole, Timeout(2.seconds)) { personalityRole =>
        personalityRole.personalityRoleId should be(PersonalityRoleId("candidate"))
        personalityRole.name should be("CANDIDATE_UPDATED")
      }
    }
  }

}
