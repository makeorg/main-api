package org.make.core.user

import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator, RolePolitical}
import org.scalatest._
import org.scalatest.mockito.MockitoSugar

class UserTest extends FeatureSpec with GivenWhenThen with MockitoSugar with Matchers {
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
  }
}
