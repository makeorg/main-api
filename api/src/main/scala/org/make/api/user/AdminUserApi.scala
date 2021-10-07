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
import akka.http.scaladsl.server._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Url
import grizzled.slf4j.Logging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.refined._
import io.circe.{Decoder, Encoder}
import io.swagger.annotations.{ApiImplicitParam, _}
import org.make.api.technical.CsvReceptacle._
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.storage.{Content, StorageConfigurationComponent, StorageServiceComponent, UploadResponse}
import org.make.api.technical.{`X-Total-Count`, MakeAuthenticationDirectives}
import org.make.core.Validation._
import org.make.core._
import org.make.core.auth.UserRights
import org.make.core.job.Job.JobId
import org.make.core.profile.Profile
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.technical.Pagination._
import org.make.core.user._
import scalaoauth2.provider.AuthInfo

import javax.ws.rs.Path
import scala.annotation.meta.field
import scala.concurrent.Future

@Api(value = "Admin Users")
@Path(value = "/admin/users")
trait AdminUserApi extends Directives {

  @ApiOperation(
    value = "get-users",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "id", paramType = "query", dataType = "string", allowMultiple = true),
      new ApiImplicitParam(name = "email", paramType = "query", dataType = "string"),
      new ApiImplicitParam(
        name = "role",
        paramType = "query",
        dataType = "string",
        defaultValue = "ROLE_MODERATOR",
        allowableValues = "ROLE_CITIZEN,ROLE_MODERATOR,ROLE_ADMIN,ROLE_SUPER_ADMIN,ROLE_POLITICAL,ROLE_ACTOR"
      ),
      new ApiImplicitParam(
        name = "userType",
        paramType = "query",
        dataType = "string",
        defaultValue = "USER",
        allowableValues = "USER,ORGANISATION,PERSONALITY"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[AdminUserResponse]]))
  )
  @Path(value = "/")
  def getUsers: Route

  @ApiOperation(
    value = "update-user",
    httpMethod = "PUT",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      ),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.AdminUpdateUserRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[AdminUserResponse]))
  )
  @Path(value = "/{userId}")
  def updateUser: Route

  @ApiOperation(
    value = "get-user",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[AdminUserResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      )
    )
  )
  @Path(value = "/{userId}")
  def getUser: Route

  @ApiOperation(
    value = "admin-anonymize-user",
    httpMethod = "DELETE",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @Path(value = "/{userId}")
  def anonymizeUser: Route

  @ApiOperation(
    value = "admin-anonymize-user-by-email",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.AnonymizeUserRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @Path(value = "/anonymize")
  def anonymizeUserByEmail: Route

  @ApiOperation(
    value = "admin-upload-avatar",
    httpMethod = "POST",
    code = HttpCodes.OK,
    consumes = "multipart/form-data",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userType",
        paramType = "path",
        dataType = "string",
        defaultValue = "USER",
        allowableValues = "USER,ORGANISATION,PERSONALITY"
      ),
      new ApiImplicitParam(name = "data", paramType = "formData", dataType = "file")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UploadResponse])))
  @Path(value = "/upload-avatar/{userType}")
  def adminUploadAvatar: Route

  @ApiOperation(
    value = "update-user-email",
    httpMethod = "POST",
    code = HttpCodes.NoContent,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.AdminUpdateUserEmail")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No content")))
  @Path(value = "/update-user-email")
  def updateUserEmail: Route

  @ApiOperation(
    value = "update-user-roles",
    httpMethod = "POST",
    code = HttpCodes.NoContent,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.UpdateUserRolesRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
  @Path(value = "/update-user-roles")
  def updateUserRoles: Route

  @ApiOperation(
    value = "anonymize-users",
    httpMethod = "DELETE",
    code = HttpCodes.Accepted,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.Accepted, message = "Accepted")))
  @Path(value = "/")
  def anonymizeUsers: Route

  def routes: Route =
    getUsers ~ getUser ~ updateUser ~ anonymizeUser ~ anonymizeUserByEmail ~ adminUploadAvatar ~ updateUserEmail ~ updateUserRoles ~ anonymizeUsers

}

trait AdminUserApiComponent {
  def adminUserApi: AdminUserApi
}

trait DefaultAdminUserApiComponent
    extends AdminUserApiComponent
    with MakeAuthenticationDirectives
    with Logging
    with ParameterExtractors {

  this: MakeDirectivesDependencies
    with UserServiceComponent
    with PersistentUserServiceComponent
    with StorageServiceComponent
    with StorageConfigurationComponent =>

  override lazy val adminUserApi: AdminUserApi = new DefaultAdminUserApi

  class DefaultAdminUserApi extends AdminUserApi {

    val userId: PathMatcher1[UserId] = Segment.map(UserId.apply)

    override def getUsers: Route = get {
      path("admin" / "users") {
        makeOperation("AdminGetUsers") { _ =>
          parameters(
            "_start".as[Start].?,
            "_end".as[End].?,
            "_sort".?,
            "_order".as[Order].?,
            "id".csv[UserId],
            "email".?,
            "role".as[String].?,
            "userType".as[UserType].?
          ) {
            (
              start: Option[Start],
              end: Option[End],
              sort: Option[String],
              order: Option[Order],
              ids: Option[Seq[UserId]],
              email: Option[String],
              maybeRole: Option[String],
              userType: Option[UserType]
            ) =>
              makeOAuth2 { auth: AuthInfo[UserRights] =>
                requireSuperAdminRole(auth.user) {
                  val role: Option[Role] = maybeRole.map(Role.apply)
                  provideAsync(
                    userService.adminCountUsers(
                      ids = ids,
                      email = email,
                      firstName = None,
                      lastName = None,
                      role = role,
                      userType = userType
                    )
                  ) { count =>
                    provideAsync(
                      userService.adminFindUsers(
                        start.orZero,
                        end,
                        sort,
                        order,
                        ids = ids,
                        email = email,
                        firstName = None,
                        lastName = None,
                        role = role,
                        userType = userType
                      )
                    ) { users =>
                      complete(
                        (StatusCodes.OK, List(`X-Total-Count`(count.toString)), users.map(AdminUserResponse.apply))
                      )
                    }
                  }
                }
              }
          }
        }
      }
    }

    override def updateUser: Route =
      put {
        path("admin" / "users" / userId) { userId =>
          makeOperation("AdminUpdateUser") { requestContext =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireSuperAdminRole(userAuth.user) {
                decodeRequest {
                  entity(as[AdminUpdateUserRequest]) { request: AdminUpdateUserRequest =>
                    provideAsyncOrNotFound(userService.getUser(userId)) { user =>
                      val lowerCasedEmail: String = request.email.getOrElse(user.email).toLowerCase()
                      provideAsync(userService.getUserByEmail(lowerCasedEmail)) { maybeUser =>
                        maybeUser.foreach { userToCheck =>
                          Validation.validate(
                            Validation.validateField(
                              field = "email",
                              "already_registered",
                              condition = userToCheck.userId.value == user.userId.value,
                              message = s"Email $lowerCasedEmail already exists"
                            )
                          )
                        }
                        val profile: Option[Profile] = user.profile
                          .map(
                            _.copy(
                              website = request.website.map(_.value),
                              politicalParty = request.politicalParty,
                              optInNewsletter = request.optInNewsletter,
                              avatarUrl = request.avatarUrl.map(_.value)
                            )
                          )
                          .orElse(
                            Profile.parseProfile(
                              website = request.website.map(_.value),
                              politicalParty = request.politicalParty,
                              optInNewsletter = request.optInNewsletter,
                              avatarUrl = request.avatarUrl.map(_.value)
                            )
                          )

                        onSuccess(
                          userService.update(
                            user.copy(
                              email = lowerCasedEmail,
                              firstName = request.firstName.orElse(user.firstName),
                              lastName = request.lastName.orElse(user.lastName),
                              country = request.country.getOrElse(user.country),
                              organisationName = request.organisationName,
                              userType = request.userType,
                              roles = request.roles.map(_.map(Role.apply)).getOrElse(user.roles),
                              availableQuestions = request.availableQuestions,
                              profile = profile
                            ),
                            requestContext
                          )
                        ) { user: User =>
                          complete(StatusCodes.OK -> AdminUserResponse(user))
                        }
                      }
                    }
                  }
                }
              }

            }
          }
        }
      }

    override def getUser: Route = get {
      path("admin" / "users" / userId) { userId =>
        makeOperation("GetUser") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireSuperAdminRole(auth.user) {
              provideAsyncOrNotFound(userService.getUser(userId)) { user =>
                complete(AdminUserResponse(user))
              }
            }
          }
        }
      }
    }

    override def anonymizeUser: Route = delete {
      path("admin" / "users" / userId) { userId: UserId =>
        makeOperation("adminDeleteUser") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireSuperAdminRole(userAuth.user) {
              provideAsyncOrNotFound(userService.getUser(userId)) { user =>
                provideAsync(userService.anonymize(user, userAuth.user.userId, requestContext, Anonymization.Explicit)) {
                  _ =>
                    provideAsync(oauth2DataHandler.removeTokenByUserId(userId)) { _ =>
                      complete(StatusCodes.OK)
                    }
                }
              }
            }
          }
        }
      }
    }

    override def anonymizeUsers: Route = delete {
      path("admin" / "users") {
        makeOperation("adminDeleteUsers") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireSuperAdminRole(userAuth.user) {
              provideAsync(userService.anonymizeInactiveUsers(userAuth.user.userId, requestContext)) { acceptance =>
                if (acceptance.isAccepted) {
                  complete(StatusCodes.Accepted -> JobId.AnonymizeInactiveUsers)
                } else {
                  complete(StatusCodes.Conflict -> JobId.AnonymizeInactiveUsers)
                }
              }
            }
          }
        }
      }
    }

    override def anonymizeUserByEmail: Route = post {
      path("admin" / "users" / "anonymize") {
        makeOperation("anonymizeUserByEmail") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireSuperAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[AnonymizeUserRequest]) { request =>
                  provideAsyncOrNotFound(userService.getUserByEmail(request.email)) { user =>
                    provideAsync(
                      userService.anonymize(user, userAuth.user.userId, requestContext, Anonymization.Explicit)
                    ) { _ =>
                      provideAsync(oauth2DataHandler.removeTokenByUserId(user.userId)) { _ =>
                        complete(StatusCodes.OK)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def adminUploadAvatar: Route = {
      post {
        path("admin" / "users" / "upload-avatar" / UserType) { userType =>
          makeOperation("AdminUserUploadAvatar") { _ =>
            makeOAuth2 { userAuth =>
              requireAdminRole(userAuth.user) {
                def uploadFile(extension: String, contentType: String, fileContent: Content): Future[String] =
                  storageService.uploadAdminUserAvatar(extension, contentType, fileContent, userType)

                uploadImageAsync("data", uploadFile, sizeLimit = Some(storageConfiguration.maxFileSize)) {
                  (path, file) =>
                    file.delete()
                    complete(UploadResponse(path))
                }
              }
            }
          }
        }
      }
    }

    override def updateUserEmail: Route = {
      post {
        path("admin" / "users" / "update-user-email") {
          makeOperation("AdminUserUpdateEmail") { _ =>
            makeOAuth2 { userAuth =>
              requireSuperAdminRole(userAuth.user) {
                decodeRequest {
                  entity(as[AdminUpdateUserEmail]) {
                    case AdminUpdateUserEmail(oldEmail, newEmail) =>
                      provideAsync(userService.getUserByEmail(oldEmail)) {
                        case Some(user) =>
                          provideAsync(userService.adminUpdateUserEmail(user, newEmail))(
                            _ => complete(StatusCodes.NoContent)
                          )
                        case None =>
                          complete(
                            StatusCodes.BadRequest ->
                              Seq(
                                ValidationError("oldEmail", "not_found", Some(s"No user found for email '$oldEmail'"))
                              )
                          )
                      }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def updateUserRoles: Route = {
      post {
        path("admin" / "users" / "update-user-roles") {
          makeOperation("UpdateUserRoles") { requestContext =>
            makeOAuth2 { userAuth =>
              requireSuperAdminRole(userAuth.user) {
                decodeRequest {
                  entity(as[UpdateUserRolesRequest]) { request =>
                    provideAsync(userService.getUserByEmail(request.email)) {
                      case None =>
                        complete(
                          StatusCodes.BadRequest ->
                            Seq(
                              ValidationError(
                                field = "email",
                                key = "not_found",
                                message = Some(s"The email ${request.email} was not found")
                              )
                            )
                        )
                      case Some(user) =>
                        provideAsync(userService.update(user.copy(roles = request.roles), requestContext)) { _ =>
                          complete(StatusCodes.NoContent)
                        }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

}

final case class AnonymizeUserRequest(email: String) {
  Validation.validate(validateUserInput("email", email, None), validateEmail("email", email, None))
}

object AnonymizeUserRequest {
  implicit val decoder: Decoder[AnonymizeUserRequest] = deriveDecoder[AnonymizeUserRequest]
}

final case class AdminUserResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d22c8e70-f709-42ff-8a52-9398d159c753") id: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "yopmail+test@make.org")
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  organisationName: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "USER") userType: UserType,
  @(ApiModelProperty @field)(dataType = "list[string]") roles: Seq[Role],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Country,
  @(ApiModelProperty @field)(dataType = "list[string]")
  availableQuestions: Seq[QuestionId],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com")
  website: Option[String],
  politicalParty: Option[String],
  optInNewsletter: Option[Boolean],
  avatarUrl: Option[String]
) {
  Validation.validate(validateUserInput("email", email, None))
}

object AdminUserResponse extends CirceFormatters {
  implicit val encoder: Encoder[AdminUserResponse] = deriveEncoder[AdminUserResponse]
  implicit val decoder: Decoder[AdminUserResponse] = deriveDecoder[AdminUserResponse]

  def apply(user: User): AdminUserResponse = AdminUserResponse(
    id = user.userId,
    email = user.email,
    firstName = user.firstName,
    organisationName = user.organisationName,
    userType = user.userType,
    lastName = user.lastName,
    roles = user.roles.map(role => CustomRole(role.value)),
    country = user.country,
    availableQuestions = user.availableQuestions,
    politicalParty = user.profile.flatMap(_.politicalParty),
    website = user.profile.flatMap(_.website),
    optInNewsletter = user.profile.map(_.optInNewsletter),
    avatarUrl = user.profile.flatMap(_.avatarUrl)
  )
}

final case class AdminUpdateUserRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "yopmail+test@make.org")
  email: Option[String],
  firstName: Option[String],
  lastName: Option[String],
  organisationName: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "USER") userType: UserType,
  roles: Option[Seq[String]],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Option[Country],
  @(ApiModelProperty @field)(dataType = "list[string]", example = "d22c8e70-f709-42ff-8a52-9398d159c753")
  availableQuestions: Seq[QuestionId],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/website") website: Option[
    String Refined Url
  ],
  politicalParty: Option[String],
  optInNewsletter: Boolean,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/avatar.png") avatarUrl: Option[
    String Refined Url
  ]
) {
  private val maxCountryLength = 3

  validateOptional(
    email.map(email     => validateEmail(fieldName = "email", fieldValue = email.toLowerCase)),
    email.map(email     => validateUserInput("email", email, None)),
    country.map(country => maxLength("country", maxCountryLength, country.value))
  )
}

object AdminUpdateUserRequest {
  implicit lazy val decoder: Decoder[AdminUpdateUserRequest] = deriveDecoder[AdminUpdateUserRequest]
}

final case class AdminUpdateUserEmail(
  @(ApiModelProperty @field)(dataType = "string", example = "yopmail+old@make.org") oldEmail: String,
  @(ApiModelProperty @field)(dataType = "string", example = "yopmail+new@make.org") newEmail: String
) {
  Validation.validate(validateEmail("oldEmail", oldEmail.toLowerCase), validateEmail("newEmail", newEmail.toLowerCase))
}

object AdminUpdateUserEmail {
  implicit val decoder: Decoder[AdminUpdateUserEmail] = deriveDecoder
  implicit val encoder: Encoder[AdminUpdateUserEmail] = deriveEncoder
}

final case class UpdateUserRolesRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "yopmail+test@make.org") email: String,
  @(ApiModelProperty @field)(dataType = "list[string]") roles: Seq[Role]
) {
  Validation.validate(
    validateEmail(fieldName = "email", fieldValue = email.toLowerCase),
    validateUserInput(fieldName = "email", fieldValue = email, message = None)
  )
}

object UpdateUserRolesRequest {
  implicit lazy val decoder: Decoder[UpdateUserRolesRequest] = deriveDecoder[UpdateUserRolesRequest]
  implicit lazy val encoder: Encoder[UpdateUserRolesRequest] = deriveEncoder[UpdateUserRolesRequest]
}
