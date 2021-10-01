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

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.{ActorSystemComponent, MakeApi, MakeApiTestBase}
import org.make.core.reference.Country
import org.make.core.technical.Pagination.Start
import org.make.core.user.Role.{RoleCitizen, RoleModerator, RolePolitical}
import org.make.core.user._
import org.make.core.{RequestContext, ValidationError}
import org.mockito.Mockito.clearInvocations

import scala.collection.immutable.Seq
import scala.concurrent.Future

class AdminModeratorApiTest
    extends MakeApiTestBase
    with DefaultAdminModeratorApiComponent
    with UserServiceComponent
    with ActorSystemComponent
    with PersistentUserServiceComponent {

  override val userService: UserService = mock[UserService]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]

  val routes: Route = sealRoute(handleRejections(MakeApi.rejectionHandler) {
    adminModeratorApi.routes
  })

  val moderatorId: UserId = defaultModeratorUser.userId

  when(userService.getUser(eqTo(moderatorId))).thenReturn(Future.successful(Some(defaultModeratorUser)))
  when(userService.getUserByEmail(defaultModeratorUser.email)).thenReturn(Future.successful(Some(defaultModeratorUser)))
  when(userService.update(any[User], any[RequestContext])).thenReturn(Future.successful(defaultModeratorUser))

  Feature("get moderator") {
    Scenario("unauthenticate user unauthorized to get moderator") {
      Get(s"/admin/moderators/${moderatorId.value}") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("citizen forbidden to get moderator") {
      Get(s"/admin/moderators/${moderatorId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("moderator forbidden to get moderator") {
      Get(s"/admin/moderators/${moderatorId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("unexistant moderator") {
      when(userService.getUser(UserId("moderator-fake"))).thenReturn(Future.successful(None))
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/moderators/moderator-fake")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }

    Scenario("found user with no moderator role") {
      when(userService.getUser(eqTo(defaultCitizenUser.userId)))
        .thenReturn(Future.successful(Some(defaultCitizenUser)))
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get(s"/admin/moderators/${defaultCitizenUser.userId.value}")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }

    Scenario("successfully return moderator") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get(s"/admin/moderators/${moderatorId.value}")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val moderator = entityAs[ModeratorResponse]
          moderator.id should be(moderatorId)
        }
      }
    }
  }

  Feature("get moderators") {
    val moderator1 =
      defaultModeratorUser.copy(
        userId = UserId("moderator1-id"),
        email = "moder@ator1.com",
        roles = Seq(Role.RoleModerator)
      )
    val moderator2 =
      defaultModeratorUser.copy(
        userId = UserId("moderator2-id"),
        email = "moder@ator2.com",
        roles = Seq(Role.RoleModerator)
      )
    val admin1 =
      defaultModeratorUser.copy(
        userId = UserId("admin1-id"),
        email = "ad@min1.com",
        roles = Seq(Role.RoleModerator, Role.RoleAdmin)
      )
    val listModerator = Seq(moderator1, moderator2, admin1)

    when(
      userService.adminCountUsers(
        ids = None,
        email = None,
        firstName = None,
        lastName = None,
        role = Some(Role.RoleModerator),
        userType = None
      )
    ).thenReturn(Future.successful(listModerator.size))

    Scenario("unauthenticate user unauthorized to get moderator") {
      Get("/admin/moderators") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("citizen forbidden to get moderator") {
      Get("/admin/moderators")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("moderator forbidden to get moderator") {
      Get("/admin/moderators")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("get all moderators") {
      when(
        userService
          .adminFindUsers(
            start = Start.zero,
            end = None,
            sort = None,
            order = None,
            ids = None,
            email = None,
            firstName = None,
            lastName = None,
            role = Some(Role.RoleModerator),
            userType = None
          )
      ).thenReturn(Future.successful(listModerator))
      when(
        userService.adminCountUsers(
          ids = None,
          email = None,
          firstName = None,
          lastName = None,
          role = Some(Role.RoleModerator),
          userType = None
        )
      ).thenReturn(Future.successful(listModerator.size))
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/moderators")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val moderators = entityAs[Seq[ModeratorResponse]]
          moderators.size should be(listModerator.size)
        }
      }
    }
  }

  Feature("create moderator") {

    Scenario("moderator forbidden to create moderator") {
      Post("/admin/moderators")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("citizen forbidden to create moderator") {
      Post("/admin/moderators")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("random user unauthorized create moderator") {
      Post("/admin/moderators") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("admin successfully create moderator") {
      when(userService.register(any[UserRegisterData], any[RequestContext]))
        .thenReturn(Future.successful(defaultModeratorUser))
      val request =
        s"""{
          |  "email": "${defaultModeratorUser.email}",
          |  "firstName": "Mod",
          |  "lastName": "Erator",
          |  "roles": ["ROLE_MODERATOR", "ROLE_POLITICAL"],
          |  "country": "FR",
          |  "language": "fr",
          |  "availableQuestions": []
          |}
        """.stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        clearInvocations(userService)
        Post("/admin/moderators", HttpEntity(ContentTypes.`application/json`, request))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.Created)
          verify(userService).register(argThat[UserRegisterData] { data =>
            data.email == s"${defaultModeratorUser.email}" &&
            data.firstName.contains("Mod") &&
            data.lastName.contains("Erator") &&
            data.password.isEmpty &&
            data.lastIp.isEmpty &&
            data.dateOfBirth.isEmpty &&
            data.profession.isEmpty &&
            data.postalCode.isEmpty &&
            data.country == Country("FR") &&
            data.gender.isEmpty &&
            data.socioProfessionalCategory.isEmpty &&
            data.optIn.contains(false) &&
            data.optInPartner.contains(false) &&
            data.questionId.isEmpty
          }, any[RequestContext])
        }
      }
    }

    Scenario("validation failed for existing email") {
      when(userService.register(any[UserRegisterData], any[RequestContext]))
        .thenReturn(Future.failed(EmailAlreadyRegisteredException(defaultModeratorUser.email)))

      val request =
        s"""{
          |  "email": "${defaultModeratorUser.email}",
          |  "firstName": "Mod",
          |  "lastName": "Erator",
          |  "roles": ["ROLE_MODERATOR", "ROLE_POLITICAL"],
          |  "country": "FR",
          |  "language": "fr",
          |  "availableQuestions": []
          |}
        """.stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/moderators", HttpEntity(ContentTypes.`application/json`, request))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          val emailError = errors.find(_.field == "email")
          emailError should be(
            Some(
              ValidationError("email", "already_registered", Some(s"Email ${defaultModeratorUser.email} already exist"))
            )
          )
        }
      }
    }

    Scenario("validation failed for missing country") {
      val request =
        s"""{
          |  "email": "${defaultModeratorUser.email}",
          |  "firstName": "Mod",
          |  "lastName": "Erator",
          |  "roles": ["ROLE_MODERATOR", "ROLE_POLITICAL"],
          |  "availableQuestions": []
          |}
        """.stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/moderators", HttpEntity(ContentTypes.`application/json`, request))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          val countryError = errors.find(_.field == "country")
          countryError should be(
            Some(ValidationError("country", "malformed", Some("The field [.country] is missing.")))
          )
        }
      }
    }

    Scenario("validation failed for malformed email") {
      val request =
        """{
          |  "email": "mod.erator",
          |  "firstName": "Mod",
          |  "lastName": "Erator",
          |  "roles": ["ROLE_MODERATOR", "ROLE_POLITICAL"],
          |  "country": "FR",
          |  "language": "fr",
          |  "availableQuestions": []
          |}
        """.stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/moderators", HttpEntity(ContentTypes.`application/json`, request))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          val emailError = errors.find(_.field == "email")
          emailError should be(Some(ValidationError("email", "invalid_email", Some("email is not a valid email"))))
        }
      }
    }

    Scenario("validation failed for invalid roles") {
      val request =
        s"""{
          |  "email": "${defaultModeratorUser.email}",
          |  "firstName": "Mod",
          |  "lastName": "Erator",
          |  "roles": "foo",
          |  "country": "FR",
          |  "language": "fr",
          |  "availableQuestions": []
          |}
        """.stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/moderators", HttpEntity(ContentTypes.`application/json`, request))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          val rolesError = errors.find(_.field == "roles")
          rolesError.isDefined should be(true)
          rolesError.map(_.field) should be(Some("roles"))
        }
      }
    }
  }

  Feature("update moderator") {

    Scenario("citizen forbidden to update moderator") {
      Put(s"/admin/moderators/${moderatorId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("random user unauthorized update moderator") {
      Put(s"/admin/moderators/${moderatorId.value}") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("moderator allowed to update itself") {
      Put(s"/admin/moderators/${moderatorId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("admin successfully update moderator") {
      val request =
        s"""{
          |  "email": "${defaultModeratorUser.email}",
          |  "firstName": "New Mod",
          |  "lastName": "New Erator",
          |  "roles": ["ROLE_MODERATOR", "ROLE_POLITICAL", "ROLE_CITIZEN"],
          |  "country": "GB",
          |  "language": "en",
          |  "availableQuestions": []
          |}
        """.stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        clearInvocations(userService)
        Put(s"/admin/moderators/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)

          verify(userService)
            .update(argThat[User] { user: User =>
              user.userId == moderatorId &&
              user.email == s"${defaultModeratorUser.email}" &&
              user.firstName.contains("New Mod") &&
              user.lastName.contains("New Erator") &&
              user.lastIp.isEmpty &&
              user.hashedPassword.isEmpty &&
              user.roles == Seq(RoleModerator, RolePolitical, RoleCitizen) &&
              user.country == Country("GB")
            }, any[RequestContext])
        }
      }
    }

    Scenario("failed because email exists") {
      when(userService.getUserByEmail("toto@modo.com"))
        .thenReturn(
          Future.successful(Some(defaultModeratorUser.copy(userId = UserId("other"), email = "toto@modo.com")))
        )
      val request =
        """{
          |  "email": "toto@modo.com",
          |  "firstName": "New Mod",
          |  "lastName": "New Erator",
          |  "roles": ["ROLE_MODERATOR", "ROLE_POLITICAL", "ROLE_CITIZEN"],
          |  "country": "GB",
          |  "language": "en",
          |  "availableQuestions": []
          |}
        """.stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put(s"/admin/moderators/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          val emailError = errors.find(_.field == "email")
          emailError should be(
            Some(ValidationError("email", "already_registered", Some("Email toto@modo.com already exists")))
          )
        }
      }
    }

    Scenario("failed because new email is invalid") {
      val request =
        """{
          |  "email": "toto@modo",
          |  "firstName": "New Mod",
          |  "lastName": "New Erator",
          |  "roles": ["ROLE_MODERATOR", "ROLE_POLITICAL", "ROLE_CITIZEN"],
          |  "country": "GB",
          |  "language": "en",
          |  "availableQuestions": []
          |}
        """.stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put(s"/admin/moderators/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          val emailError = errors.find(_.field == "email")
          emailError should be(Some(ValidationError("email", "invalid_email", Some("email is not a valid email"))))
        }
      }
    }

    Scenario("moderator tries to change roles") {
      val request =
        s"""{
          |  "email": "${defaultModeratorUser.email}",
          |  "firstName": "New Mod",
          |  "lastName": "New Erator",
          |  "roles": ["ROLE_MODERATOR", "ROLE_CITIZEN", "ROLE_POLITICAL", "ROLE_ADMIN"],
          |  "country": "GB",
          |  "language": "en",
          |  "availableQuestions": []
          |}
        """.stripMargin

      Put(s"/admin/moderators/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("validation failed for invalid roles") {
      val request =
        s"""{
          |  "email": "${defaultModeratorUser.email}",
          |  "firstName": "New Mod",
          |  "lastName": "New Erator",
          |  "roles": "foo",
          |  "country": "GB",
          |  "language": "en",
          |  "availableQuestions": []
          |}
        """.stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put(s"/admin/moderators/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          val rolesError = errors.find(_.field == "roles")
          rolesError.isDefined should be(true)
          rolesError.map(_.field) should be(Some("roles"))
        }
      }
    }

  }

}
