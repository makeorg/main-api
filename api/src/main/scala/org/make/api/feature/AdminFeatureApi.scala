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
import org.make.core.feature.{Feature, FeatureId}
import org.make.core.{HttpCodes, Order, ParameterExtractors, Validation}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

@Api(value = "Admin Features")
@Path(value = "/admin/features")
trait AdminFeatureApi extends Directives {

  @Path(value = "/{featureId}")
  @ApiOperation(
    value = "get-feature",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[FeatureResponse]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "featureId", paramType = "path", dataType = "string")))
  def adminGetFeature: Route

  @ApiOperation(
    value = "create-feature",
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
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.feature.CreateFeatureRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[FeatureResponse]))
  )
  @Path(value = "/")
  def adminCreateFeature: Route

  @ApiOperation(
    value = "list-features",
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
      new ApiImplicitParam(name = "slug", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[FeatureResponse]]))
  )
  @Path(value = "/")
  def adminListFeatures: Route

  @ApiOperation(
    value = "update-feature",
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
      new ApiImplicitParam(name = "featureId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.feature.UpdateFeatureRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[FeatureResponse]))
  )
  @Path(value = "/{featureId}")
  def adminUpdateFeature: Route

  @ApiOperation(
    value = "delete-feature",
    httpMethod = "DELETE",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "featureId", paramType = "path", dataType = "string")))
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @Path(value = "/{featureId}")
  def adminDeleteFeature: Route

  def routes: Route = adminGetFeature ~ adminCreateFeature ~ adminListFeatures ~ adminUpdateFeature ~ adminDeleteFeature
}

trait AdminFeatureApiComponent {
  def adminFeatureApi: AdminFeatureApi
}

trait DefaultAdminFeatureApiComponent
    extends AdminFeatureApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  this: FeatureServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with SessionHistoryCoordinatorServiceComponent
    with MakeSettingsComponent =>

  override lazy val adminFeatureApi: AdminFeatureApi = new DefaultAdminFeatureApi

  class DefaultAdminFeatureApi extends AdminFeatureApi {

    val adminFeatureId: PathMatcher1[FeatureId] = Segment.map(id => FeatureId(id))

    override def adminGetFeature: Route = {
      get {
        path("admin" / "features" / adminFeatureId) { featureId =>
          makeOperation("AdminGetFeature") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                provideAsyncOrNotFound(featureService.getFeature(featureId)) { feature =>
                  complete(FeatureResponse(feature))
                }
              }
            }
          }
        }
      }
    }

    override def adminCreateFeature: Route = post {
      path("admin" / "features") {
        makeOperation("AdminRegisterFeature") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[CreateFeatureRequest]) { request: CreateFeatureRequest =>
                  provideAsync(featureService.findBySlug(request.slug)) { featureList =>
                    Validation.validate(
                      Validation.requireEmpty(
                        fieldName = "slug",
                        fieldValue = featureList,
                        message = Some("Feature slug already exists in this context. Duplicates are not allowed")
                      ),
                      Validation.validateUserInput("name", request.name, None)
                    )
                    onSuccess(featureService.createFeature(name = request.name, slug = request.slug)) { feature =>
                      complete(StatusCodes.Created -> FeatureResponse(feature))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def adminListFeatures: Route = {
      get {
        path("admin" / "features") {
          makeOperation("AdminSearchFeature") { _ =>
            parameters(("_start".as[Int].?, "_end".as[Int].?, "_sort".?, "_order".as[Order].?, "slug".?)) {
              (
                start: Option[Int],
                end: Option[Int],
                sort: Option[String],
                order: Option[Order],
                maybeSlug: Option[String]
              ) =>
                makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                  requireAdminRole(userAuth.user) {
                    provideAsync(featureService.count(slug = maybeSlug)) { count =>
                      onSuccess(
                        featureService
                          .find(start = start.getOrElse(0), end = end, sort = sort, order = order, slug = maybeSlug)
                      ) { filteredFeatures =>
                        complete(
                          (
                            StatusCodes.OK,
                            List(`X-Total-Count`(count.toString)),
                            filteredFeatures.map(FeatureResponse.apply)
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

    override def adminUpdateFeature: Route = put {
      path("admin" / "features" / adminFeatureId) { featureId =>
        makeOperation("AdminUpdateFeature") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[UpdateFeatureRequest]) { request: UpdateFeatureRequest =>
                  provideAsync(featureService.findBySlug(request.slug)) { featureList =>
                    Validation.validate(
                      Validation.requireEmpty(
                        fieldName = "slug",
                        fieldValue = featureList.filterNot(feature => feature.featureId == featureId),
                        message = Some("Feature slug already exists in this context. Duplicates are not allowed")
                      )
                    )
                    provideAsyncOrNotFound(
                      featureService.updateFeature(featureId = featureId, slug = request.slug, name = request.name)
                    ) { feature =>
                      complete(FeatureResponse(feature))
                    }
                  }
                }
              }

            }
          }
        }
      }
    }

    override def adminDeleteFeature: Route = delete {
      path("admin" / "features" / adminFeatureId) { featureId =>
        makeOperation("AdminDeleteFeature") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              provideAsyncOrNotFound(featureService.getFeature(featureId)) { _ =>
                provideAsync(featureService.deleteFeature(featureId)) { _ =>
                  complete(StatusCodes.OK -> FeatureIdResponse(featureId))
                }
              }
            }
          }
        }
      }
    }
  }
}

final case class CreateFeatureRequest(name: String, slug: String)

object CreateFeatureRequest {
  implicit val decoder: Decoder[CreateFeatureRequest] = deriveDecoder[CreateFeatureRequest]
}

final case class UpdateFeatureRequest(name: String, slug: String)

object UpdateFeatureRequest {
  implicit val decoder: Decoder[UpdateFeatureRequest] = deriveDecoder[UpdateFeatureRequest]
}

final case class FeatureResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "1c895cb8-f4fe-45ff-a1c2-24db14324a0f") id: FeatureId,
  name: String,
  slug: String
)

object FeatureResponse {
  implicit val encoder: Encoder[FeatureResponse] = deriveEncoder[FeatureResponse]
  implicit val decoder: Decoder[FeatureResponse] = deriveDecoder[FeatureResponse]

  def apply(feature: Feature): FeatureResponse =
    FeatureResponse(id = feature.featureId, name = feature.name, slug = feature.slug)
}

final case class FeatureIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  featureId: FeatureId
)

object FeatureIdResponse {
  implicit val encoder: Encoder[FeatureIdResponse] = deriveEncoder[FeatureIdResponse]
}
