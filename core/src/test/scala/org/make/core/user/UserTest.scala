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

package org.make.core.user

import java.time.ZonedDateTime

import org.make.core.reference.{Country, Language}
import org.make.core.user.Role.{RoleActor, RoleAdmin, RoleCitizen, RoleModerator, RolePolitical}
import org.scalatest._
import org.scalatest.mockito.MockitoSugar

class UserTest extends FeatureSpec with GivenWhenThen with MockitoSugar with Matchers {
  val before: ZonedDateTime = ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]")
  val johnDoe = User(
    userId = UserId("1"),
    email = "doe@example.com",
    firstName = Some("John"),
    lastName = Some("Doe"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    enabled = true,
    emailVerified = true,
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleAdmin, Role.RoleCitizen),
    country = Country("FR"),
    language = Language("fr"),
    profile = None
  )

  feature("parse a Role from a String") {
    scenario("pass ROLE_ADMIN string to matchRole function") {
      Given("a Role as a string")
      When("call matchRole with ROLE_ADMIN as Role string")
      val role = Role.matchRole("ROLE_ADMIN")
      Then("Role object are returned")
      role shouldBe Some(RoleAdmin)
    }

    scenario("pass ROLE_MODERATOR string to matchRole function") {
      Given("a Role as a string")
      When("call matchRole with ROLE_MODERATOR as Role string")
      val role = Role.matchRole("ROLE_MODERATOR")
      Then("Role object are returned")
      role shouldBe Some(RoleModerator)
    }

    scenario("pass ROLE_POLITICAL string to matchRole function") {
      Given("a Role as a string")
      When("call matchRole with ROLE_POLITICAL as Role string")
      val role = Role.matchRole("ROLE_POLITICAL")
      Then("Role object are returned")
      role shouldBe Some(RolePolitical)
    }

    scenario("pass ROLE_CITIZEN string to matchRole function") {
      Given("a Role as a string")
      When("call matchRole with ROLE_CITIZEN as Role string")
      val role = Role.matchRole("ROLE_CITIZEN")
      Then("Role object are returned")
      role shouldBe Some(RoleCitizen)
    }

    scenario("pass ROLE_ACTOR string to matchRole function") {
      Given("a Role as a string")
      When("call matchRole with ROLE_ACTOR as Role string")
      val role = Role.matchRole("ROLE_ACTOR")
      Then("Role object are returned")
      role shouldBe Some(RoleActor)
    }
  }

  feature("get a user full name") {
    scenario("user with empty first name, empty last name and empty organisation name") {
      Given("a user with empty first name, empty last name and empty organisation name")
      val userWithoutFirstnameAndLastName = johnDoe.copy(firstName = None, lastName = None, organisationName = None)
      When("I get the full name")
      val fullName = userWithoutFirstnameAndLastName.fullName
      Then("result is None")
      fullName shouldBe None
    }

    scenario("user with empty first name and last name and non empty organisation name") {
      Given("a user with empty first name and last name and non empty organisation name")
      val userWithOrganisationName =
        johnDoe.copy(firstName = None, lastName = None, organisationName = Some("John Doe Corp."))
      When("I get the full name")
      val fullName = userWithOrganisationName.fullName
      fullName shouldBe Some("John Doe Corp.")
    }

    scenario("user with non empty first name and empty last name") {
      Given("a user with a first name John and empty last name")
      val userWithoutFirstnameAndLastName = johnDoe.copy(lastName = None)
      When("I get the full name")
      val fullName = userWithoutFirstnameAndLastName.fullName
      Then("result is John")
      fullName shouldBe Some("John")
    }

    scenario("user with empty first name and non empty last name") {
      Given("a user with a last name Doe and empty first name")
      val userWithoutFirstnameAndLastName = johnDoe.copy(firstName = None)
      When("I get the full name")
      val fullName = userWithoutFirstnameAndLastName.fullName
      Then("result is Doe")
      fullName shouldBe Some("Doe")
    }

    scenario("user with a first name and a last name") {
      Given("a user with a last name Doe and a first name John")
      When("I get the full name")
      val fullName = johnDoe.fullName
      Then("result is John Doe")
      fullName shouldBe Some("John Doe")
    }
  }
}
