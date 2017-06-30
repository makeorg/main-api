package org.make.api.user

import org.make.core.user.Role
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, GivenWhenThen, Matchers}
import scalikejdbc.WrappedResultSet

import scala.concurrent.ExecutionContext

class PersistentUserServiceComponentTest
    extends FlatSpec
    with GivenWhenThen
    with PersistentUserServiceComponent
    with MockitoSugar
    with Matchers {

  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val readExecutionContext: ExecutionContext = ExecutionContext.Implicits.global
  override val writeExecutionContext: ExecutionContext = ExecutionContext.Implicits.global

  "toUser" should "return roles" in {
    Given("a WrappedResultSet with a string column role")
    val rs = mock[WrappedResultSet]
    Mockito.when(rs.string(ArgumentMatchers.eq("roles"))).thenReturn("ROLE_ADMIN,ROLE_MODERATOR")
    When("transformed to user")
    val user = PersistentUser.toUser(rs)
    Then("Role objects are returned")
    user.roles should be(Seq[Role](Role.RoleAdmin, Role.RoleModerator))
  }

  "toUser" should "not fail to return roles when invalid roles" in {
    Given("a WrappedResultSet with a string column role")
    val rs = mock[WrappedResultSet]
    Mockito.when(rs.string(ArgumentMatchers.eq("roles"))).thenReturn("Faulty_role")
    When("transformed to user")
    val user = PersistentUser.toUser(rs)
    Then("Role must be empty")
    user.roles should be(empty)
  }

  "toProfilel" should "return a consistent Profile" in {
    Given("a partially filled Profile")
    val rs = mock[WrappedResultSet]
    val exampleUrl = "http://example.com/"
    Mockito.when(rs.stringOpt(ArgumentMatchers.eq("avatar_url"))).thenReturn(Some(exampleUrl))
    Mockito.when(rs.intOpt(ArgumentMatchers.eq("karma_level"))).thenReturn(None)
    When("initialized with toProfile")
    val maybeProfile = PersistentUser.toProfile(rs)
    Then("return a Profile with options")
    maybeProfile should not be empty
    maybeProfile.get.avatarUrl should be(Some(exampleUrl))
    maybeProfile.get.karmaLevel should be(None)
  }
}
