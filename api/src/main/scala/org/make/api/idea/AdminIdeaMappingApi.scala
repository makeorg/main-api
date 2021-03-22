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
import akka.http.scaladsl.server.{Directives, PathMatcher1, Route}
import io.circe.generic.semiauto.{deriveDecoder, _}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._

import javax.ws.rs.Path
import org.make.api.idea.AdminIdeaMappingApi.{CreateIdeaMappingRequest, IdeaMappingResponse, UpdateIdeaMappingRequest}
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.{`X-Total-Count`, MakeAuthenticationDirectives}
import org.make.core.idea.{IdeaId, IdeaMapping, IdeaMappingId}
import org.make.core.question.QuestionId
import org.make.core.tag.TagId
import org.make.core.{HttpCodes, Order, ParameterExtractors}

import scala.annotation.meta.field
import org.make.core.technical.Pagination._

@Path("/admin/idea-mappings")
@Api(value = "Admin Idea Mappings")
trait AdminIdeaMappingApi extends Directives {

  @Path("/")
  @ApiOperation(
    value = "list-idea-mappings",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[IdeaMappingResponse]]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "questionId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "stakeTagId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "solutionTypeTagId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "ideaId", paramType = "query", dataType = "string")
    )
  )
  def search: Route

  @Path("/{ideaMappingId}")
  @ApiOperation(
    value = "get-idea-mapping",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[IdeaMappingResponse]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "ideaMappingId", paramType = "path", dataType = "string"))
  )
  def getIdeaMapping: Route

  @Path("/")
  @ApiOperation(
    value = "create-idea-mapping",
    httpMethod = "POST",
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
        value = "body",
        paramType = "body",
        dataType = AdminIdeaMappingApiTypes.CreateIdeaMappingRequestType
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[IdeaMappingResponse]))
  )
  def createIdeaMapping: Route

  @Path("/{ideaMappingId}")
  @ApiOperation(
    value = "update-idea-mapping",
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
      new ApiImplicitParam(name = "ideaMappingId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = AdminIdeaMappingApiTypes.UpdateIdeaMappingRequestType
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[IdeaMappingResponse]))
  )
  def updateIdeaMapping: Route

  def routes: Route = search ~ getIdeaMapping ~ createIdeaMapping ~ updateIdeaMapping
}

object AdminIdeaMappingApi {
  @ApiModel
  final case class IdeaMappingResponse(
    @(ApiModelProperty @field)(dataType = "string", example = "d4b33c55-46ea-4fef-b4a5-b6a594d967b6") id: IdeaMappingId,
    @(ApiModelProperty @field)(dataType = "string", example = "613ea01f-2da7-4d77-b1fe-99f9f252b3a2") questionId: QuestionId,
    @(ApiModelProperty @field)(dataType = "string", example = "dd9e6f92-7745-4224-aa6d-a177a5645daf") stakeTagId: Option[
      TagId
    ],
    @(ApiModelProperty @field)(dataType = "string", example = "0fa85cb4-cf91-4f0c-a03e-0b0445af4184") solutionTypeTagId: Option[
      TagId
    ],
    @(ApiModelProperty @field)(dataType = "string", example = "fa113d64-bc99-4e25-894c-03dccf3203e2") ideaId: IdeaId
  )
  object IdeaMappingResponse {
    implicit val encoder: Encoder[IdeaMappingResponse] = deriveEncoder[IdeaMappingResponse]

    def fromIdeaMapping(mapping: IdeaMapping): IdeaMappingResponse = {
      IdeaMappingResponse(
        id = mapping.id,
        questionId = mapping.questionId,
        stakeTagId = mapping.stakeTagId,
        solutionTypeTagId = mapping.solutionTypeTagId,
        ideaId = mapping.ideaId
      )
    }
  }

  @ApiModel
  final case class CreateIdeaMappingRequest(
    @(ApiModelProperty @field)(dataType = "string", example = "613ea01f-2da7-4d77-b1fe-99f9f252b3a2") questionId: QuestionId,
    @(ApiModelProperty @field)(dataType = "string", example = "dd9e6f92-7745-4224-aa6d-a177a5645daf") stakeTagId: Option[
      TagId
    ],
    @(ApiModelProperty @field)(dataType = "string", example = "0fa85cb4-cf91-4f0c-a03e-0b0445af4184") solutionTypeTagId: Option[
      TagId
    ],
    @(ApiModelProperty @field)(dataType = "string", example = "fa113d64-bc99-4e25-894c-03dccf3203e2") ideaId: IdeaId
  )
  object CreateIdeaMappingRequest {
    implicit val decoder: Decoder[CreateIdeaMappingRequest] = deriveDecoder[CreateIdeaMappingRequest]
  }

  @ApiModel
  final case class UpdateIdeaMappingRequest(
    @(ApiModelProperty @field)(dataType = "string", example = "fa113d64-bc99-4e25-894c-03dccf3203e2") ideaId: IdeaId,
    migrateProposals: Boolean
  )

  object UpdateIdeaMappingRequest {
    implicit val decoder: Decoder[UpdateIdeaMappingRequest] = deriveDecoder[UpdateIdeaMappingRequest]
  }
}

trait AdminIdeaMappingApiComponent {
  def adminIdeaMappingApi: AdminIdeaMappingApi
}

trait DefaultAdminIdeaMappingApiComponent
    extends AdminIdeaMappingApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {

  self: MakeDirectivesDependencies with IdeaMappingServiceComponent =>

  override val adminIdeaMappingApi: DefaultAdminIdeaMappingApi = new DefaultAdminIdeaMappingApi

  class DefaultAdminIdeaMappingApi extends AdminIdeaMappingApi {

    val ideaMappingId: PathMatcher1[IdeaMappingId] = Segment.map(IdeaMappingId.apply)

    override def search: Route = get {
      path("admin" / "idea-mappings") {
        parameters(
          "_start".as[Start].?,
          "_end".as[End].?,
          "_sort".?,
          "_order".as[Order].?,
          "questionId".as[QuestionId].?,
          "stakeTagId".as[TagIdOrNone].?,
          "solutionTypeTagId".as[TagIdOrNone].?,
          "ideaId".as[IdeaId].?
        ) {
          (
            start: Option[Start],
            end: Option[End],
            sort: Option[String],
            order: Option[Order],
            questionId: Option[QuestionId],
            stakeTagId: Option[TagIdOrNone],
            solutionTypeTagId: Option[TagIdOrNone],
            ideaId: Option[IdeaId]
          ) =>
            makeOperation("searchIdeaMapping") { _ =>
              makeOAuth2 { auth =>
                requireAdminRole(auth.user) {
                  provideAsync(ideaMappingService.count(questionId, stakeTagId, solutionTypeTagId, ideaId)) { count =>
                    provideAsync(
                      ideaMappingService
                        .search(start.orZero, end, sort, order, questionId, stakeTagId, solutionTypeTagId, ideaId)
                    ) { mappings =>
                      complete(
                        (
                          StatusCodes.OK,
                          List(`X-Total-Count`(count.toString)),
                          mappings.map(IdeaMappingResponse.fromIdeaMapping)
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

    override def getIdeaMapping: Route = get {
      path("admin" / "idea-mappings" / ideaMappingId) { ideaMappingId =>
        makeOperation("getIdeaMapping") { _ =>
          makeOAuth2 { auth =>
            requireAdminRole(auth.user) {
              provideAsyncOrNotFound(ideaMappingService.getById(ideaMappingId)) { mapping =>
                complete(StatusCodes.OK -> IdeaMappingResponse.fromIdeaMapping(mapping))
              }
            }
          }
        }
      }
    }

    override def createIdeaMapping: Route = post {
      path("admin" / "idea-mappings") {
        makeOperation("createIdeaMapping") { _ =>
          makeOAuth2 { auth =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[CreateIdeaMappingRequest]) { request =>
                  provideAsync(
                    ideaMappingService
                      .create(request.questionId, request.stakeTagId, request.solutionTypeTagId, request.ideaId)
                  ) { result =>
                    complete(StatusCodes.Created -> IdeaMappingResponse.fromIdeaMapping(result))
                  }
                }
              }
            }
          }
        }
      }
    }

    override def updateIdeaMapping: Route = put {
      path("admin" / "idea-mappings" / ideaMappingId) { ideaMappingId =>
        makeOperation("updateIdeaMapping") { _ =>
          makeOAuth2 { auth =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[UpdateIdeaMappingRequest]) { request =>
                  provideAsyncOrNotFound(
                    ideaMappingService
                      .changeIdea(auth.user.userId, ideaMappingId, request.ideaId, request.migrateProposals)
                  ) { mapping =>
                    complete(StatusCodes.OK -> IdeaMappingResponse.fromIdeaMapping(mapping))
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
