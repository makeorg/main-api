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

package org.make.api.idea

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{`X-Total-Count`, IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.idea.{IdeaId, TopIdea, TopIdeaId, TopIdeaScores}
import org.make.core.question.QuestionId
import org.make.core.{HttpCodes, Order, ParameterExtractors, ValidationError}

import scala.annotation.meta.field

@Path("/admin/top-ideas")
@Api(value = "Admin Top Idea")
trait AdminTopIdeaApi extends Directives {

  @Path("/")
  @ApiOperation(
    value = "list-top-ideas",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[TopIdeaResponse]]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "ideaId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "questionId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "name", paramType = "query", dataType = "string")
    )
  )
  def search: Route

  @Path("/{topIdeaId}")
  @ApiOperation(
    value = "get-top-idea",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TopIdeaResponse]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "topIdeaId", paramType = "path", dataType = "string")))
  def getTopIdea: Route

  @Path("/")
  @ApiOperation(
    value = "create-top-idea",
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
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.idea.CreateTopIdeaRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TopIdeaResponse]))
  )
  def createTopIdea: Route

  @Path("/{topIdeaId}")
  @ApiOperation(
    value = "update-top-idea",
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
      new ApiImplicitParam(name = "topIdeaId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.idea.UpdateTopIdeaRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TopIdeaResponse]))
  )
  def updateTopIdea: Route

  @Path("/{topIdeaId}")
  @ApiOperation(
    value = "delete-top-idea",
    httpMethod = "DELETE",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "topIdeaId", paramType = "path", dataType = "string")))
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TopIdeaIdResponse]))
  )
  def deleteTopIdea: Route

  def routes: Route = search ~ getTopIdea ~ createTopIdea ~ updateTopIdea ~ deleteTopIdea
}

trait AdminTopIdeaApiComponent {
  def adminTopIdeaApi: AdminTopIdeaApi
}

trait DefaultAdminTopIdeaApiComponent
    extends AdminTopIdeaApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {

  self: MakeDataHandlerComponent
    with IdGeneratorComponent
    with SessionHistoryCoordinatorServiceComponent
    with MakeSettingsComponent
    with TopIdeaServiceComponent
    with QuestionServiceComponent
    with IdeaServiceComponent =>

  override val adminTopIdeaApi: DefaultAdminTopIdeaApi = new DefaultAdminTopIdeaApi

  class DefaultAdminTopIdeaApi extends AdminTopIdeaApi {

    val topIdeaId: PathMatcher1[TopIdeaId] = Segment.map(TopIdeaId.apply)

    override def search: Route = get {
      path("admin" / "top-ideas") {
        parameters(
          "_start".as[Int].?,
          "_end".as[Int].?,
          "_sort".?,
          "_order".as[Order].?,
          "ideaId".as[IdeaId].?,
          "questionId".as[QuestionId].?,
          "name".?
        ) {
          (
            start: Option[Int],
            end: Option[Int],
            sort: Option[String],
            order: Option[Order],
            ideaId: Option[IdeaId],
            questionId: Option[QuestionId],
            name: Option[String]
          ) =>
            makeOperation("searchTopIdea") { _ =>
              makeOAuth2 { auth =>
                requireAdminRole(auth.user) {
                  provideAsync(topIdeaService.count(ideaId, questionId, name)) { count =>
                    provideAsync(
                      topIdeaService
                        .search(start.getOrElse(0), end, sort, order, ideaId, questionId.map(Seq(_)), name)
                    ) { topIdeas =>
                      complete(
                        (StatusCodes.OK, List(`X-Total-Count`(count.toString)), topIdeas.map(TopIdeaResponse.apply))
                      )
                    }
                  }
                }
              }
            }
        }
      }
    }

    override def getTopIdea: Route = get {
      path("admin" / "top-ideas" / topIdeaId) { topIdeaId =>
        makeOperation("getTopIdea") { _ =>
          makeOAuth2 { auth =>
            requireAdminRole(auth.user) {
              provideAsyncOrNotFound(topIdeaService.getById(topIdeaId)) { topIdea =>
                complete(StatusCodes.OK -> TopIdeaResponse(topIdea))
              }
            }
          }
        }
      }
    }

    override def createTopIdea: Route = post {
      path("admin" / "top-ideas") {
        makeOperation("createTopIdea") { _ =>
          makeOAuth2 { auth =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[CreateTopIdeaRequest]) { request =>
                  provideAsync(questionService.getQuestion(request.questionId)) {
                    case None =>
                      complete(
                        StatusCodes.BadRequest -> Seq(
                          ValidationError(
                            "questionId",
                            "not_found",
                            Some(s"questionId ${request.questionId} doesn't exists")
                          )
                        )
                      )
                    case Some(_) =>
                      provideAsync(ideaService.fetchOne(request.ideaId)) {
                        case None =>
                          complete(
                            StatusCodes.BadRequest -> Seq(
                              ValidationError("ideaId", "not_found", Some(s"ideaId ${request.ideaId} doesn't exists"))
                            )
                          )
                        case Some(_) =>
                          provideAsync(
                            topIdeaService
                              .create(
                                request.ideaId,
                                request.questionId,
                                request.name,
                                request.label,
                                TopIdeaScores(
                                  request.scores.totalProposalsRatio,
                                  request.scores.agreementRatio,
                                  request.scores.likeItRatio
                                ),
                                request.weight
                              )
                          ) { result =>
                            complete(StatusCodes.Created -> TopIdeaResponse(result))
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

    override def updateTopIdea: Route = put {
      path("admin" / "top-ideas" / topIdeaId) { topIdeaId =>
        makeOperation("updateTopIdea") { _ =>
          makeOAuth2 { auth =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[UpdateTopIdeaRequest]) { request =>
                  provideAsyncOrNotFound(topIdeaService.getById(topIdeaId)) { topIdea =>
                    provideAsync(questionService.getQuestion(request.questionId)) {
                      case None =>
                        complete(
                          StatusCodes.BadRequest -> Seq(
                            ValidationError(
                              "questionId",
                              "not_found",
                              Some(s"questionId ${request.questionId} doesn't exists")
                            )
                          )
                        )
                      case Some(_) =>
                        provideAsync(ideaService.fetchOne(request.ideaId)) {
                          case None =>
                            complete(
                              StatusCodes.BadRequest -> Seq(
                                ValidationError("ideaId", "not_found", Some(s"ideaId ${request.ideaId} doesn't exists"))
                              )
                            )
                          case Some(_) =>
                            provideAsync(
                              topIdeaService.update(
                                topIdea.copy(
                                  questionId = request.questionId,
                                  ideaId = request.ideaId,
                                  name = request.name,
                                  label = request.label,
                                  scores = TopIdeaScores(
                                    request.scores.totalProposalsRatio,
                                    request.scores.agreementRatio,
                                    request.scores.likeItRatio
                                  ),
                                  weight = request.weight
                                )
                              )
                            ) { updateTopIdea =>
                              complete(StatusCodes.OK -> TopIdeaResponse(updateTopIdea))
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

    override def deleteTopIdea: Route = delete {
      path("admin" / "top-ideas" / topIdeaId) { topIdeaId =>
        makeOperation("deleteTopIdea") { _ =>
          makeOAuth2 { auth =>
            requireAdminRole(auth.user) {
              provideAsync(topIdeaService.delete(topIdeaId)) { _ =>
                complete(StatusCodes.OK -> TopIdeaIdResponse(topIdeaId))
              }
            }
          }
        }
      }
    }
  }
}

@ApiModel
final case class TopIdeaResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d4b33c55-46ea-4fef-b4a5-b6a594d967b6") id: TopIdeaId,
  @(ApiModelProperty @field)(dataType = "string", example = "fa113d64-bc99-4e25-894c-03dccf3203e2") ideaId: IdeaId,
  @(ApiModelProperty @field)(dataType = "string", example = "613ea01f-2da7-4d77-b1fe-99f9f252b3a2") questionId: QuestionId,
  name: String,
  label: String,
  scores: TopIdeaScores,
  weight: Float
)
object TopIdeaResponse {
  implicit val decoder: Decoder[TopIdeaResponse] = deriveDecoder[TopIdeaResponse]
  implicit val encoder: Encoder[TopIdeaResponse] = deriveEncoder[TopIdeaResponse]

  def apply(topIdea: TopIdea): TopIdeaResponse = {
    TopIdeaResponse(
      id = topIdea.topIdeaId,
      ideaId = topIdea.ideaId,
      questionId = topIdea.questionId,
      name = topIdea.name,
      label = topIdea.label,
      scores = topIdea.scores,
      weight = topIdea.weight
    )
  }
}

@ApiModel
final case class TopIdeaIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "fa113d64-bc99-4e25-894c-03dccf3203e2")
  id: TopIdeaId,
  @(ApiModelProperty @field)(dataType = "string", example = "fa113d64-bc99-4e25-894c-03dccf3203e2")
  topIdeaId: TopIdeaId
)
object TopIdeaIdResponse {
  implicit val encoder: Encoder[TopIdeaIdResponse] = deriveEncoder[TopIdeaIdResponse]

  def apply(id: TopIdeaId): TopIdeaIdResponse = TopIdeaIdResponse(id, id)
}

@ApiModel
final case class CreateTopIdeaRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "fa113d64-bc99-4e25-894c-03dccf3203e2") ideaId: IdeaId,
  @(ApiModelProperty @field)(dataType = "string", example = "613ea01f-2da7-4d77-b1fe-99f9f252b3a2") questionId: QuestionId,
  name: String,
  label: String,
  scores: TopIdeaScores,
  weight: Float
)
object CreateTopIdeaRequest {
  implicit val decoder: Decoder[CreateTopIdeaRequest] = deriveDecoder[CreateTopIdeaRequest]
}

@ApiModel
final case class UpdateTopIdeaRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "fa113d64-bc99-4e25-894c-03dccf3203e2") ideaId: IdeaId,
  @(ApiModelProperty @field)(dataType = "string", example = "613ea01f-2da7-4d77-b1fe-99f9f252b3a2") questionId: QuestionId,
  name: String,
  label: String,
  scores: TopIdeaScores,
  weight: Float
)

object UpdateTopIdeaRequest {
  implicit val decoder: Decoder[UpdateTopIdeaRequest] = deriveDecoder[UpdateTopIdeaRequest]
}
