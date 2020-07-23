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

package org.make.api.operation

import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Url
import eu.timepit.refined.auto._
import io.circe.refined._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, PathMatcher1, Route}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{`X-Total-Count`, IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.Validation._
import org.make.core.auth.UserRights
import org.make.core.operation.{CurrentOperation, CurrentOperationId}
import org.make.core.question.QuestionId
import org.make.core.{HttpCodes, ParameterExtractors, Requirement}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

@Api(
  value = "Admin Current Operation",
  authorizations = Array(
    new Authorization(
      value = "MakeApi",
      scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
    )
  )
)
@Path(value = "/admin/views/home/current-operations")
trait AdminCurrentOperationApi extends Directives {
  @ApiOperation(value = "post-current-operation", httpMethod = "POST", code = HttpCodes.Created)
  @ApiResponses(
    value = Array(
      new ApiResponse(code = HttpCodes.Created, message = "Created", response = classOf[CurrentOperationIdResponse])
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.operation.CreateCurrentOperationRequest"
      )
    )
  )
  @Path(value = "/")
  def adminPostCurrentOperation: Route

  @ApiOperation(value = "put-current-operation", httpMethod = "PUT", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[CurrentOperationIdResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "currentOperationId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.operation.UpdateCurrentOperationRequest"
      )
    )
  )
  @Path(value = "/{currentOperationId}")
  def adminPutCurrentOperation: Route

  @ApiOperation(value = "get-current-operations", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[CurrentOperationResponse]]))
  )
  @Path(value = "/")
  def adminGetCurrentOperations: Route

  @ApiOperation(value = "get-current-operation", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[CurrentOperationResponse]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "currentOperationId", paramType = "path", dataType = "string"))
  )
  @Path(value = "/{currentOperationId}")
  def adminGetCurrentOperation: Route

  @ApiOperation(value = "delete-current-operation", httpMethod = "DELETE", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[CurrentOperationIdResponse]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "currentOperationId", paramType = "path", dataType = "string"))
  )
  @Path(value = "/{currentOperationId}")
  def adminDeleteCurrentOperation: Route

  def routes: Route =
    adminPostCurrentOperation ~
      adminPutCurrentOperation ~
      adminGetCurrentOperations ~
      adminGetCurrentOperation ~
      adminDeleteCurrentOperation
}

trait AdminCurrentOperationApiComponent {
  def adminCurrentOperationApi: AdminCurrentOperationApi
}

trait DefaultAdminCurrentOperationApiComponent
    extends AdminCurrentOperationApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  this: CurrentOperationServiceComponent
    with MakeDataHandlerComponent
    with SessionHistoryCoordinatorServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent =>

  override lazy val adminCurrentOperationApi: AdminCurrentOperationApi = new DefaultAdminCurrentOperationApi

  class DefaultAdminCurrentOperationApi extends AdminCurrentOperationApi {

    val currentOperationId: PathMatcher1[CurrentOperationId] = Segment.map(id => CurrentOperationId(id))

    override def adminPostCurrentOperation: Route = {
      post {
        path("admin" / "views" / "home" / "current-operations") {
          makeOperation("AdminPostCurrentOperation") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[CreateCurrentOperationRequest]) { request =>
                    onSuccess(currentOperationService.create(request)) { result =>
                      complete(StatusCodes.Created -> CurrentOperationIdResponse(result.currentOperationId))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def adminPutCurrentOperation: Route = {
      put {
        path("admin" / "views" / "home" / "current-operations" / currentOperationId) { id =>
          makeOperation("AdminPutCurrentOperation") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[UpdateCurrentOperationRequest]) { request =>
                    provideAsyncOrNotFound(currentOperationService.update(id, request)) { result =>
                      complete(StatusCodes.OK -> CurrentOperationIdResponse(result.currentOperationId))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def adminGetCurrentOperation: Route = {
      get {
        path("admin" / "views" / "home" / "current-operations" / currentOperationId) { id =>
          makeOperation("AdminGetCurrentOperation") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsyncOrNotFound(currentOperationService.getCurrentOperation(id)) { result =>
                  complete(StatusCodes.OK -> CurrentOperationResponse.apply(result))
                }
              }
            }
          }
        }
      }
    }

    override def adminGetCurrentOperations: Route = {
      get {
        path("admin" / "views" / "home" / "current-operations") {
          makeOperation("AdminGetCurrentOperations") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsync(currentOperationService.getAll) { result =>
                  complete(
                    (
                      StatusCodes.OK,
                      List(`X-Total-Count`(result.size.toString)),
                      result.map(CurrentOperationResponse.apply)
                    )
                  )
                }
              }
            }
          }
        }
      }
    }

    override def adminDeleteCurrentOperation: Route = {
      delete {
        path("admin" / "views" / "home" / "current-operations" / currentOperationId) { id =>
          makeOperation("AdminDeleteCurrentOperation") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsyncOrNotFound(currentOperationService.getCurrentOperation(id)) { _ =>
                  provideAsync(currentOperationService.delete(id)) { _ =>
                    complete(StatusCodes.OK -> CurrentOperationIdResponse(id))
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

final case class CreateCurrentOperationRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  questionId: QuestionId,
  description: String,
  label: String,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/picture.png")
  picture: String Refined Url,
  altPicture: String,
  linkLabel: String,
  internalLink: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/external-link")
  externalLink: Option[String Refined Url]
) {
  validate(
    validateUserInput("description", description, None),
    validateUserInput("label", label, None),
    validateUserInput("picture", picture, None),
    validateUserInput("altPicture", altPicture, None),
    validateUserInput("linkLabel", linkLabel, None),
    validateOptionalUserInput("internalLink", internalLink, None),
    validateOptionalUserInput("externalLink", externalLink.map(_.value), None),
    maxLength("description", 130, description),
    maxLength("label", 25, label),
    maxLength("altPicture", 80, altPicture),
    maxLength("linkLabel", 25, linkLabel),
    Requirement(
      "internalLink / externalLink",
      "invalid_link",
      () => internalLink.isDefined && externalLink.isEmpty || internalLink.isEmpty && externalLink.isDefined,
      () => "Only one between internal or external link must be defined"
    )
  )
}

object CreateCurrentOperationRequest {
  implicit val decoder: Decoder[CreateCurrentOperationRequest] = deriveDecoder[CreateCurrentOperationRequest]
}

final case class UpdateCurrentOperationRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  questionId: QuestionId,
  description: String,
  label: String,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/picture.png")
  picture: String Refined Url,
  altPicture: String,
  linkLabel: String,
  internalLink: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/external-link")
  externalLink: Option[String Refined Url]
) {
  validate(
    validateUserInput("description", description, None),
    validateUserInput("label", label, None),
    validateUserInput("picture", picture, None),
    validateUserInput("altPicture", altPicture, None),
    validateUserInput("linkLabel", linkLabel, None),
    validateOptionalUserInput("internalLink", internalLink, None),
    validateOptionalUserInput("externalLink", externalLink.map(_.value), None),
    maxLength("description", 130, description),
    maxLength("label", 25, label),
    maxLength("altPicture", 80, altPicture),
    maxLength("linkLabel", 25, linkLabel),
    Requirement(
      "internalLink / externalLink",
      "invalid_link",
      () => internalLink.isDefined && externalLink.isEmpty || internalLink.isEmpty && externalLink.isDefined,
      () => "Only one between internal or external link must be defined"
    )
  )
}

object UpdateCurrentOperationRequest {
  implicit val decoder: Decoder[UpdateCurrentOperationRequest] = deriveDecoder[UpdateCurrentOperationRequest]
}

final case class CurrentOperationResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  id: CurrentOperationId,
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  questionId: QuestionId,
  description: String,
  label: String,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/picture.png")
  picture: String,
  altPicture: String,
  linkLabel: String,
  internalLink: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/external-link")
  externalLink: Option[String]
)

object CurrentOperationResponse {
  implicit val encoder: Encoder[CurrentOperationResponse] = deriveEncoder[CurrentOperationResponse]

  def apply(currentOperation: CurrentOperation): CurrentOperationResponse = {
    CurrentOperationResponse(
      id = currentOperation.currentOperationId,
      questionId = currentOperation.questionId,
      description = currentOperation.description,
      label = currentOperation.label,
      picture = currentOperation.picture,
      altPicture = currentOperation.altPicture,
      linkLabel = currentOperation.linkLabel,
      internalLink = currentOperation.internalLink,
      externalLink = currentOperation.externalLink
    )
  }
}

final case class CurrentOperationIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  currentOperationId: CurrentOperationId
)

object CurrentOperationIdResponse {
  implicit val encoder: Encoder[CurrentOperationIdResponse] = deriveEncoder[CurrentOperationIdResponse]
}
