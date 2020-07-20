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

import org.make.api.MakeUnitTest
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.user.PersistentUserServiceComponent.{FollowedUsers, PersistentUser}
import org.make.core.DateHelper
import org.make.core.user.{CustomRole, Role}
import scalikejdbc.WrappedResultSet

import scala.concurrent.ExecutionContext

class PersistentUserServiceComponentTest
    extends MakeUnitTest
    with DefaultPersistentUserServiceComponent
    with MakeDBExecutionContextComponent {

  override val readExecutionContext: ExecutionContext = ExecutionContext.Implicits.global
  override val writeExecutionContext: ExecutionContext = ExecutionContext.Implicits.global

  val rs: WrappedResultSet = mock[WrappedResultSet]
  val roles: String = PersistentUser.alias.resultName.roles.value
  when(rs.string(eqTo(roles))).thenReturn("ROLE_ADMIN,ROLE_MODERATOR")
  when(rs.stringOpt(any[String])).thenReturn(None)
  when(rs.arrayOpt(any[String])).thenReturn(None)
  when(rs.zonedDateTimeOpt(any[String])).thenReturn(None)

  Feature("PersistentUser to User") {
    Scenario("User should have roles") {
      Given("a WrappedResultSet with a string column role")

      When("transformed to user")
      val user = PersistentUser.apply()(rs).toUser

      Then("Role objects are returned")
      user.roles should be(Seq[Role](Role.RoleAdmin, Role.RoleModerator))
    }

    Scenario("User should not fail to return roles when custom roles") {
      Given("a WrappedResultSet with a faulty role")
      when(rs.string(eqTo(roles))).thenReturn("faulty_role")

      When("transformed to user")
      val user = PersistentUser.apply()(rs).toUser

      Then("Role must be custom")
      user.roles should be(Seq(CustomRole("faulty_role")))
    }

    Scenario("User's Profile should be consistent") {
      val exampleUrl = "http://example.com/"

      Given("""a partially filled Profile with:
          |    - avatar_url: http://example.com/
          |    - karma_level: None
          |    """.stripMargin)

      val avatarUrl = PersistentUser.alias.resultName.avatarUrl.value
      val karmaLevel = PersistentUser.alias.resultName.karmaLevel.value

      when(rs.stringOpt(eqTo(avatarUrl))).thenReturn(Some(exampleUrl))
      when(rs.intOpt(eqTo(karmaLevel))).thenReturn(None)

      When("transformed to user")
      val maybeProfile = PersistentUser.apply()(rs).toUser.profile

      Then("User's Profile is not empty")
      maybeProfile should not be empty
      And("User's Profile avatarUrl must be http://example.com/")
      maybeProfile.get.avatarUrl should be(Some(exampleUrl))
      And("User's Profile karmaLevel must be None")
      maybeProfile.get.karmaLevel should be(None)
    }

  }

  Feature("user followed") {
    Scenario("user followed") {

      val now = DateHelper.now()

      val userId = FollowedUsers.followedUsersAlias.resultName.userId.value
      val followedUserId = FollowedUsers.followedUsersAlias.resultName.followedUserId.value
      val date = FollowedUsers.followedUsersAlias.resultName.date.value

      when(rs.string(eqTo(userId))).thenReturn("user-id")
      when(rs.string(eqTo(followedUserId))).thenReturn("followed-user-id")
      when(rs.zonedDateTime(eqTo(date)))
        .thenReturn(now)

      val userFollowed = FollowedUsers.apply()(rs)

      userFollowed.userId shouldBe "user-id"
      userFollowed.followedUserId shouldBe "followed-user-id"
      userFollowed.date shouldBe now
    }
  }
}
