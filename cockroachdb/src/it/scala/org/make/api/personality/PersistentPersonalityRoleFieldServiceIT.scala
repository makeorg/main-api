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
import org.make.core.personality.FieldType.StringType
import org.make.core.personality.{PersonalityRole, PersonalityRoleField, PersonalityRoleFieldId, PersonalityRoleId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import org.make.core.technical.Pagination.Start

class PersistentPersonalityRoleFieldServiceIT
    extends DatabaseTest
    with DefaultPersistentPersonalityRoleFieldServiceComponent
    with DefaultPersistentPersonalityRoleServiceComponent {

  val personalityRoleField: PersonalityRoleField =
    PersonalityRoleField(
      personalityRoleFieldId = PersonalityRoleFieldId("string-field"),
      personalityRoleId = PersonalityRoleId("candidate"),
      name = "String field",
      fieldType = StringType,
      required = true
    )

  Feature("get personality role field by id") {
    Scenario("get existing personality role field") {
      val futurePersonalityRoleField = for {
        _ <- persistentPersonalityRoleService.persist(
          PersonalityRole(PersonalityRoleId("candidate"), name = "CANDIDATE_TEST")
        )
        _ <- persistentPersonalityRoleFieldService.persist(personalityRoleField)
        role <- persistentPersonalityRoleFieldService.getById(
          PersonalityRoleFieldId("string-field"),
          PersonalityRoleId("candidate")
        )
      } yield role

      whenReady(futurePersonalityRoleField, Timeout(2.seconds)) { personalityRoleField =>
        personalityRoleField.map(_.personalityRoleFieldId) should be(Some(PersonalityRoleFieldId("string-field")))
      }
    }

    Scenario("get non existing personality role field") {
      whenReady(
        persistentPersonalityRoleFieldService
          .getById(PersonalityRoleFieldId("not-found"), PersonalityRoleId("candidate")),
        Timeout(2.seconds)
      ) { personalityRoleField =>
        personalityRoleField should be(None)
      }
    }
  }

  Feature("search personality role fields") {
    Scenario("search all") {
      val futurePersonalityRoleFields = for {
        _ <- persistentPersonalityRoleFieldService.persist(
          personalityRoleField
            .copy(personalityRoleFieldId = PersonalityRoleFieldId("int-field"), name = "Int field")
        )
        _ <- persistentPersonalityRoleFieldService.persist(
          personalityRoleField
            .copy(personalityRoleFieldId = PersonalityRoleFieldId("bool-field"), name = "Boolean field")
        )
        personalityRoleFields <- persistentPersonalityRoleFieldService.find(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          maybePersonalityRoleId = None,
          maybeName = None,
          maybeFieldType = None,
          maybeRequired = None
        )
      } yield personalityRoleFields

      whenReady(futurePersonalityRoleFields, Timeout(2.seconds)) { personalityRoleFields =>
        personalityRoleFields.map(_.personalityRoleFieldId) should contain(PersonalityRoleFieldId("string-field"))
        personalityRoleFields.map(_.personalityRoleFieldId) should contain(PersonalityRoleFieldId("int-field"))
        personalityRoleFields.map(_.personalityRoleFieldId) should contain(PersonalityRoleFieldId("bool-field"))
      }
    }

    Scenario("search by name") {
      val futurePersonalityRoleField =
        persistentPersonalityRoleFieldService.find(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          maybePersonalityRoleId = None,
          maybeName = Some("Int field"),
          maybeFieldType = None,
          maybeRequired = None
        )

      whenReady(futurePersonalityRoleField, Timeout(2.seconds)) { personalityRoleField =>
        personalityRoleField.map(_.personalityRoleFieldId) should contain(PersonalityRoleFieldId("int-field"))
      }
    }

  }

  Feature("count personality role fields") {
    Scenario("count") {

      val futurePersonalityRoleFieldCount =
        persistentPersonalityRoleFieldService.count(
          maybePersonalityRoleId = None,
          maybeName = None,
          maybeFieldType = None,
          maybeRequired = None
        )

      whenReady(futurePersonalityRoleFieldCount, Timeout(2.seconds)) { count =>
        count should be.>=(3)
      }
    }

  }

  Feature("update personality role field") {
    Scenario("update existing personality role field") {
      val updatedPersonalityRoleField =
        personalityRoleField.copy(name = "Field updated")

      val futureUpdatedPersonalityRoleField = persistentPersonalityRoleFieldService.modify(updatedPersonalityRoleField)

      whenReady(futureUpdatedPersonalityRoleField, Timeout(2.seconds)) { personalityRoleField =>
        personalityRoleField.personalityRoleFieldId should be(PersonalityRoleFieldId("string-field"))
        personalityRoleField.name should be("Field updated")
      }
    }
  }

}
