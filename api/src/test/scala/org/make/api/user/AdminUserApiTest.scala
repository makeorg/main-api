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

import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical._
import org.make.api.technical.auth._
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.{ActorSystemComponent, MakeApi, MakeApiTestBase}
import org.make.core.auth.UserRights
import org.make.core.reference.{Country, Language}
import org.make.core.user.Role.{RoleCitizen, RoleModerator, RolePolitical}
import org.make.core.user.{CustomRole, Role, User, UserId}
import org.make.core.{DateHelper, RequestContext, ValidationError}
import org.mockito.ArgumentMatchers.{any, eq => matches}
import org.mockito.Mockito._
import org.mockito.{ArgumentMatchers, Mockito}
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.collection.immutable.Seq
import scala.concurrent.Future

class AdminUserApiTest
    extends MakeApiTestBase
    with DefaultAdminUserApiComponent
    with UserServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with ActorSystemComponent
    with PersistentUserServiceComponent {

  override val userService: UserService = mock[UserService]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]

  val routes: Route = sealRoute(handleRejections(MakeApi.rejectionHandler) { adminUserApi.routes })

  val citizenId = UserId("citizen")
  val moderatorId = UserId("moderator")
  val adminId = UserId("admin")

  val validAccessToken = "my-valid-access-token"
  val moderatorToken = "my-moderator-access-token"
  val adminToken = "my-admin-access-token"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, None, Some(1234567890L), tokenCreationDate)
  private val moderatorAccessToken = AccessToken(moderatorToken, None, None, Some(1234567890L), tokenCreationDate)
  private val adminAccessToken = AccessToken(adminToken, None, None, Some(1234567890L), tokenCreationDate)

  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))
  when(oauth2DataHandler.findAccessToken(moderatorToken)).thenReturn(Future.successful(Some(moderatorAccessToken)))
  when(oauth2DataHandler.findAccessToken(adminToken)).thenReturn(Future.successful(Some(adminAccessToken)))

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(accessToken)))
    .thenReturn(
      Future
        .successful(Some(AuthInfo(UserRights(citizenId, Seq(Role.RoleCitizen), Seq.empty), None, Some("user"), None)))
    )
  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(
      Future
        .successful(
          Some(AuthInfo(UserRights(userId = moderatorId, roles = Seq(Role.RoleModerator), Seq.empty), None, None, None))
        )
    )
  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future.successful(
        Some(AuthInfo(UserRights(userId = adminId, roles = Seq(Role.RoleAdmin), Seq.empty), None, None, None))
      )
    )

  private val newModerator = User(
    userId = moderatorId,
    email = "mod.erator@modo.com",
    firstName = Some("Mod"),
    lastName = Some("Erator"),
    lastIp = None,
    hashedPassword = None,
    enabled = true,
    emailVerified = false,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleModerator),
    country = Country("FR"),
    language = Language("fr"),
    profile = None,
    createdAt = None,
    updatedAt = None,
    lastMailingError = None,
    availableQuestions = Seq.empty
  )

  private val newCitizen = User(
    userId = citizenId,
    email = "cit.izen@make.org",
    firstName = Some("Cit"),
    lastName = Some("Izen"),
    lastIp = None,
    hashedPassword = None,
    enabled = true,
    emailVerified = false,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleCitizen),
    country = Country("FR"),
    language = Language("fr"),
    profile = None,
    createdAt = None,
    updatedAt = None,
    lastMailingError = None,
    availableQuestions = Seq.empty
  )

  feature("get moderator") {
    scenario("unauthenticate user unauthorized to get moderator") {
      Get("/admin/moderators/moderator-id") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("citizen forbidden to get moderator") {
      Get("/admin/moderators/moderator-id")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("moderator forbidden to get moderator") {
      Get("/admin/moderators/moderator-id")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("unexistant moderator") {
      Mockito.when(userService.getUser(ArgumentMatchers.any[UserId])).thenReturn(Future.successful(None))
      Get("/admin/moderators/moderator-id")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("found user with no moderator role") {
      Mockito
        .when(userService.getUser(ArgumentMatchers.eq(moderatorId)))
        .thenReturn(Future.successful(Some(newModerator.copy(roles = Seq(Role.RoleCitizen)))))
      Get("/admin/moderators/moderator")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("successfully return moderator") {
      Mockito
        .when(userService.getUser(ArgumentMatchers.eq(moderatorId)))
        .thenReturn(Future.successful(Some(newModerator)))
      Get("/admin/moderators/moderator")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val moderator = entityAs[ModeratorResponse]
        moderator.id should be(moderatorId)
      }
    }
  }

  feature("get user") {
    scenario("unauthenticate user unauthorized to get user") {
      Get("/admin/users/user-id") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("citizen forbidden to get user") {
      Get("/admin/users/user-id")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("moderator forbidden to get user") {
      Get("/admin/users/user-id")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("unexistant user") {
      Mockito.when(userService.getUser(ArgumentMatchers.any[UserId])).thenReturn(Future.successful(None))
      Get("/admin/users/user-id")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("successfully return user") {
      Mockito
        .when(userService.getUser(ArgumentMatchers.eq(citizenId)))
        .thenReturn(Future.successful(Some(newCitizen)))
      Get("/admin/users/citizen")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val user = entityAs[AdminUserResponse]
        user.id should be(citizenId)
      }
    }
  }

  feature("get moderators") {

    val moderator1 =
      newModerator.copy(userId = UserId("moderator1-id"), email = "moder@ator1.com", roles = Seq(Role.RoleModerator))
    val moderator2 =
      newModerator.copy(userId = UserId("moderator2-id"), email = "moder@ator2.com", roles = Seq(Role.RoleModerator))
    val admin1 =
      newModerator.copy(
        userId = UserId("admin1-id"),
        email = "ad@min1.com",
        roles = Seq(Role.RoleModerator, Role.RoleAdmin)
      )
    val listModerator = Seq(moderator1, moderator2, admin1)

    Mockito
      .when(userService.adminCountUsers(None, None, Some(Role.RoleModerator)))
      .thenReturn(Future.successful(listModerator.size))

    scenario("unauthenticate user unauthorized to get moderator") {
      Get("/admin/moderators") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("citizen forbidden to get moderator") {
      Get("/admin/moderators")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("moderator forbidden to get moderator") {
      Get("/admin/moderators")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("get all moderators") {
      Mockito
        .when(userService.adminFindUsers(0, None, None, None, None, None, Some(Role.RoleModerator)))
        .thenReturn(Future.successful(listModerator))
      Get("/admin/moderators")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val moderators = entityAs[Seq[ModeratorResponse]]
        moderators.size should be(listModerator.size)
      }
    }
  }

  feature("create moderator") {

    scenario("moderator forbidden to create moderator") {
      Post("/admin/moderators")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("citizen forbidden to create moderator") {
      Post("/admin/moderators")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("random user unauthorized create moderator") {
      Post("/admin/moderators") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("admin successfully create moderator") {
      Mockito
        .when(userService.register(any[UserRegisterData], any[RequestContext]))
        .thenReturn(Future.successful(newModerator))
      val request =
        """{
          |  "email": "mod.erator@modo.com",
          |  "firstName": "Mod",
          |  "lastName": "Erator",
          |  "roles": ["ROLE_MODERATOR", "ROLE_POLITICAL"],
          |  "country": "FR",
          |  "language": "fr",
          |  "availableQuestions": []
          |}
        """.stripMargin

      Post("/admin/moderators", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
        verify(userService).register(
          matches(
            UserRegisterData(
              email = "mod.erator@modo.com",
              firstName = Some("Mod"),
              lastName = Some("Erator"),
              password = None,
              lastIp = None,
              dateOfBirth = None,
              profession = None,
              postalCode = None,
              country = Country("FR"),
              language = Language("fr"),
              gender = None,
              socioProfessionalCategory = None,
              optIn = Some(false),
              optInPartner = Some(false),
              questionId = None,
              roles = Seq(Role.RoleModerator, Role.RolePolitical)
            )
          ),
          any[RequestContext]
        )
      }
    }

    scenario("validation failed for existing email") {
      Mockito
        .when(userService.register(any[UserRegisterData], any[RequestContext]))
        .thenReturn(Future.failed(EmailAlreadyRegisteredException("mod.erator@modo.com")))

      val request =
        """{
          |  "email": "mod.erator@modo.com",
          |  "firstName": "Mod",
          |  "lastName": "Erator",
          |  "roles": ["ROLE_MODERATOR", "ROLE_POLITICAL"],
          |  "country": "FR",
          |  "language": "fr",
          |  "availableQuestions": []
          |}
        """.stripMargin

      Post("/admin/moderators", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(Some(ValidationError("email", Some("Email mod.erator@modo.com already exist"))))
      }
    }

    scenario("validation failed for missing country and/or language") {
      val request =
        """{
          |  "email": "mod.erator@modo.com",
          |  "firstName": "Mod",
          |  "lastName": "Erator",
          |  "roles": ["ROLE_MODERATOR", "ROLE_POLITICAL"],
          |  "availableQuestions": []
          |}
        """.stripMargin

      Post("/admin/moderators", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val countryError = errors.find(_.field == "country")
        countryError should be(Some(ValidationError("country", Some("The field [.country] is missing."))))
      }
    }

    scenario("validation failed for malformed email") {
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

      Post("/admin/moderators", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(Some(ValidationError("email", Some("email is not a valid email"))))
      }
    }

    scenario("validation failed for invalid roles") {
      val request =
        """{
          |  "email": "mod.erator@modo.com",
          |  "firstName": "Mod",
          |  "lastName": "Erator",
          |  "roles": "foo",
          |  "country": "FR",
          |  "language": "fr",
          |  "availableQuestions": []
          |}
        """.stripMargin

      Post("/admin/moderators", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val rolesError = errors.find(_.field == "roles")
        rolesError.isDefined should be(true)
        rolesError.map(_.field) should be(Some("roles"))
      }
    }
  }

  feature("update moderator") {

    scenario("citizen forbidden to update moderator") {
      Put(s"/admin/moderators/${moderatorId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("random user unauthorized update moderator") {
      Put(s"/admin/moderators/${moderatorId.value}") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Mockito.when(userService.getUser(moderatorId)).thenReturn(Future.successful(Some(newModerator)))
    Mockito.when(userService.getUserByEmail(any[String])).thenReturn(Future.successful(None))

    scenario("moderator allowed to update itself") {
      Put(s"/admin/moderators/${moderatorId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("admin successfully update moderator") {
      Mockito.when(userService.getUser(moderatorId)).thenReturn(Future.successful(Some(newModerator)))
      Mockito.when(userService.update(any[User], any[RequestContext])).thenReturn(Future.successful(newModerator))
      val request =
        """{
          |  "email": "mod.erator@modo.com",
          |  "firstName": "New Mod",
          |  "lastName": "New Erator",
          |  "roles": ["ROLE_MODERATOR", "ROLE_POLITICAL", "ROLE_CITIZEN"],
          |  "country": "GB",
          |  "language": "en",
          |  "availableQuestions": []
          |}
        """.stripMargin

      Put(s"/admin/moderators/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)

        verify(userService)
          .update(ArgumentMatchers.argThat[User] { user: User =>
            user.userId == moderatorId &&
            user.email == "mod.erator@modo.com" &&
            user.firstName.contains("New Mod") &&
            user.lastName.contains("New Erator") &&
            user.lastIp.isEmpty &&
            user.hashedPassword.isEmpty &&
            user.roles == Seq(RoleModerator, RolePolitical, RoleCitizen) &&
            user.country == Country("GB") &&
            user.language == Language("en")
          }, any[RequestContext])
      }
    }

    scenario("failed because email exists") {
      Mockito.when(userService.getUser(moderatorId)).thenReturn(Future.successful(Some(newModerator)))
      Mockito.when(userService.update(any[User], any[RequestContext])).thenReturn(Future.successful(newModerator))
      Mockito
        .when(userService.getUserByEmail(any[String]))
        .thenReturn(Future.successful(Some(newModerator.copy(userId = UserId("other")))))
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

      Put(s"/admin/moderators/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(Some(ValidationError("email", Some("Email toto@modo.com already exists"))))
      }
    }

    scenario("failed because new email is invalid") {
      Mockito.when(userService.update(any[User], any[RequestContext])).thenReturn(Future.successful(newModerator))
      Mockito.when(userService.getUserByEmail(any[String])).thenReturn(Future.successful(None))
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

      Put(s"/admin/moderators/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(Some(ValidationError("email", Some("email is not a valid email"))))
      }
    }

    scenario("moderator tries to change roles") {
      Mockito.when(userService.getUser(moderatorId)).thenReturn(Future.successful(Some(newModerator)))
      Mockito.when(userService.update(any[User], any[RequestContext])).thenReturn(Future.successful(newModerator))
      val request =
        """{
          |  "email": "mod.erator@modo.com",
          |  "firstName": "New Mod",
          |  "lastName": "New Erator",
          |  "roles": ["ROLE_MODERATOR", "ROLE_CITIZEN", "ROLE_POLITICAL", "ROLE_ADMIN"],
          |  "country": "GB",
          |  "language": "en",
          |  "availableQuestions": []
          |}
        """.stripMargin

      Put(s"/admin/moderators/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("validation failed for invalid roles") {
      val request =
        """{
          |  "email": "mod.erator@modo.com",
          |  "firstName": "New Mod",
          |  "lastName": "New Erator",
          |  "roles": "foo",
          |  "country": "GB",
          |  "language": "en",
          |  "availableQuestions": []
          |}
        """.stripMargin

      Put(s"/admin/moderators/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val rolesError = errors.find(_.field == "roles")
        rolesError.isDefined should be(true)
        rolesError.map(_.field) should be(Some("roles"))
      }
    }

  }

  feature("anonymize user by id") {
    scenario("unauthenticated user") {
      Delete("/admin/users/user-id") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("citizen user") {
      Delete("/admin/users/user-id")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("moderator user") {
      Delete("/admin/users/user-id")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("admin user") {
      Delete(s"/admin/users/$moderatorId")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        Mockito.when(userService.getUser(moderatorId)).thenReturn(Future.successful(Some(newModerator)))
        Mockito
          .when(userService.anonymize(newModerator, adminId, RequestContext.empty))
          .thenReturn(Future.successful({}))
        Mockito.when(oauth2DataHandler.removeTokenByUserId(moderatorId)).thenReturn(Future.successful(1))
      }
    }
  }

  feature("anonymize user by email") {

    val request =
      """{
        |  "email": "mod.erator@modo.com"
        |}
      """.stripMargin

    val badRequest =
      """{
        |  "email": "bad-email"
        |}
      """.stripMargin

    scenario("unauthenticated user") {
      Post("/admin/users/anonymize", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("citizen user") {
      Post("/admin/users/anonymize", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("moderator user") {
      Post("/admin/users/anonymize", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("admin user") {
      Mockito.when(userService.getUserByEmail(newModerator.email)).thenReturn(Future.successful(Some(newModerator)))
      Mockito
        .when(
          userService.anonymize(ArgumentMatchers.eq(newModerator), ArgumentMatchers.eq(adminId), any[RequestContext])
        )
        .thenReturn(Future.successful({}))
      Mockito.when(oauth2DataHandler.removeTokenByUserId(moderatorId)).thenReturn(Future.successful(1))
      Post("/admin/users/anonymize", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("bad request") {
      Post("/admin/users/anonymize", HttpEntity(ContentTypes.`application/json`, badRequest))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  feature("admin get users") {

    val totoUser =
      newModerator.copy(userId = UserId("toto-id"), email = "toto@user.fr", roles = Seq(Role.RoleCitizen))
    val tataUser =
      newModerator.copy(userId = UserId("tata-id"), email = "tata@user.fr", roles = Seq(CustomRole("some-custom-role")))
    val admin =
      newModerator.copy(
        userId = UserId("admin1-id"),
        email = "ad@min1.com",
        roles = Seq(Role.RoleModerator, Role.RoleAdmin)
      )
    val listUsers = Seq(totoUser, tataUser, admin)

    scenario("unauthenticate user unauthorized to get user") {
      Get("/admin/users") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("citizen forbidden to get user") {
      Get("/admin/users")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("moderator forbidden to get user") {
      Get("/admin/users")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("get all users") {
      Mockito
        .when(userService.adminCountUsers(None, None, Some(Role.RoleModerator)))
        .thenReturn(Future.successful(listUsers.size))
      Mockito
        .when(userService.adminFindUsers(0, None, None, None, None, None, Some(Role.RoleModerator)))
        .thenReturn(Future.successful(listUsers))

      Mockito
        .when(userService.adminCountUsers(None, None, Some(CustomRole("some-custom-role"))))
        .thenReturn(Future.successful(1))
      Mockito
        .when(userService.adminFindUsers(0, None, None, None, None, None, Some(CustomRole("some-custom-role"))))
        .thenReturn(Future.successful(Seq(tataUser)))
      Get("/admin/users")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val users = entityAs[Seq[AdminUserResponse]]
        users.size should be(listUsers.size)
      }

      Get("/admin/users?role=some-custom-role")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val users = entityAs[Seq[AdminUserResponse]]
        users.size should be(1)
        users.head.id should be(tataUser.userId)
      }
    }
  }

  feature("admin update user") {

    scenario("citizen forbidden to update user") {
      Put(s"/admin/users/${citizenId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("random user unauthorized to update user") {
      Put(s"/admin/users/${citizenId.value}") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Mockito.when(userService.getUser(citizenId)).thenReturn(Future.successful(Some(newModerator)))
    Mockito.when(userService.getUserByEmail(any[String])).thenReturn(Future.successful(None))

    scenario("moderator forbidden to update user") {
      Put(s"/admin/users/${moderatorId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("admin successfully update user") {
      Mockito.when(userService.update(any[User], any[RequestContext])).thenReturn(Future.successful(newModerator))
      val request =
        """{
          |  "email": "toto@user.com",
          |  "firstName": "New Us",
          |  "lastName": "New Er",
          |  "isOrganisation": false,
          |  "roles": ["ROLE_MODERATOR", "ROLE_POLITICAL", "ROLE_CITIZEN"],
          |  "country": "GB",
          |  "language": "en",
          |  "availableQuestions": []
          |}
        """.stripMargin

      Put(s"/admin/users/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)

        verify(userService)
          .update(ArgumentMatchers.argThat[User] { user: User =>
            user.userId == moderatorId &&
            user.email == "toto@user.com" &&
            user.firstName.contains("New Us") &&
            user.lastName.contains("New Er") &&
            user.lastIp.isEmpty &&
            user.hashedPassword.isEmpty &&
            user.roles == Seq(RoleModerator, RolePolitical, RoleCitizen) &&
            user.country == Country("GB") &&
            user.language == Language("en")
          }, any[RequestContext])
      }
    }

    scenario("failed because email exists") {
      Mockito.when(userService.update(any[User], any[RequestContext])).thenReturn(Future.successful(newModerator))
      Mockito
        .when(userService.getUserByEmail(any[String]))
        .thenReturn(Future.successful(Some(newModerator.copy(userId = UserId("other")))))
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

      Put(s"/admin/moderators/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(Some(ValidationError("email", Some("Email toto@modo.com already exists"))))
      }
    }

    scenario("failed because new email is invalid") {
      Mockito.when(userService.update(any[User], any[RequestContext])).thenReturn(Future.successful(newModerator))
      Mockito.when(userService.getUserByEmail(any[String])).thenReturn(Future.successful(None))
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

      Put(s"/admin/moderators/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(Some(ValidationError("email", Some("email is not a valid email"))))
      }
    }

    scenario("moderator tries to change roles") {
      Mockito.when(userService.update(any[User], any[RequestContext])).thenReturn(Future.successful(newModerator))
      val request =
        """{
          |  "email": "mod.erator@modo.com",
          |  "firstName": "New Mod",
          |  "lastName": "New Erator",
          |  "roles": ["ROLE_MODERATOR", "ROLE_CITIZEN", "ROLE_POLITICAL", "ROLE_ADMIN"],
          |  "country": "GB",
          |  "language": "en",
          |  "availableQuestions": []
          |}
        """.stripMargin

      Put(s"/admin/moderators/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("validation failed for invalid roles") {
      val request =
        """{
          |  "email": "mod.erator@modo.com",
          |  "firstName": "New Mod",
          |  "lastName": "New Erator",
          |  "roles": "foo",
          |  "country": "GB",
          |  "language": "en",
          |  "availableQuestions": []
          |}
        """.stripMargin

      Put(s"/admin/moderators/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val rolesError = errors.find(_.field == "roles")
        rolesError.isDefined should be(true)
        rolesError.map(_.field) should be(Some("roles"))
      }
    }

  }
}
