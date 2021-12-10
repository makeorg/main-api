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
import org.make.api.question.QuestionServiceComponent

import javax.ws.rs.Path
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.{`X-Total-Count`, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.feature.{ActiveFeature, ActiveFeatureId, FeatureId}
import org.make.core.question.QuestionId
import org.make.core.{HttpCodes, Order, ParameterExtractors, Validation, ValidationError}
import scalaoauth2.provider.AuthInfo
import org.make.core.technical.Pagination._

import scala.annotation.meta.field
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.feature.ActiveFeatureRequest")
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

  @ApiOperation(
    value = "delete-active-feature-from-feature-id",
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
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.feature.ActiveFeatureRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @Path(value = "/")
  def adminDeleteActiveFeatureFromFeatureId: Route

  def routes: Route =
    adminGetActiveFeature ~ adminCreateActiveFeature ~ adminListActiveFeatures ~ adminDeleteActiveFeature ~ adminDeleteActiveFeatureFromFeatureId
}

trait AdminActiveFeatureApiComponent {
  def adminActiveFeatureApi: AdminActiveFeatureApi
}

trait DefaultAdminActiveFeatureApiComponent
    extends AdminActiveFeatureApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  this: MakeDirectivesDependencies
    with ActiveFeatureServiceComponent
    with FeatureServiceComponent
    with QuestionServiceComponent =>

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
                entity(as[ActiveFeatureRequest]) { request: ActiveFeatureRequest =>
                  val feature = featureService.getFeature(request.featureId)
                  val question = request.maybeQuestionId match {
                    case Some(questionId) => questionService.getCachedQuestion(questionId)
                    case None             => Future.successful(None)
                  }
                  provideAsyncOrBadRequest(
                    feature,
                    ValidationError("featureId", "not_found", Some(s"Feature ${request.featureId.value} doesn't exist"))
                  ) { _ =>
                    provideAsync(question) { foundQuestion =>
                      Validation.validateOptional(request.maybeQuestionId.map { qId =>
                        Validation.validateField(
                          "questionId",
                          "not_found",
                          foundQuestion.isDefined,
                          s"Question ${qId.value} doesn't exist"
                        )
                      })
                      provideAsync(
                        activeFeatureService
                          .find(
                            maybeQuestionId = request.maybeQuestionId.map(q => Seq(q)),
                            featureIds = Some(Seq(request.featureId))
                          )
                      ) { actives =>
                        Validation.validate(
                          Validation
                            .requireEmpty(
                              "questionId",
                              actives,
                              Some(
                                s"An active feature already exists for questionId ${request.maybeQuestionId} and featureId ${request.featureId}"
                              )
                            )
                        )
                        provideAsync(
                          activeFeatureService
                            .createActiveFeature(
                              featureId = request.featureId,
                              maybeQuestionId = request.maybeQuestionId
                            )
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
        }
      }
    }

    override def adminListActiveFeatures: Route = {
      get {
        path("admin" / "active-features") {
          makeOperation("AdminSearchActiveFeature") { _ =>
            parameters(
              "_start".as[Start].?,
              "_end".as[End].?,
              "_sort".?,
              "_order".as[Order].?,
              "questionId".as[QuestionId].?
            ) {
              (
                start: Option[Start],
                end: Option[End],
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
                            start = start.orZero,
                            end = end,
                            sort = sort,
                            order = order,
                            maybeQuestionId = maybeQuestionId.map(Seq(_))
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

    override def adminDeleteActiveFeatureFromFeatureId: Route = delete {
      path("admin" / "active-features") {
        makeOperation("AdminDeleteActiveFeatureFromFeatureId") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[ActiveFeatureRequest]) { request: ActiveFeatureRequest =>
                  val foundActiveFeature = activeFeatureService
                    .find(
                      maybeQuestionId = request.maybeQuestionId.map(questionId => Seq(questionId)),
                      featureIds = Some(Seq(request.featureId))
                    )
                    .map {
                      case Seq(head) => Some(head)
                      case _         => None
                    }
                  provideAsyncOrNotFound(foundActiveFeature) { activeFeature =>
                    provideAsync(activeFeatureService.deleteActiveFeature(activeFeature.activeFeatureId)) { _ =>
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

final case class ActiveFeatureRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "f403b357-a528-4657-b4e4-6dc07516f16a")
  featureId: FeatureId,
  @(ApiModelProperty @field)(dataType = "string", example = "afe70bf6-ae1b-4fd0-9051-0732958ca810")
  maybeQuestionId: Option[QuestionId]
)

object ActiveFeatureRequest {
  implicit val decoder: Decoder[ActiveFeatureRequest] = deriveDecoder[ActiveFeatureRequest]
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
