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
import grizzled.slf4j.Logging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations.{ApiImplicitParam, _}
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.{`X-Total-Count`, MakeAuthenticationDirectives}
import org.make.core.Validation.{validateField, _}
import org.make.core._
import org.make.core.auth.UserRights
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.technical.Pagination._
import org.make.core.user.Role.{RoleAdmin, RoleModerator, RoleSuperAdmin}
import org.make.core.user._
import scalaoauth2.provider.AuthInfo

import javax.ws.rs.Path
import scala.annotation.meta.field

@Api(value = "Admin Moderators")
@Path(value = "/admin/moderators")
trait AdminModeratorApi extends Directives {

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
  @Path(value = "/{moderatorId}")
  def getModerator: Route

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
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "email", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "firstName", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[ModeratorResponse]]))
  )
  @Path(value = "/")
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
  @Path(value = "/")
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
      new ApiImplicitParam(
        name = "moderatorId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      ),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.UpdateModeratorRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModeratorResponse]))
  )
  @Path(value = "/{moderatorId}")
  def updateModerator: Route

  def routes: Route =
    getModerator ~ getModerators ~ createModerator ~ updateModerator
}

trait AdminModeratorApiComponent {
  def adminModeratorApi: AdminModeratorApi
}

trait DefaultAdminModeratorApiComponent
    extends AdminModeratorApiComponent
    with MakeAuthenticationDirectives
    with Logging
    with ParameterExtractors {

  this: MakeDirectivesDependencies with UserServiceComponent with PersistentUserServiceComponent =>

  override lazy val adminModeratorApi: AdminModeratorApi = new DefaultAdminModeratorApi

  class DefaultAdminModeratorApi extends AdminModeratorApi {

    val moderatorId: PathMatcher1[UserId] = Segment.map(UserId.apply)

    private def isModerator(user: User): Boolean = {
      Set(RoleModerator, RoleAdmin, RoleSuperAdmin).intersect(user.roles.toSet).nonEmpty
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

    override def getModerators: Route = get {
      path("admin" / "moderators") {
        makeOperation("GetModerators") { _ =>
          parameters("_start".as[Start].?, "_end".as[End].?, "_sort".?, "_order".as[Order].?, "email".?, "firstName".?) {
            (
              start: Option[Start],
              end: Option[End],
              sort: Option[String],
              order: Option[Order],
              email: Option[String],
              firstName: Option[String]
            ) =>
              makeOAuth2 { auth: AuthInfo[UserRights] =>
                requireAdminRole(auth.user) {
                  provideAsync(
                    userService.adminCountUsers(
                      email = email,
                      firstName = firstName,
                      lastName = None,
                      role = Some(Role.RoleModerator),
                      userType = Some(UserType.UserTypeUser)
                    )
                  ) { count =>
                    provideAsync(
                      userService.adminFindUsers(
                        start.orZero,
                        end,
                        sort,
                        order,
                        email = email,
                        firstName = firstName,
                        lastName = None,
                        role = Some(Role.RoleModerator),
                        userType = Some(UserType.UserTypeUser)
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
                          password = request.password,
                          lastIp = None,
                          country = request.country,
                          optIn = Some(false),
                          optInPartner = Some(false),
                          roles = request.roles
                            .map(_.map(Role.apply))
                            .getOrElse(Seq(Role.RoleModerator, Role.RoleCitizen)),
                          availableQuestions = request.availableQuestions,
                          privacyPolicyApprovalDate = Some(DateHelper.now())
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
              val isAdmin = Set(RoleAdmin, RoleSuperAdmin).intersect(userAuth.user.roles.toSet).nonEmpty
              val isModerator = userAuth.user.roles.contains(RoleModerator)
              authorize((isModerator && moderatorId == userAuth.user.userId) || isAdmin) {
                decodeRequest {
                  entity(as[UpdateModeratorRequest]) { request: UpdateModeratorRequest =>
                    provideAsyncOrNotFound(userService.getUser(moderatorId)) { user =>
                      val roles =
                        request.roles.map(_.map(Role.apply)).getOrElse(user.roles)
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
  }

}

final case class CreateModeratorRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "yopmail+test@make.org") email: String,
  firstName: Option[String],
  lastName: Option[String],
  @(ApiModelProperty @field)(dataType = "list[string]") roles: Option[Seq[String]],
  password: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Country,
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
    mandatoryField("country", country),
    validateField(
      "password",
      "invalid_password",
      password.forall(_.length >= 8),
      "Password must be at least 8 characters"
    )
  )
}

object CreateModeratorRequest {
  implicit val decoder: Decoder[CreateModeratorRequest] = deriveDecoder[CreateModeratorRequest]
}

final case class UpdateModeratorRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "yopmail+test@make.org") email: Option[String],
  firstName: Option[String],
  lastName: Option[String],
  @(ApiModelProperty @field)(dataType = "list[string]") roles: Option[Seq[String]],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Option[Country],
  @(ApiModelProperty @field)(dataType = "list[string]", example = "d22c8e70-f709-42ff-8a52-9398d159c753")
  availableQuestions: Seq[QuestionId]
) {
  private val maxCountryLength = 3

  validateOptional(
    email.map(email     => validateEmail(fieldName = "email", fieldValue = email.toLowerCase)),
    email.map(email     => validateUserInput("email", email, None)),
    firstName.map(value => requireNonEmpty("firstName", value, Some("firstName should not be an empty string"))),
    firstName.map(value => validateUserInput("firstName", value, None)),
    lastName.map(value  => validateUserInput("lastName", value, None)),
    country.map(country => maxLength("country", maxCountryLength, country.value))
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

final case class ModeratorResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d22c8e70-f709-42ff-8a52-9398d159c753") id: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "yopmail+test@make.org") email: String,
  firstName: Option[String],
  lastName: Option[String],
  @(ApiModelProperty @field)(dataType = "list[string]") roles: Seq[Role],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Country,
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
    availableQuestions = user.availableQuestions
  )
}
