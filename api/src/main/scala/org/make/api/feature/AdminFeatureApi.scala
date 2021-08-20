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
import org.make.api.question.{ModerationQuestionResponse, QuestionServiceComponent, SearchQuestionRequest}

import javax.ws.rs.Path
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.{`X-Total-Count`, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.feature.{Feature, FeatureId, FeatureSlug}
import org.make.core.question.Question
import org.make.core.{HttpCodes, Order, ParameterExtractors, Validation}
import scalaoauth2.provider.AuthInfo
import org.make.core.technical.Pagination._

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
  this: MakeDirectivesDependencies
    with FeatureServiceComponent
    with ActiveFeatureServiceComponent
    with QuestionServiceComponent =>

  override lazy val adminFeatureApi: AdminFeatureApi = new DefaultAdminFeatureApi

  class DefaultAdminFeatureApi extends AdminFeatureApi {

    val adminFeatureId: PathMatcher1[FeatureId] = Segment.map(id => FeatureId(id))

    override def adminGetFeature: Route = {
      get {
        path("admin" / "features" / adminFeatureId) { featureId =>
          makeOperation("AdminGetFeature") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                val futureFeature = featureService.getFeature(featureId)
                val futureActiveFeatures = activeFeatureService.find(featureIds = Some(Seq(featureId)))
                provideAsyncOrNotFound(futureFeature) { feature =>
                  provideAsync(futureActiveFeatures) { activeFeatures =>
                    val questionIds = activeFeatures.flatMap(_.maybeQuestionId).distinct
                    provideAsync(questionService.searchQuestion(SearchQuestionRequest(Some(questionIds)))) {
                      questions =>
                        complete(FeatureResponse(feature, questions))
                    }
                  }
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
                      complete(StatusCodes.Created -> FeatureResponse(feature, Seq.empty))
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
            parameters("_start".as[Start].?, "_end".as[End].?, "_sort".?, "_order".as[Order].?, "slug".?) {
              (
                start: Option[Start],
                end: Option[End],
                sort: Option[String],
                order: Option[Order],
                maybeSlug: Option[String]
              ) =>
                makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                  requireAdminRole(userAuth.user) {
                    val futureCount = featureService.count(slug = maybeSlug)
                    val futureFeatures = featureService
                      .find(start = start.orZero, end = end, sort = sort, order = order, slug = maybeSlug)

                    provideAsync(futureCount) { count =>
                      onSuccess(futureFeatures) { filteredFeatures =>
                        provideAsync(activeFeatureService.find(featureIds = Some(filteredFeatures.map(_.featureId)))) {
                          activeFeatures =>
                            val questionIds = activeFeatures.flatMap(_.maybeQuestionId).distinct
                            provideAsync(questionService.searchQuestion(SearchQuestionRequest(Some(questionIds)))) {
                              questions =>
                                val questionsByFeature =
                                  activeFeatures.groupMapReduce(_.featureId)(af => Set.from(af.maybeQuestionId))(_ ++ _)
                                val response = filteredFeatures.map { feature =>
                                  FeatureResponse(
                                    feature = feature,
                                    questions = questions.filter(
                                      q =>
                                        questionsByFeature
                                          .getOrElse(feature.featureId, Set.empty)
                                          .contains(q.questionId)
                                    )
                                  )
                                }
                                complete((StatusCodes.OK, List(`X-Total-Count`(count.toString)), response))
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
                    val futureFeature =
                      featureService.updateFeature(featureId = featureId, slug = request.slug, name = request.name)
                    val futureActiveFeatures = activeFeatureService.find(featureIds = Some(Seq(featureId)))
                    provideAsyncOrNotFound(futureFeature) { feature =>
                      provideAsync(futureActiveFeatures) { activeFeatures =>
                        val questionIds = activeFeatures.flatMap(_.maybeQuestionId).distinct
                        provideAsync(questionService.searchQuestion(SearchQuestionRequest(Some(questionIds)))) {
                          questions =>
                            complete(FeatureResponse(feature, questions))
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

final case class CreateFeatureRequest(name: String, slug: FeatureSlug) {
  Validation.validate(Validation.requireNonEmpty("slug", slug.value, Some("Slug must not be empty")))
}

object CreateFeatureRequest {
  implicit val decoder: Decoder[CreateFeatureRequest] = deriveDecoder[CreateFeatureRequest]
}

final case class UpdateFeatureRequest(name: String, slug: FeatureSlug) {
  Validation.validate(Validation.requireNonEmpty("slug", slug.value, Some("Slug must not be empty")))
}

object UpdateFeatureRequest {
  implicit val decoder: Decoder[UpdateFeatureRequest] = deriveDecoder[UpdateFeatureRequest]
}

final case class FeatureResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "1c895cb8-f4fe-45ff-a1c2-24db14324a0f") id: FeatureId,
  name: String,
  @(ApiModelProperty @field)(dataType = "string", example = "sequence-custom-data-segment")
  slug: FeatureSlug,
  questions: Seq[ModerationQuestionResponse]
)

object FeatureResponse {
  implicit val encoder: Encoder[FeatureResponse] = deriveEncoder[FeatureResponse]
  implicit val decoder: Decoder[FeatureResponse] = deriveDecoder[FeatureResponse]

  def apply(feature: Feature, questions: Seq[Question]): FeatureResponse =
    FeatureResponse(
      id = feature.featureId,
      name = feature.name,
      slug = feature.slug,
      questions = questions.map(ModerationQuestionResponse.apply)
    )
}

final case class FeatureIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  id: FeatureId,
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  featureId: FeatureId
)

object FeatureIdResponse {
  implicit val encoder: Encoder[FeatureIdResponse] = deriveEncoder[FeatureIdResponse]

  def apply(id: FeatureId): FeatureIdResponse = FeatureIdResponse(id, id)
}
