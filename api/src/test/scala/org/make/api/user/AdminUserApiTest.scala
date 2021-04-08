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
import akka.util.ByteString
import org.make.api.technical.job.JobActor.Protocol.Response.JobAcceptance
import org.make.api.technical.storage.Content.FileContent
import org.make.api.technical.storage._
import org.make.api.{ActorSystemComponent, MakeApi, MakeApiTestBase, TestUtils}
import org.make.core.job.Job.JobId
import org.make.core.reference.Country
import org.make.core.technical.Pagination.Start
import org.make.core.user.Role.{RoleCitizen, RoleModerator, RolePolitical}
import org.make.core.user._
import org.make.core.{RequestContext, ValidationError}

import scala.collection.immutable.Seq
import scala.concurrent.Future

class AdminUserApiTest
    extends MakeApiTestBase
    with DefaultAdminUserApiComponent
    with UserServiceComponent
    with ActorSystemComponent
    with PersistentUserServiceComponent
    with StorageServiceComponent
    with StorageConfigurationComponent {

  override val userService: UserService = mock[UserService]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val storageService: StorageService = mock[StorageService]
  override val storageConfiguration: StorageConfiguration = StorageConfiguration("", "", 4242L)

  val routes: Route = sealRoute(handleRejections(MakeApi.rejectionHandler) {
    adminUserApi.routes
  })

  val citizenId: UserId = defaultCitizenUser.userId
  val moderatorId: UserId = defaultModeratorUser.userId
  val adminId: UserId = defaultAdminUser.userId

  when(userService.getUser(eqTo(citizenId))).thenReturn(Future.successful(Some(defaultCitizenUser)))
  when(userService.getUserByEmail(defaultCitizenUser.email)).thenReturn(Future.successful(Some(defaultCitizenUser)))
  when(userService.getUser(eqTo(moderatorId))).thenReturn(Future.successful(Some(defaultModeratorUser)))
  when(userService.getUserByEmail(defaultModeratorUser.email)).thenReturn(Future.successful(Some(defaultModeratorUser)))
  when(userService.update(any[User], any[RequestContext])).thenReturn(Future.successful(defaultModeratorUser))

  Feature("get user") {
    Scenario("unauthenticate user unauthorized to get user") {
      Get(s"/admin/users/${citizenId.value}") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("citizen forbidden to get user") {
      Get(s"/admin/users/${citizenId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("moderator forbidden to get user") {
      Get(s"/admin/users/${citizenId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("unexistant user") {
      when(userService.getUser(eqTo(UserId("user-fake")))).thenReturn(Future.successful(None))
      Get("/admin/users/user-fake")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    Scenario("successfully return user") {
      Get(s"/admin/users/${citizenId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val user = entityAs[AdminUserResponse]
        user.id should be(citizenId)
      }
    }
  }

  Feature("anonymize user by id") {
    Scenario("unauthenticated user") {
      Delete("/admin/users/user-id") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("citizen user") {
      Delete("/admin/users/user-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("moderator user") {
      Delete("/admin/users/user-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("admin user") {
      when(
        userService
          .anonymize(eqTo(defaultModeratorUser), eqTo(adminId), any[RequestContext], eqTo(Anonymization.Automatic))
      ).thenReturn(Future.unit)
      when(oauth2DataHandler.removeTokenByUserId(moderatorId)).thenReturn(Future.successful(1))
      Delete(s"/admin/users/${moderatorId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  Feature("anonymize user by email") {

    val request =
      s"""{
        |  "email": "${defaultModeratorUser.email}"
        |}
      """.stripMargin

    val badRequest =
      """{
        |  "email": "bad-email"
        |}
      """.stripMargin

    Scenario("unauthenticated user") {
      Post("/admin/users/anonymize", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("citizen user") {
      Post("/admin/users/anonymize", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("moderator user") {
      Post("/admin/users/anonymize", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("admin user") {
      when(
        userService
          .anonymize(eqTo(defaultModeratorUser), eqTo(adminId), any[RequestContext], eqTo(Anonymization.Automatic))
      ).thenReturn(Future.unit)
      when(oauth2DataHandler.removeTokenByUserId(moderatorId)).thenReturn(Future.successful(1))
      Post("/admin/users/anonymize", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("bad request") {
      Post("/admin/users/anonymize", HttpEntity(ContentTypes.`application/json`, badRequest))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  Feature("anonymize inactive users") {

    Scenario("unauthenticated user") {
      Delete("/admin/users") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("citizen user") {
      Delete("/admin/users")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("moderator user") {
      Delete("/admin/users")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("admin user - job accepted") {
      when(userService.anonymizeInactiveUsers(eqTo(adminId), any[RequestContext]))
        .thenReturn(Future.successful(JobAcceptance(true)))
      Delete("/admin/users")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.Accepted)
        val response = entityAs[JobId]
        response should be(JobId.AnonymizeInactiveUsers)
      }
    }

    Scenario("admin user - job not accepted") {
      when(userService.anonymizeInactiveUsers(eqTo(adminId), any[RequestContext]))
        .thenReturn(Future.successful(JobAcceptance(false)))
      Delete("/admin/users")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.Conflict)
        val response = entityAs[JobId]
        response should be(JobId.AnonymizeInactiveUsers)
      }
    }
  }

  Feature("admin get users") {

    val totoUser =
      TestUtils.user(id = UserId("toto-id"), email = "toto@user.fr", roles = Seq(Role.RoleCitizen))
    val tataUser =
      TestUtils.user(id = UserId("tata-id"), email = "tata@user.fr", roles = Seq(CustomRole("some-custom-role")))
    val admin =
      TestUtils.user(id = UserId("admin1-id"), email = "ad@min1.com", roles = Seq(Role.RoleModerator, Role.RoleAdmin))
    val listUsers = Seq(totoUser, tataUser, admin)

    Scenario("unauthenticate user unauthorized to get user") {
      Get("/admin/users") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("citizen forbidden to get user") {
      Get("/admin/users")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("moderator forbidden to get user") {
      Get("/admin/users")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("get all users") {
      when(
        userService
          .adminCountUsers(email = None, firstName = None, lastName = None, role = None, userType = None)
      ).thenReturn(Future.successful(listUsers.size))
      when(
        userService
          .adminFindUsers(
            start = Start.zero,
            end = None,
            sort = None,
            order = None,
            email = None,
            firstName = None,
            lastName = None,
            role = None,
            userType = None
          )
      ).thenReturn(Future.successful(listUsers))

      when(
        userService.adminCountUsers(
          email = None,
          firstName = None,
          lastName = None,
          role = Some(CustomRole("some-custom-role")),
          userType = None
        )
      ).thenReturn(Future.successful(1))
      when(
        userService.adminFindUsers(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          email = None,
          firstName = None,
          lastName = None,
          role = Some(CustomRole("some-custom-role")),
          userType = None
        )
      ).thenReturn(Future.successful(Seq(tataUser)))
      Get("/admin/users")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val users = entityAs[Seq[AdminUserResponse]]
        users.size should be(listUsers.size)
      }

      Get("/admin/users?role=some-custom-role")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val users = entityAs[Seq[AdminUserResponse]]
        users.size should be(1)
        users.head.id should be(tataUser.userId)
      }
    }
  }

  Feature("admin update user") {

    Scenario("citizen forbidden to update user") {
      Put(s"/admin/users/${citizenId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("random user unauthorized to update user") {
      Put(s"/admin/users/${citizenId.value}") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("moderator forbidden to update user") {
      Put(s"/admin/users/${moderatorId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("admin successfully update user") {
      when(userService.getUserByEmail(eqTo("toto@user.com"))).thenReturn(Future.successful(None))
      val request =
        """{
          |  "email": "toto@user.com",
          |  "firstName": "New Us",
          |  "lastName": "New Er",
          |  "userType": "USER",
          |  "roles": ["ROLE_MODERATOR", "ROLE_POLITICAL", "ROLE_CITIZEN"],
          |  "country": "GB",
          |  "language": "en",
          |  "availableQuestions": []
          |}
        """.stripMargin

      Put(s"/admin/users/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)

        verify(userService)
          .update(argThat[User] { user: User =>
            user.userId == moderatorId &&
            user.email == "toto@user.com" &&
            user.firstName.contains("New Us") &&
            user.lastName.contains("New Er") &&
            user.lastIp.isEmpty &&
            user.hashedPassword.isEmpty &&
            user.roles == Seq(RoleModerator, RolePolitical, RoleCitizen) &&
            user.country == Country("GB")
          }, any[RequestContext])
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
          |  "userType": "USER",
          |  "roles": ["ROLE_MODERATOR", "ROLE_POLITICAL", "ROLE_CITIZEN"],
          |  "country": "GB",
          |  "language": "en",
          |  "availableQuestions": []
          |}
        """.stripMargin

      Put(s"/admin/users/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(
          Some(ValidationError("email", "already_registered", Some("Email toto@modo.com already exists")))
        )
      }
    }

    Scenario("failed because new email is invalid") {
      val request =
        """{
          |  "email": "toto@modo",
          |  "firstName": "New Mod",
          |  "lastName": "New Erator",
          |  "userType": "USER",
          |  "roles": ["ROLE_MODERATOR", "ROLE_POLITICAL", "ROLE_CITIZEN"],
          |  "country": "GB",
          |  "language": "en",
          |  "availableQuestions": []
          |}
        """.stripMargin

      Put(s"/admin/users/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(Some(ValidationError("email", "invalid_email", Some("email is not a valid email"))))
      }
    }

    Scenario("moderator tries to change roles") {
      val request =
        s"""{
          |  "email": "${defaultModeratorUser.email}",
          |  "firstName": "New Mod",
          |  "lastName": "New Erator",
          |  "userType": "USER",
          |  "roles": ["ROLE_MODERATOR", "ROLE_CITIZEN", "ROLE_POLITICAL", "ROLE_ADMIN"],
          |  "country": "GB",
          |  "language": "en",
          |  "availableQuestions": []
          |}
        """.stripMargin

      Put(s"/admin/users/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
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
          |  "userType": "USER",
          |  "roles": "foo",
          |  "country": "GB",
          |  "language": "en",
          |  "availableQuestions": []
          |}
        """.stripMargin

      Put(s"/admin/users/${moderatorId.value}", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val rolesError = errors.find(_.field == "roles")
        rolesError.isDefined should be(true)
        rolesError.map(_.field) should be(Some("roles"))
      }
    }

  }

  Feature("upload avatar") {
    Scenario("unauthorized not connected") {
      Post(s"/admin/users/upload-avatar/${UserType.UserTypeOrganisation.value}") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbidden citizen") {
      Post(s"/admin/users/upload-avatar/${UserType.UserTypeOrganisation.value}")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbidden moderator") {
      Post(s"/admin/users/upload-avatar/${UserType.UserTypeOrganisation.value}")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("incorrect file type") {
      val request: Multipart = Multipart.FormData(fields = Map(
        "data" -> HttpEntity
          .Strict(ContentTypes.`application/x-www-form-urlencoded`, ByteString("incorrect file type"))
      )
      )

      Post(s"/admin/users/upload-avatar/${UserType.UserTypeOrganisation.value}", request)
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("storage unavailable") {
      when(
        storageService
          .uploadAdminUserAvatar(any[String], any[String], any[FileContent], eqTo(UserType.UserTypePersonality))
      ).thenReturn(Future.failed(new Exception("swift client error")))
      val request: Multipart =
        Multipart.FormData(
          Multipart.FormData.BodyPart
            .Strict(
              "data",
              HttpEntity.Strict(ContentType(MediaTypes.`image/jpeg`), ByteString("image")),
              Map("filename" -> "image.jpeg")
            )
        )

      Post(s"/admin/users/upload-avatar/${UserType.UserTypePersonality.value}", request)
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.InternalServerError)
      }
    }

    Scenario("file too large uploaded by admin") {
      when(storageService.uploadFile(eqTo(FileType.Avatar), any[String], any[String], any[FileContent]))
        .thenReturn(Future.successful("path/to/uploaded/image.jpeg"))

      def entityOfSize(size: Int): Multipart = Multipart.FormData(
        Multipart.FormData.BodyPart
          .Strict(
            "data",
            HttpEntity.Strict(ContentType(MediaTypes.`image/jpeg`), ByteString("0" * size)),
            Map("filename" -> "image.jpeg")
          )
      )

      Post(
        s"/admin/users/upload-avatar/${UserType.UserTypeOrganisation.value}",
        entityOfSize(storageConfiguration.maxFileSize.toInt + 1)
      ).withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.PayloadTooLarge)
      }
    }

    Scenario("file successfully uploaded") {
      when(
        storageService
          .uploadAdminUserAvatar(any[String], any[String], any[FileContent], eqTo(UserType.UserTypeOrganisation))
      ).thenReturn(Future.successful("path/to/uploaded/image.jpeg"))

      def entityOfSize(size: Int): Multipart = Multipart.FormData(
        Multipart.FormData.BodyPart
          .Strict(
            "data",
            HttpEntity.Strict(ContentType(MediaTypes.`image/jpeg`), ByteString("0" * size)),
            Map("filename" -> "image.jpeg")
          )
      )

      Post(s"/admin/users/upload-avatar/${UserType.UserTypeOrganisation.value}", entityOfSize(10))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)

        val path: UploadResponse = entityAs[UploadResponse]
        path.path shouldBe "path/to/uploaded/image.jpeg"
      }
    }
  }

  Feature("update user email") {

    val request = AdminUpdateUserEmail(defaultCitizenUser.email, "kane@example.com")
    when(userService.adminUpdateUserEmail(any[User], any[String])).thenReturn(Future.unit)

    Scenario("anonymously") {
      Post("/admin/users/update-user-email", request) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("as a citizen") {
      Post("/admin/users/update-user-email", request)
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("as a moderator") {
      Post("/admin/users/update-user-email", request)
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("as an admin") {
      Post("/admin/users/update-user-email", request)
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
    }

    Scenario("old email not found") {
      when(userService.getUserByEmail(eqTo("outis@example.com"))).thenReturn(Future.successful(None))
      Post("/admin/users/update-user-email", request.copy(oldEmail = "outis@example.com"))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  Feature("update user role") {
    val request = UpdateUserRolesRequest(email = defaultCitizenUser.email, roles = Seq(RoleCitizen, RoleModerator))

    Scenario("unauthorized not connected") {
      Post(s"/admin/users/update-user-roles") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbidden citizen") {
      Post(s"/admin/users/update-user-roles")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbidden moderator") {
      Post(s"/admin/users/update-user-roles")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("ok") {
      Post("/admin/users/update-user-roles", request)
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
    }

    Scenario("email not found") {
      when(userService.getUserByEmail(eqTo("non-existent@make.org"))).thenReturn(Future.successful(None))
      Post("/admin/users/update-user-roles", request.copy(email = "non-existent@make.org"))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

}
