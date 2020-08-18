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

package org.make.api.feature

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{`X-Total-Count`, IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.feature.{ActiveFeature, ActiveFeatureId, FeatureId}
import org.make.core.question.QuestionId
import org.make.core.{HttpCodes, Order, ParameterExtractors}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

@Api(value = "Admin Active Features")
@Path(value = "/admin/active-features")
trait AdminActiveFeatureApi extends Directives {

  @Path(value = "/{activeFeatureId}")
  @ApiOperation(
    value = "get-active-feature",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ActiveFeatureResponse]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "activeFeatureId", paramType = "path", dataType = "string"))
  )
  def adminGetActiveFeature: Route

  @ApiOperation(
    value = "create-active-feature",
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
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.feature.CreateActiveFeatureRequest"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ActiveFeatureResponse]))
  )
  @Path(value = "/")
  def adminCreateActiveFeature: Route

  @ApiOperation(
    value = "list-active-features",
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
      new ApiImplicitParam(name = "questionId", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[ActiveFeatureResponse]]))
  )
  @Path(value = "/")
  def adminListActiveFeatures: Route

  @ApiOperation(
    value = "delete-active-feature",
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
    value = Array(new ApiImplicitParam(name = "activeFeatureId", paramType = "path", dataType = "string"))
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @Path(value = "/{activeFeatureId}")
  def adminDeleteActiveFeature: Route

  def routes: Route =
    adminGetActiveFeature ~ adminCreateActiveFeature ~ adminListActiveFeatures ~ adminDeleteActiveFeature
}

trait AdminActiveFeatureApiComponent {
  def adminActiveFeatureApi: AdminActiveFeatureApi
}

trait DefaultAdminActiveFeatureApiComponent
    extends AdminActiveFeatureApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  this: ActiveFeatureServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with SessionHistoryCoordinatorServiceComponent
    with MakeSettingsComponent =>

  override lazy val adminActiveFeatureApi: AdminActiveFeatureApi = new DefaultAdminActiveFeatureApi

  class DefaultAdminActiveFeatureApi extends AdminActiveFeatureApi {

    val adminActiveFeatureId: PathMatcher1[ActiveFeatureId] = Segment.map(id => ActiveFeatureId(id))

    override def adminGetActiveFeature: Route = {
      get {
        path("admin" / "active-features" / adminActiveFeatureId) { activeFeatureId =>
          makeOperation("AdminGetActiveFeature") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                provideAsyncOrNotFound(activeFeatureService.getActiveFeature(activeFeatureId)) { activeFeature =>
                  complete(ActiveFeatureResponse(activeFeature))
                }
              }
            }
          }
        }
      }
    }

    override def adminCreateActiveFeature: Route = post {
      path("admin" / "active-features") {
        makeOperation("AdminRegisterActiveFeature") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[CreateActiveFeatureRequest]) { request: CreateActiveFeatureRequest =>
                  provideAsync(
                    activeFeatureService
                      .createActiveFeature(featureId = request.featureId, maybeQuestionId = request.maybeQuestionId)
                  ) { activeFeature =>
                    complete(StatusCodes.Created -> ActiveFeatureResponse(activeFeature))
                  }
                }
              }
            }
          }
        }
      }
    }

    override def adminListActiveFeatures: Route = {
      get {
        path("admin" / "active-features") {
          makeOperation("AdminSearchActiveFeature") { _ =>
            parameters(
              ("_start".as[Int].?, "_end".as[Int].?, "_sort".?, "_order".as[Order].?, "questionId".as[QuestionId].?)
            ) {
              (
                start: Option[Int],
                end: Option[Int],
                sort: Option[String],
                order: Option[Order],
                maybeQuestionId: Option[QuestionId]
              ) =>
                makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                  requireAdminRole(userAuth.user) {
                    provideAsync(activeFeatureService.count(maybeQuestionId = maybeQuestionId)) { count =>
                      onSuccess(
                        activeFeatureService
                          .find(
                            start = start.getOrElse(0),
                            end = end,
                            sort = sort,
                            order = order,
                            maybeQuestionId = maybeQuestionId
                          )
                      ) { filteredActiveFeatures =>
                        complete(
                          (
                            StatusCodes.OK,
                            List(`X-Total-Count`(count.toString)),
                            filteredActiveFeatures.map(ActiveFeatureResponse.apply)
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

    override def adminDeleteActiveFeature: Route = delete {
      path("admin" / "active-features" / adminActiveFeatureId) { activeFeatureId =>
        makeOperation("AdminDeleteActiveFeature") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              provideAsyncOrNotFound(activeFeatureService.getActiveFeature(activeFeatureId)) { _ =>
                provideAsync(activeFeatureService.deleteActiveFeature(activeFeatureId)) { _ =>
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

case class CreateActiveFeatureRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "f403b357-a528-4657-b4e4-6dc07516f16a")
  featureId: FeatureId,
  @(ApiModelProperty @field)(dataType = "string", example = "afe70bf6-ae1b-4fd0-9051-0732958ca810")
  maybeQuestionId: Option[QuestionId]
)

object CreateActiveFeatureRequest {
  implicit val decoder: Decoder[CreateActiveFeatureRequest] = deriveDecoder[CreateActiveFeatureRequest]
}

final case class ActiveFeatureResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "1c895cb8-f4fe-45ff-a1c2-24db14324a0f")
  id: ActiveFeatureId,
  @(ApiModelProperty @field)(dataType = "string", example = "f403b357-a528-4657-b4e4-6dc07516f16a")
  featureId: FeatureId,
  @(ApiModelProperty @field)(dataType = "string", example = "afe70bf6-ae1b-4fd0-9051-0732958ca810")
  maybeQuestionId: Option[QuestionId]
)

object ActiveFeatureResponse {
  implicit val encoder: Encoder[ActiveFeatureResponse] = deriveEncoder[ActiveFeatureResponse]
  implicit val decoder: Decoder[ActiveFeatureResponse] = deriveDecoder[ActiveFeatureResponse]

  def apply(activeFeature: ActiveFeature): ActiveFeatureResponse =
    ActiveFeatureResponse(
      id = activeFeature.activeFeatureId,
      featureId = activeFeature.featureId,
      maybeQuestionId = activeFeature.maybeQuestionId
    )
}
