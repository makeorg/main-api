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
import org.make.api.user.PersistentUserServiceComponent.PersistentUser
import org.make.core.user.Role
import org.mockito.{ArgumentMatchers, Mockito}
import scalikejdbc.WrappedResultSet

import scala.concurrent.ExecutionContext

class PersistentUserServiceComponentTest
    extends MakeUnitTest
    with DefaultPersistentUserServiceComponent
    with MakeDBExecutionContextComponent {

  override val readExecutionContext: ExecutionContext = ExecutionContext.Implicits.global
  override val writeExecutionContext: ExecutionContext = ExecutionContext.Implicits.global

  val rs: WrappedResultSet = mock[WrappedResultSet]
  Mockito.when(rs.string(ArgumentMatchers.eq("r_on_u"))).thenReturn("ROLE_ADMIN,ROLE_MODERATOR")
  Mockito.when(rs.stringOpt(ArgumentMatchers.any[String])).thenReturn(None)
  Mockito.when(rs.zonedDateTimeOpt(ArgumentMatchers.any[String])).thenReturn(None)

  feature("PersistentUser to User") {
    scenario("User should have roles") {
      Given("a WrappedResultSet with a string column role")

      When("transformed to user")
      val user = PersistentUser.apply()(rs).toUser

      Then("Role objects are returned")
      user.roles should be(Seq[Role](Role.RoleAdmin, Role.RoleModerator))
    }

    scenario("User should not fail to return roles when invalid roles") {
      Given("a WrappedResultSet with a faulty role")
      Mockito.when(rs.string(ArgumentMatchers.eq("r_on_u"))).thenReturn("faulty_role")

      When("transformed to user")
      val user = PersistentUser.apply()(rs).toUser

      Then("Role must be empty")
      user.roles should be(empty)
    }

    scenario("User's Profile should be consistent") {
      val exampleUrl = "http://example.com/"

      Given("""a partially filled Profile with:
          |    - avatar_url: http://example.com/
          |    - karma_level: None
          |    """.stripMargin)
      Mockito.when(rs.stringOpt(ArgumentMatchers.eq("au_on_u"))).thenReturn(Some(exampleUrl))
      Mockito.when(rs.intOpt(ArgumentMatchers.eq("kl_on_u"))).thenReturn(None)

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
}
