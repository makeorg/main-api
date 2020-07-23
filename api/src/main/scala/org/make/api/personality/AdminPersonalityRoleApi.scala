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

package org.make.api.personality

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
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
import org.make.core.personality.{
  FieldType,
  PersonalityRole,
  PersonalityRoleField,
  PersonalityRoleFieldId,
  PersonalityRoleId
}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

@Api(
  value = "Admin Personality Roles",
  authorizations = Array(
    new Authorization(
      value = "MakeApi",
      scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
    )
  )
)
@Path(value = "/admin/personality-roles")
trait AdminPersonalityRoleApi extends Directives {

  @ApiOperation(value = "get-personality-role", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityRoleResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "personalityRoleId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      )
    )
  )
  @Path(value = "/{personalityRoleId}")
  def getPersonalityRole: Route

  @ApiOperation(value = "list-personality-roles", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "name", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[PersonalityRoleResponse]]))
  )
  @Path(value = "/")
  def listPersonalityRoles: Route

  @ApiOperation(value = "create-personality-role", httpMethod = "POST", code = HttpCodes.Created)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.personality.CreatePersonalityRoleRequest"
      )
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.Created, message = "Ok", response = classOf[PersonalityRoleResponse]))
  )
  @Path(value = "/")
  def createPersonalityRole: Route

  @ApiOperation(value = "update-personality-role", httpMethod = "PUT", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "personalityRoleId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      ),
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.personality.UpdatePersonalityRoleRequest"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityRoleResponse]))
  )
  @Path(value = "/{personalityRoleId}")
  def updatePersonalityRole: Route

  @ApiOperation(value = "delete-personality-role", httpMethod = "DELETE", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "personalityRoleId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityRoleIdResponse]))
  )
  @Path(value = "/{personalityRoleId}")
  def deletePersonalityRole: Route

  @ApiOperation(value = "get-personality-role-field", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityRoleFieldResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "personalityRoleId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      ),
      new ApiImplicitParam(
        name = "personalityRoleFieldId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      )
    )
  )
  @Path(value = "/{personalityRoleId}/fields/{personalityRoleFieldId}")
  def getPersonalityRoleField: Route

  @ApiOperation(value = "list-personality-role-fields", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "personalityRoleId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      ),
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "name", paramType = "query", dataType = "string"),
      new ApiImplicitParam(
        name = "fieldType",
        paramType = "query",
        dataType = "string",
        allowableValues = "INT,STRING,BOOLEAN"
      ),
      new ApiImplicitParam(name = "required", paramType = "query", dataType = "boolean")
    )
  )
  @ApiResponses(
    value = Array(
      new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[PersonalityRoleFieldResponse]])
    )
  )
  @Path(value = "/{personalityRoleId}/fields")
  def listPersonalityRoleFields: Route

  @ApiOperation(value = "create-personality-role-field", httpMethod = "POST", code = HttpCodes.Created)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "personalityRoleId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      ),
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.personality.CreatePersonalityRoleFieldRequest"
      )
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.Created, message = "Ok", response = classOf[PersonalityRoleFieldResponse]))
  )
  @Path(value = "/{personalityRoleId}/fields")
  def createPersonalityRoleField: Route

  @ApiOperation(value = "update-personality-role-field", httpMethod = "PUT", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "personalityRoleId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      ),
      new ApiImplicitParam(
        name = "personalityRoleFieldId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      ),
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.personality.UpdatePersonalityRoleFieldRequest"
      )
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityRoleFieldResponse]))
  )
  @Path(value = "/{personalityRoleId}/fields/{personalityRoleFieldId}")
  def updatePersonalityRoleField: Route

  @ApiOperation(value = "delete-personality-role-field", httpMethod = "DELETE", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "personalityRoleId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      ),
      new ApiImplicitParam(
        name = "personalityRoleFieldId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      )
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityRoleFieldIdResponse]))
  )
  @Path(value = "/{personalityRoleId}/fields/{personalityRoleFieldId}")
  def deletePersonalityRoleField: Route

  def routes: Route =
    getPersonalityRole ~
      listPersonalityRoles ~
      createPersonalityRole ~
      updatePersonalityRole ~
      deletePersonalityRole ~
      getPersonalityRoleField ~
      listPersonalityRoleFields ~
      createPersonalityRoleField ~
      updatePersonalityRoleField ~
      deletePersonalityRoleField

}

trait AdminPersonalityRoleApiComponent {
  def adminPersonalityRoleApi: AdminPersonalityRoleApi
}

trait DefaultAdminPersonalityRoleApiComponent
    extends AdminPersonalityRoleApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  this: MakeDataHandlerComponent
    with SessionHistoryCoordinatorServiceComponent
    with PersonalityRoleServiceComponent
    with PersonalityRoleFieldServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent =>

  override lazy val adminPersonalityRoleApi: AdminPersonalityRoleApi = new DefaultAdminPersonalityRoleApi

  class DefaultAdminPersonalityRoleApi extends AdminPersonalityRoleApi {

    val personalityRoleId: PathMatcher1[PersonalityRoleId] = Segment.map(PersonalityRoleId.apply)
    val personalityRoleFieldId: PathMatcher1[PersonalityRoleFieldId] = Segment.map(PersonalityRoleFieldId.apply)

    override def getPersonalityRole: Route = get {
      path("admin" / "personality-roles" / personalityRoleId) { personalityRoleId =>
        makeOperation("GetPersonalityRole") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              provideAsyncOrNotFound(personalityRoleService.getPersonalityRole(personalityRoleId)) { personalityRole =>
                complete(PersonalityRoleResponse(personalityRole))
              }
            }
          }
        }
      }
    }

    override def listPersonalityRoles: Route = get {
      path("admin" / "personality-roles") {
        makeOperation("GetPersonalityRoles") { _ =>
          parameters(("_start".as[Int].?, "_end".as[Int].?, "_sort".?, "_order".?, "name".?)) {
            (start: Option[Int], end: Option[Int], sort: Option[String], order: Option[String], name: Option[String]) =>
              makeOAuth2 { auth: AuthInfo[UserRights] =>
                requireAdminRole(auth.user) {
                  provideAsync(personalityRoleService.count(roleIds = None, name = name)) { count =>
                    provideAsync(
                      personalityRoleService
                        .find(
                          start = start.getOrElse(0),
                          end = end,
                          sort = sort,
                          order = order,
                          roleIds = None,
                          name = name
                        )
                    ) { personalityRoles =>
                      complete(
                        (
                          StatusCodes.OK,
                          List(`X-Total-Count`(count.toString)),
                          personalityRoles.map(PersonalityRoleResponse.apply)
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

    override def createPersonalityRole: Route = post {
      path("admin" / "personality-roles") {
        makeOperation("CreatePersonalityRole") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[CreatePersonalityRoleRequest]) { request: CreatePersonalityRoleRequest =>
                  provideAsync(
                    personalityRoleService
                      .createPersonalityRole(request)
                  ) { result =>
                    complete(StatusCodes.Created -> PersonalityRoleResponse(result))
                  }
                }
              }
            }
          }
        }
      }
    }

    override def updatePersonalityRole: Route =
      put {
        path("admin" / "personality-roles" / personalityRoleId) { personalityRoleId =>
          makeOperation("UpdatePersonalityRole") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                decodeRequest {
                  entity(as[UpdatePersonalityRoleRequest]) { request: UpdatePersonalityRoleRequest =>
                    provideAsyncOrNotFound(personalityRoleService.updatePersonalityRole(personalityRoleId, request)) {
                      personalityRole: PersonalityRole =>
                        complete(StatusCodes.OK -> PersonalityRoleResponse(personalityRole))
                    }
                  }
                }
              }
            }
          }
        }
      }

    override def deletePersonalityRole: Route =
      delete {
        path("admin" / "personality-roles" / personalityRoleId) { personalityRoleId =>
          makeOperation("UpdatePersonalityRole") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                provideAsync(personalityRoleService.deletePersonalityRole(personalityRoleId)) { _ =>
                  complete(StatusCodes.OK -> PersonalityRoleIdResponse(personalityRoleId))
                }
              }
            }
          }
        }
      }

    override def getPersonalityRoleField: Route = get {
      path("admin" / "personality-roles" / personalityRoleId / "fields" / personalityRoleFieldId) {
        (personalityRoleId, personalityRoleFieldId) =>
          makeOperation("GetPersonalityRoleField") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsyncOrNotFound(
                  personalityRoleFieldService.getPersonalityRoleField(personalityRoleFieldId, personalityRoleId)
                ) { personalityRoleField =>
                  complete(PersonalityRoleFieldResponse(personalityRoleField))
                }
              }
            }
          }
      }
    }

    override def listPersonalityRoleFields: Route = get {
      path("admin" / "personality-roles" / personalityRoleId / "fields") { personalityRoleId =>
        makeOperation("GetPersonalityRoleFields") { _ =>
          parameters(
            (
              "_start".as[Int].?,
              "_end".as[Int].?,
              "_sort".?,
              "_order".?,
              "name".?,
              "fieldType".as[FieldType].?,
              "required".as[Boolean].?
            )
          ) {
            (
              start: Option[Int],
              end: Option[Int],
              sort: Option[String],
              order: Option[String],
              name: Option[String],
              fieldType: Option[FieldType],
              required: Option[Boolean]
            ) =>
              makeOAuth2 { auth: AuthInfo[UserRights] =>
                requireAdminRole(auth.user) {
                  provideAsync(
                    personalityRoleFieldService
                      .count(
                        personalityRoleId = Some(personalityRoleId),
                        name = name,
                        fieldType = fieldType,
                        required = required
                      )
                  ) { count =>
                    provideAsync(
                      personalityRoleFieldService
                        .find(
                          start = start.getOrElse(0),
                          end = end,
                          sort = sort,
                          order = order,
                          personalityRoleId = Some(personalityRoleId),
                          name = name,
                          fieldType = fieldType,
                          required = required
                        )
                    ) { personalityRoleFields =>
                      complete(
                        (
                          StatusCodes.OK,
                          List(`X-Total-Count`(count.toString)),
                          personalityRoleFields.map(PersonalityRoleFieldResponse.apply)
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

    override def createPersonalityRoleField: Route = post {
      path("admin" / "personality-roles" / personalityRoleId / "fields") { personalityRoleId =>
        makeOperation("CreatePersonalityRoleField") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[CreatePersonalityRoleFieldRequest]) { request: CreatePersonalityRoleFieldRequest =>
                  provideAsync(
                    personalityRoleFieldService
                      .createPersonalityRoleField(personalityRoleId, request)
                  ) { result =>
                    complete(StatusCodes.Created -> PersonalityRoleFieldResponse(result))
                  }
                }
              }
            }
          }
        }
      }
    }

    override def updatePersonalityRoleField: Route =
      put {
        path("admin" / "personality-roles" / personalityRoleId / "fields" / personalityRoleFieldId) {
          (personalityRoleId, personalityRoleFieldId) =>
            makeOperation("UpdatePersonalityRoleField") { _ =>
              makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                requireAdminRole(userAuth.user) {
                  decodeRequest {
                    entity(as[UpdatePersonalityRoleFieldRequest]) { request: UpdatePersonalityRoleFieldRequest =>
                      provideAsyncOrNotFound(
                        personalityRoleFieldService
                          .updatePersonalityRoleField(personalityRoleFieldId, personalityRoleId, request)
                      ) { personalityRoleField =>
                        complete(StatusCodes.OK -> PersonalityRoleFieldResponse(personalityRoleField))
                      }
                    }
                  }
                }
              }
            }
        }
      }

    override def deletePersonalityRoleField: Route =
      delete {
        path("admin" / "personality-roles" / personalityRoleId / "fields" / personalityRoleFieldId) {
          (_, personalityRoleFieldId) =>
            makeOperation("UpdatePersonalityRoleField") { _ =>
              makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                requireAdminRole(userAuth.user) {
                  provideAsync(
                    personalityRoleFieldService
                      .deletePersonalityRoleField(personalityRoleFieldId)
                  ) { _ =>
                    complete(StatusCodes.OK -> PersonalityRoleFieldIdResponse(personalityRoleFieldId))
                  }
                }
              }
            }
        }
      }

  }

}

final case class CreatePersonalityRoleRequest(name: String) {
  validate(validateUserInput("name", name, None))
}

object CreatePersonalityRoleRequest {
  implicit val decoder: Decoder[CreatePersonalityRoleRequest] = deriveDecoder[CreatePersonalityRoleRequest]
}

final case class UpdatePersonalityRoleRequest(name: String) {
  validate(validateUserInput("name", name, None))
}

object UpdatePersonalityRoleRequest {
  implicit val decoder: Decoder[UpdatePersonalityRoleRequest] = deriveDecoder[UpdatePersonalityRoleRequest]
}

final case class CreatePersonalityRoleFieldRequest(
  name: String,
  @(ApiModelProperty @field)(dataType = "string", example = "STRING", allowableValues = "INT,STRING,BOOLEAN")
  fieldType: FieldType,
  required: Boolean
) {
  validate(validateUserInput("name", name, None))
}

object CreatePersonalityRoleFieldRequest {
  implicit val decoder: Decoder[CreatePersonalityRoleFieldRequest] = deriveDecoder[CreatePersonalityRoleFieldRequest]
}

final case class UpdatePersonalityRoleFieldRequest(
  name: String,
  @(ApiModelProperty @field)(dataType = "string", example = "STRING", allowableValues = "INT,STRING,BOOLEAN")
  fieldType: FieldType,
  required: Boolean
) {
  validate(validateUserInput("name", name, None))
}

object UpdatePersonalityRoleFieldRequest {
  implicit val decoder: Decoder[UpdatePersonalityRoleFieldRequest] = deriveDecoder[UpdatePersonalityRoleFieldRequest]
}

case class PersonalityRoleResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d22c8e70-f709-42ff-8a52-9398d159c753") id: PersonalityRoleId,
  name: String
)

object PersonalityRoleResponse {
  implicit val encoder: Encoder[PersonalityRoleResponse] = deriveEncoder[PersonalityRoleResponse]
  implicit val decoder: Decoder[PersonalityRoleResponse] = deriveDecoder[PersonalityRoleResponse]

  def apply(personalityRole: PersonalityRole): PersonalityRoleResponse =
    PersonalityRoleResponse(id = personalityRole.personalityRoleId, name = personalityRole.name)
}

case class PersonalityRoleIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d22c8e70-f709-42ff-8a52-9398d159c753")
  id: PersonalityRoleId
)

object PersonalityRoleIdResponse {
  implicit val encoder: Encoder[PersonalityRoleIdResponse] = deriveEncoder[PersonalityRoleIdResponse]
  implicit val decoder: Decoder[PersonalityRoleIdResponse] = deriveDecoder[PersonalityRoleIdResponse]
}

case class PersonalityRoleFieldResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d22c8e70-f709-42ff-8a52-9398d159c753") id: PersonalityRoleFieldId,
  @(ApiModelProperty @field)(dataType = "string", example = "0226897d-c137-4e20-ade0-af40426764a4") personalityRoleId: PersonalityRoleId,
  name: String,
  @(ApiModelProperty @field)(dataType = "string", example = "STRING") fieldType: FieldType,
  required: Boolean
)

object PersonalityRoleFieldResponse {
  implicit val encoder: Encoder[PersonalityRoleFieldResponse] = deriveEncoder[PersonalityRoleFieldResponse]
  implicit val decoder: Decoder[PersonalityRoleFieldResponse] = deriveDecoder[PersonalityRoleFieldResponse]

  def apply(personalityRoleField: PersonalityRoleField): PersonalityRoleFieldResponse =
    PersonalityRoleFieldResponse(
      id = personalityRoleField.personalityRoleFieldId,
      personalityRoleId = personalityRoleField.personalityRoleId,
      name = personalityRoleField.name,
      fieldType = personalityRoleField.fieldType,
      required = personalityRoleField.required
    )
}

case class PersonalityRoleFieldIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d22c8e70-f709-42ff-8a52-9398d159c753")
  id: PersonalityRoleFieldId
)

object PersonalityRoleFieldIdResponse {
  implicit val encoder: Encoder[PersonalityRoleFieldIdResponse] = deriveEncoder[PersonalityRoleFieldIdResponse]
  implicit val decoder: Decoder[PersonalityRoleFieldIdResponse] = deriveDecoder[PersonalityRoleFieldIdResponse]
}
