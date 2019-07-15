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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, PathMatcher1, Route}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, TotalCountHeader}
import org.make.core.Validation._
import org.make.core.auth.UserRights
import org.make.core.operation.{FeaturedOperation, FeaturedOperationId}
import org.make.core.question.QuestionId
import org.make.core.{HttpCodes, ParameterExtractors, Requirement}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

@Api(
  value = "Admin Featured Operation",
  authorizations = Array(
    new Authorization(
      value = "MakeApi",
      scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
    )
  )
)
@Path(value = "/admin/views/home/featured-operations")
trait AdminFeaturedOperationApi extends Directives {
  @ApiOperation(value = "post-featured-operation", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(
      new ApiResponse(code = HttpCodes.Created, message = "Created", response = classOf[FeaturedOperationIdResponse])
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.operation.CreateFeaturedOperationRequest"
      )
    )
  )
  @Path(value = "/")
  def adminPostFeaturedOperation: Route

  @ApiOperation(value = "put-featured-operation", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[FeaturedOperationIdResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.operation.UpdateFeaturedOperationRequest"
      ),
      new ApiImplicitParam(name = "featuredOperationId", paramType = "path", dataType = "string")
    )
  )
  @Path(value = "/{featuredOperationId}")
  def adminPutFeaturedOperation: Route

  @ApiOperation(value = "get-featured-operations", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[FeaturedOperationResponse]]))
  )
  @Path(value = "/")
  def adminGetFeaturedOperations: Route

  @ApiOperation(value = "get-featured-operation", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[FeaturedOperationResponse]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "featuredOperationId", paramType = "path", dataType = "string"))
  )
  @Path(value = "/{featuredOperationId}")
  def adminGetFeaturedOperation: Route

  @ApiOperation(value = "delete-featured-operation", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "NoContent")))
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "featuredOperationId", paramType = "path", dataType = "string"))
  )
  @Path(value = "/{featuredOperationId}")
  def adminDeleteFeaturedOperation: Route

  def routes: Route =
    adminPostFeaturedOperation ~
      adminPutFeaturedOperation ~
      adminGetFeaturedOperations ~
      adminGetFeaturedOperation ~
      adminDeleteFeaturedOperation
}

trait AdminFeaturedOperationApiComponent {
  def adminFeaturedOperationApi: AdminFeaturedOperationApi
}

trait DefaultAdminFeaturedOperationApiComponent
    extends AdminFeaturedOperationApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  this: FeaturedOperationServiceComponent
    with MakeDataHandlerComponent
    with SessionHistoryCoordinatorServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent =>

  override val adminFeaturedOperationApi: AdminFeaturedOperationApi = new DefaultAdminFeaturedOperationApi

  class DefaultAdminFeaturedOperationApi extends AdminFeaturedOperationApi {

    val featuredOperationId: PathMatcher1[FeaturedOperationId] = Segment.map(id => FeaturedOperationId(id))

    override def adminPostFeaturedOperation: Route = {
      post {
        path("admin" / "views" / "home" / "featured-operations") {
          makeOperation("AdminPostFeaturedOperation") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[CreateFeaturedOperationRequest]) { request =>
                    onSuccess(featuredOperationService.create(request)) { result =>
                      complete(StatusCodes.Created -> FeaturedOperationIdResponse(result.featuredOperationId))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def adminPutFeaturedOperation: Route = {
      put {
        path("admin" / "views" / "home" / "featured-operations" / featuredOperationId) { id =>
          makeOperation("AdminPutFeaturedOperation") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[UpdateFeaturedOperationRequest]) { request =>
                    provideAsyncOrNotFound(featuredOperationService.update(id, request)) { result =>
                      complete(StatusCodes.OK -> FeaturedOperationIdResponse(result.featuredOperationId))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def adminGetFeaturedOperation: Route = {
      get {
        path("admin" / "views" / "home" / "featured-operations" / featuredOperationId) { id =>
          makeOperation("AdminGetFeaturedOperation") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsyncOrNotFound(featuredOperationService.getFeaturedOperation(id)) { result =>
                  complete(StatusCodes.OK -> FeaturedOperationResponse.apply(result))
                }
              }
            }
          }
        }
      }
    }

    override def adminGetFeaturedOperations: Route = {
      get {
        path("admin" / "views" / "home" / "featured-operations") {
          makeOperation("AdminGetFeaturedOperations") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsync(featuredOperationService.getAll) { result =>
                  complete(
                    (
                      StatusCodes.OK,
                      List(TotalCountHeader(result.size.toString)),
                      result.map(FeaturedOperationResponse.apply)
                    )
                  )
                }
              }
            }
          }
        }
      }
    }

    override def adminDeleteFeaturedOperation: Route = {
      delete {
        path("admin" / "views" / "home" / "featured-operations" / featuredOperationId) { id =>
          makeOperation("AdminDeleteFeaturedOperation") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsyncOrNotFound(featuredOperationService.getFeaturedOperation(id)) { _ =>
                  provideAsync(featuredOperationService.delete(id)) { _ =>
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

final case class CreateFeaturedOperationRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  questionId: Option[QuestionId],
  title: String,
  description: Option[String],
  landscapePicture: String,
  portraitPicture: String,
  altPicture: String,
  label: String,
  buttonLabel: String,
  internalLink: Option[String],
  externalLink: Option[String],
  slot: Int
) {
  validate(
    maxLength("title", 90, title),
    maxLength("description", 140, description.getOrElse("")),
    maxLength("label", 25, label),
    maxLength("buttonLabel", 25, buttonLabel),
    maxLength("altPicture", 130, altPicture),
    Requirement(
      "questionId",
      () => internalLink.isDefined && questionId.isDefined || internalLink.isEmpty,
      () => "QuestionId must be defined when an internalLink is defined"
    ),
    Requirement(
      "internalLink / externalLink",
      () => internalLink.isDefined && externalLink.isEmpty || internalLink.isEmpty && externalLink.isDefined,
      () => "Only one between internal or external link must be defined"
    )
  )
}

object CreateFeaturedOperationRequest {
  implicit val decoder: Decoder[CreateFeaturedOperationRequest] = deriveDecoder[CreateFeaturedOperationRequest]
}

final case class UpdateFeaturedOperationRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  questionId: Option[QuestionId],
  title: String,
  description: Option[String],
  landscapePicture: String,
  portraitPicture: String,
  altPicture: String,
  label: String,
  buttonLabel: String,
  internalLink: Option[String],
  externalLink: Option[String],
  slot: Int
) {
  validate(
    maxLength("title", 90, title),
    maxLength("description", 140, description.getOrElse("")),
    maxLength("label", 25, label),
    maxLength("buttonLabel", 25, buttonLabel),
    maxLength("altPicture", 130, altPicture),
    Requirement(
      "questionId",
      () => internalLink.isDefined && questionId.isDefined || internalLink.isEmpty,
      () => "QuestionId must be defined when an internalLink is defined"
    ),
    Requirement(
      "internalLink / externalLink",
      () => internalLink.isDefined && externalLink.isEmpty || internalLink.isEmpty && externalLink.isDefined,
      () => "Only one between internal or external link must be defined"
    )
  )
}

object UpdateFeaturedOperationRequest {
  implicit val decoder: Decoder[UpdateFeaturedOperationRequest] = deriveDecoder[UpdateFeaturedOperationRequest]
}

final case class FeaturedOperationResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  id: FeaturedOperationId,
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  questionId: Option[QuestionId],
  title: String,
  description: Option[String],
  landscapePicture: String,
  portraitPicture: String,
  altPicture: String,
  label: String,
  buttonLabel: String,
  internalLink: Option[String],
  externalLink: Option[String],
  slot: Int
)

object FeaturedOperationResponse {
  implicit val encoder: Encoder[FeaturedOperationResponse] = deriveEncoder[FeaturedOperationResponse]

  def apply(featuredOperation: FeaturedOperation): FeaturedOperationResponse = {
    FeaturedOperationResponse(
      id = featuredOperation.featuredOperationId,
      questionId = featuredOperation.questionId,
      title = featuredOperation.title,
      description = featuredOperation.description,
      landscapePicture = featuredOperation.landscapePicture,
      altPicture = featuredOperation.altPicture,
      portraitPicture = featuredOperation.portraitPicture,
      label = featuredOperation.label,
      buttonLabel = featuredOperation.buttonLabel,
      internalLink = featuredOperation.internalLink,
      externalLink = featuredOperation.externalLink,
      slot = featuredOperation.slot
    )
  }
}

final case class FeaturedOperationIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  featuredOperationId: FeaturedOperationId
)

object FeaturedOperationIdResponse {
  implicit val encoder: Encoder[FeaturedOperationIdResponse] = deriveEncoder[FeaturedOperationIdResponse]
}
