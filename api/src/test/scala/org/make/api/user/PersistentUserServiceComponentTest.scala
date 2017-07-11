package org.make.api.user

import org.make.api.MakeUnitTest
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.core.user.Role
import org.mockito.{ArgumentMatchers, Mockito}
import scalikejdbc.WrappedResultSet

import scala.concurrent.ExecutionContext

class PersistentUserServiceComponentTest
    extends MakeUnitTest
    with PersistentUserServiceComponent
    with MakeDBExecutionContextComponent {

  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val readExecutionContext: ExecutionContext = ExecutionContext.Implicits.global
  override val writeExecutionContext: ExecutionContext = ExecutionContext.Implicits.global

  val rs: WrappedResultSet = mock[WrappedResultSet]
  Mockito.when(rs.string(ArgumentMatchers.eq("r_on_u"))).thenReturn("ROLE_ADMIN,ROLE_MODERATOR")

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
