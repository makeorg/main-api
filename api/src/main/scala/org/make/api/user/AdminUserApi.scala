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
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations.{ApiImplicitParam, _}
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{`X-Total-Count`, IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.Validation._
import org.make.core._
import org.make.core.auth.UserRights
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.user.Role.RoleAdmin
import org.make.core.user.{CustomRole, Role, User, UserId}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

@Api(value = "Admin Moderator")
@Path(value = "/admin")
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
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "email", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "role", paramType = "query", dataType = "string", defaultValue = "ROLE_MODERATOR")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[AdminUserResponse]]))
  )
  @Path(value = "/users")
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
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.AdminUpdateUserRequest"),
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[AdminUserResponse]))
  )
  @Path(value = "/users/{userId}")
  def updateUser: Route

  @ApiOperation(
    value = "get-moderator",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModeratorResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "moderatorId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      )
    )
  )
  @Path(value = "/moderators/{moderatorId}")
  def getModerator: Route

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
  @Path(value = "/users/{userId}")
  def getUser: Route

  @ApiOperation(
    value = "get-moderators",
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
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "email", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "firstName", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[ModeratorResponse]]))
  )
  @Path(value = "/moderators")
  def getModerators: Route

  @ApiOperation(
    value = "create-moderator",
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
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.CreateModeratorRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModeratorResponse]))
  )
  @Path(value = "/moderators")
  def createModerator: Route

  @ApiOperation(
    value = "update-moderator",
    httpMethod = "PUT",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.UpdateModeratorRequest"),
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModeratorResponse]))
  )
  @Path(value = "/moderators/{moderatorId}")
  def updateModerator: Route

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
  @Path(value = "/users/{userId}")
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
  @Path(value = "/users/anonymize")
  def anonymizeUserByEmail: Route

  def routes: Route =
    getUsers ~ getUser ~ updateUser ~ getModerator ~ getModerators ~ createModerator ~ updateModerator ~
      anonymizeUser ~ anonymizeUserByEmail
}

trait AdminUserApiComponent {
  def adminUserApi: AdminUserApi
}

trait DefaultAdminUserApiComponent
    extends AdminUserApiComponent
    with MakeAuthenticationDirectives
    with StrictLogging
    with ParameterExtractors {

  this: UserServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with SessionHistoryCoordinatorServiceComponent
    with MakeSettingsComponent
    with PersistentUserServiceComponent =>

  override lazy val adminUserApi: AdminUserApi = new DefaultAdminUserApi

  class DefaultAdminUserApi extends AdminUserApi {

    val moderatorId: PathMatcher1[UserId] = Segment.map(UserId.apply)
    val userId: PathMatcher1[UserId] = Segment.map(UserId.apply)

    override def getUsers: Route = get {
      path("admin" / "users") {
        makeOperation("AdminGetUsers") { _ =>
          parameters(('_start.as[Int].?, '_end.as[Int].?, '_sort.?, '_order.?, 'email.?, 'role.as[String].?)) {
            (start: Option[Int],
             end: Option[Int],
             sort: Option[String],
             order: Option[String],
             email: Option[String],
             maybeRole: Option[String]) =>
              makeOAuth2 { auth: AuthInfo[UserRights] =>
                requireAdminRole(auth.user) {
                  val role: Role =
                    maybeRole.map(Role.matchRole).getOrElse(Role.RoleModerator)
                  provideAsync(userService.adminCountUsers(email = email, firstName = None, role = Some(role))) {
                    count =>
                      provideAsync(
                        userService.adminFindUsers(
                          start.getOrElse(0),
                          end,
                          sort,
                          order,
                          email = email,
                          firstName = None,
                          role = Some(role)
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
              requireAdminRole(userAuth.user) {
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

                        onSuccess(
                          userService.update(
                            user.copy(
                              email = lowerCasedEmail,
                              firstName = request.firstName.orElse(user.firstName),
                              lastName = request.lastName.orElse(user.lastName),
                              country = request.country.getOrElse(user.country),
                              language = request.language.getOrElse(user.language),
                              organisationName = request.organisationName,
                              isOrganisation = request.isOrganisation,
                              roles = request.roles.map(_.map(Role.matchRole)).getOrElse(user.roles),
                              availableQuestions = request.availableQuestions
                            ),
                            requestContext
                          )
                        ) { user: User =>
                          complete(StatusCodes.OK -> ModeratorResponse(user))
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

    private def isModerator(user: User): Boolean = {
      user.roles.contains(Role.RoleModerator) || user.roles.contains(Role.RoleAdmin)
    }

    override def getModerator: Route = get {
      path("admin" / "moderators" / moderatorId) { moderatorId =>
        makeOperation("GetModerator") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              provideAsyncOrNotFound(userService.getUser(moderatorId)) { moderator =>
                if (!isModerator(moderator)) {
                  complete(StatusCodes.NotFound)
                } else {
                  complete(ModeratorResponse(moderator))
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
            requireAdminRole(auth.user) {
              provideAsyncOrNotFound(userService.getUser(userId)) { user =>
                complete(AdminUserResponse(user))
              }
            }
          }
        }
      }
    }

    override def getModerators: Route = get {
      path("admin" / "moderators") {
        makeOperation("GetModerators") { _ =>
          parameters(('_start.as[Int].?, '_end.as[Int].?, '_sort.?, '_order.?, 'email.?, 'firstName.?)) {
            (start: Option[Int],
             end: Option[Int],
             sort: Option[String],
             order: Option[String],
             email: Option[String],
             firstName: Option[String]) =>
              makeOAuth2 { auth: AuthInfo[UserRights] =>
                requireAdminRole(auth.user) {
                  provideAsync(
                    userService.adminCountUsers(email = email, firstName = firstName, role = Some(Role.RoleModerator))
                  ) { count =>
                    provideAsync(
                      userService.adminFindUsers(
                        start.getOrElse(0),
                        end,
                        sort,
                        order,
                        email = email,
                        firstName = None,
                        role = Some(Role.RoleModerator)
                      )
                    ) { users =>
                      complete(
                        (StatusCodes.OK, List(`X-Total-Count`(count.toString)), users.map(ModeratorResponse.apply))
                      )
                    }
                  }
                }
              }
          }
        }
      }
    }

    override def createModerator: Route = post {
      path("admin" / "moderators") {
        makeOperation("CreateModerator") { requestContext =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[CreateModeratorRequest]) { request: CreateModeratorRequest =>
                  provideAsync(
                    userService
                      .register(
                        UserRegisterData(
                          email = request.email,
                          firstName = request.firstName,
                          lastName = request.lastName,
                          password = None,
                          lastIp = None,
                          country = request.country,
                          language = request.language,
                          optIn = Some(false),
                          optInPartner = Some(false),
                          roles = request.roles
                            .map(_.map(Role.matchRole))
                            .getOrElse(Seq(Role.RoleModerator, Role.RoleCitizen)),
                          availableQuestions = request.availableQuestions
                        ),
                        requestContext
                      )
                  ) { result =>
                    complete(StatusCodes.Created -> ModeratorResponse(result))
                  }
                }
              }
            }
          }
        }
      }
    }

    override def updateModerator: Route =
      put {
        path("admin" / "moderators" / moderatorId) { moderatorId =>
          makeOperation("UpdateModerator") { requestContext =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              val isAdmin = userAuth.user.roles.contains(RoleAdmin)
              authorize(moderatorId == userAuth.user.userId || isAdmin) {
                decodeRequest {
                  entity(as[UpdateModeratorRequest]) { request: UpdateModeratorRequest =>
                    provideAsyncOrNotFound(userService.getUser(moderatorId)) { user =>
                      val roles =
                        request.roles.map(_.map(Role.matchRole)).getOrElse(user.roles)
                      authorize {
                        roles != user.roles && isAdmin || roles == user.roles
                      } {
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

                          onSuccess(
                            userService.update(
                              user.copy(
                                email = lowerCasedEmail,
                                firstName = request.firstName.orElse(user.firstName),
                                lastName = request.lastName.orElse(user.lastName),
                                country = request.country.getOrElse(user.country),
                                language = request.language.getOrElse(user.language),
                                roles = roles,
                                availableQuestions = request.availableQuestions
                              ),
                              requestContext
                            )
                          ) { user: User =>
                            complete(StatusCodes.OK -> ModeratorResponse(user))
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

    override def anonymizeUser: Route = delete {
      path("admin" / "users" / moderatorId) { userId: UserId =>
        makeOperation("adminDeleteUser") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              provideAsyncOrNotFound(userService.getUser(userId)) { user =>
                provideAsync(userService.anonymize(user, userAuth.user.userId, requestContext)) { _ =>
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

    override def anonymizeUserByEmail: Route = post {
      path("admin" / "users" / "anonymize") {
        makeOperation("anonymizeUserByEmail") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[AnonymizeUserRequest]) { request =>
                  provideAsyncOrNotFound(userService.getUserByEmail(request.email)) { user =>
                    provideAsync(userService.anonymize(user, userAuth.user.userId, requestContext)) { _ =>
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
  }
}

final case class CreateModeratorRequest(
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  roles: Option[Seq[String]],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "fr") language: Language,
  @(ApiModelProperty @field)(dataType = "list[string]", example = "d22c8e70-f709-42ff-8a52-9398d159c753")
  availableQuestions: Seq[QuestionId]
) {
  validate(
    mandatoryField("firstName", firstName),
    validateOptionalUserInput("firstName", firstName, None),
    validateOptionalUserInput("lastName", lastName, None),
    mandatoryField("email", email),
    validateEmail("email", email.toLowerCase),
    validateUserInput("email", email, None),
    mandatoryField("language", language),
    mandatoryField("country", country)
  )
}

object CreateModeratorRequest {
  implicit val decoder: Decoder[CreateModeratorRequest] = deriveDecoder[CreateModeratorRequest]
}

final case class UpdateModeratorRequest(
  email: Option[String],
  firstName: Option[String],
  lastName: Option[String],
  roles: Option[Seq[String]],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Option[Country],
  @(ApiModelProperty @field)(dataType = "string", example = "fr") language: Option[Language],
  @(ApiModelProperty @field)(dataType = "list[string]", example = "d22c8e70-f709-42ff-8a52-9398d159c753")
  availableQuestions: Seq[QuestionId]
) {
  private val maxLanguageLength = 3
  private val maxCountryLength = 3

  validateOptional(
    email.map(email       => validateEmail(fieldName = "email", fieldValue = email.toLowerCase)),
    email.map(email       => validateUserInput("email", email, None)),
    firstName.map(value   => requireNonEmpty("firstName", value, Some("firstName should not be an empty string"))),
    firstName.map(value   => validateUserInput("firstName", value, None)),
    lastName.map(value    => validateUserInput("lastName", value, None)),
    country.map(country   => maxLength("country", maxCountryLength, country.value)),
    language.map(language => maxLength("language", maxLanguageLength, language.value))
  )
}

object UpdateModeratorRequest {
  implicit val decoder: Decoder[UpdateModeratorRequest] = deriveDecoder[UpdateModeratorRequest]

  def updateValue(oldValue: Option[String], newValue: Option[String]): Option[String] = {
    newValue match {
      case None     => oldValue
      case Some("") => None
      case value    => value
    }
  }
}

final case class AnonymizeUserRequest(email: String) {
  validate(validateUserInput("email", email, None), validateEmail("email", email, None))
}

object AnonymizeUserRequest {
  implicit val decoder: Decoder[AnonymizeUserRequest] = deriveDecoder[AnonymizeUserRequest]
}

case class ModeratorResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d22c8e70-f709-42ff-8a52-9398d159c753") id: UserId,
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  @(ApiModelProperty @field)(dataType = "list[string]") roles: Seq[Role],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "fr") language: Language,
  @(ApiModelProperty @field)(dataType = "list[string]")
  availableQuestions: Seq[QuestionId]
) {
  validate(
    validateUserInput("email", email, None),
    validateOptionalUserInput("firstName", firstName, None),
    validateOptionalUserInput("lastName", lastName, None)
  )
}

object ModeratorResponse extends CirceFormatters {
  implicit val encoder: Encoder[ModeratorResponse] = deriveEncoder[ModeratorResponse]
  implicit val decoder: Decoder[ModeratorResponse] = deriveDecoder[ModeratorResponse]

  def apply(user: User): ModeratorResponse = ModeratorResponse(
    id = user.userId,
    email = user.email,
    firstName = user.firstName,
    lastName = user.lastName,
    roles = user.roles,
    country = user.country,
    language = user.language,
    availableQuestions = user.availableQuestions
  )
}

case class AdminUserResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d22c8e70-f709-42ff-8a52-9398d159c753") id: UserId,
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  organisationName: Option[String],
  @(ApiModelProperty @field)(dataType = "boolean") isOrganisation: Boolean,
  @(ApiModelProperty @field)(dataType = "list[string]") roles: Seq[Role],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "fr") language: Language,
  @(ApiModelProperty @field)(dataType = "list[string]")
  availableQuestions: Seq[QuestionId]
) {
  validate(validateUserInput("email", email, None))
}

object AdminUserResponse extends CirceFormatters {
  implicit val encoder: Encoder[AdminUserResponse] = deriveEncoder[AdminUserResponse]
  implicit val decoder: Decoder[AdminUserResponse] = deriveDecoder[AdminUserResponse]

  def apply(user: User): AdminUserResponse = AdminUserResponse(
    id = user.userId,
    email = user.email,
    firstName = user.firstName,
    organisationName = user.organisationName,
    isOrganisation = user.isOrganisation,
    lastName = user.lastName,
    roles = user.roles.map(role => CustomRole(role.shortName)),
    country = user.country,
    language = user.language,
    availableQuestions = user.availableQuestions
  )
}

final case class AdminUpdateUserRequest(
  email: Option[String],
  firstName: Option[String],
  lastName: Option[String],
  organisationName: Option[String],
  @(ApiModelProperty @field)(dataType = "boolean") isOrganisation: Boolean,
  roles: Option[Seq[String]],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Option[Country],
  @(ApiModelProperty @field)(dataType = "string", example = "fr") language: Option[Language],
  @(ApiModelProperty @field)(dataType = "list[string]", example = "d22c8e70-f709-42ff-8a52-9398d159c753")
  availableQuestions: Seq[QuestionId]
) {
  private val maxLanguageLength = 3
  private val maxCountryLength = 3

  validateOptional(
    email.map(email       => validateEmail(fieldName = "email", fieldValue = email.toLowerCase)),
    email.map(email       => validateUserInput("email", email, None)),
    country.map(country   => maxLength("country", maxCountryLength, country.value)),
    language.map(language => maxLength("language", maxLanguageLength, language.value))
  )
}

object AdminUpdateUserRequest {
  implicit val decoder: Decoder[AdminUpdateUserRequest] = deriveDecoder[AdminUpdateUserRequest]
}
