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
import io.circe.{Decoder, ObjectEncoder}
import io.swagger.annotations.{ApiImplicitParam, _}
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, TotalCountHeader}
import org.make.core.Validation._
import org.make.core._
import org.make.core.auth.UserRights
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.user.Role.RoleAdmin
import org.make.core.user.{Role, User, UserId}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

@Api(value = "Admin Moderator")
@Path(value = "/admin")
trait AdminUserApi extends Directives {

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
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      )
    )
  )
  @Path(value = "/moderators/{moderatorId}")
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
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModeratorResponse]))
  )
  @Path(value = "/users/{userId}")
  def anonymizeUser: Route

  def routes: Route =
    getModerator ~ getModerators ~ createModerator ~ updateModerator ~ anonymizeUser
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
    with MakeSettingsComponent
    with PersistentUserServiceComponent =>

  override lazy val adminUserApi: AdminUserApi = new AdminUserApi {

    val moderatorId: PathMatcher1[UserId] = Segment.map(UserId.apply)

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
                  provideAsync(userService.countModerators(email, firstName)) { count =>
                    provideAsync(userService.findModerators(start.getOrElse(0), end, sort, order, email, firstName)) {
                      moderators =>
                        complete(
                          (
                            StatusCodes.OK,
                            List(TotalCountHeader(count.toString)),
                            moderators.map(ModeratorResponse.apply)
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
                            .map(_.flatMap(Role.matchRole))
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
                      val roles = request.roles.map(_.flatMap(Role.matchRole)).getOrElse(user.roles)
                      authorize {
                        roles != user.roles && isAdmin || roles == user.roles
                      } {
                        val lowerCasedEmail: String = request.email.getOrElse(user.email).toLowerCase()
                        provideAsync(userService.getUserByEmail(lowerCasedEmail)) { maybeUser =>
                          maybeUser.foreach { userToCheck =>
                            Validation.validate(
                              Validation.validateField(
                                field = "email",
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
        makeOperation("adminDeleteUser") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              provideAsyncOrNotFound(userService.getUser(userId)) { user =>
                provideAsync(userService.anonymize(user)) { _ =>
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
    mandatoryField("country", country),
    validateField(
      "roles",
      roles.forall(rolesValue => rolesValue.forall(r => Role.matchRole(r).isDefined)),
      s"roles should be none or some of these specified values: ${Role.roles.keys.mkString(",")}"
    )
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

  validate(
    email.map(email       => validateEmail(fieldName = "email", fieldValue = email.toLowerCase)),
    email.map(email       => validateUserInput("email", email, None)),
    firstName.map(value   => requireNonEmpty("firstName", value, Some("firstName should not be an empty string"))),
    firstName.map(value   => validateUserInput("firstName", value, None)),
    lastName.map(value    => validateUserInput("lastName", value, None)),
    country.map(country   => maxLength("country", maxCountryLength, country.value)),
    language.map(language => maxLength("language", maxLanguageLength, language.value)),
    roles.map(
      roles =>
        validateField(
          "roles",
          roles.forall(r => Role.matchRole(r).isDefined),
          s"roles should be none or some of these specified values: ${Role.roles.keys.mkString(",")}"
      )
    )
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
  implicit val encoder: ObjectEncoder[ModeratorResponse] = deriveEncoder[ModeratorResponse]
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
