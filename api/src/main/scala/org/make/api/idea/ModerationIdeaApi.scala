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
import org.make.api.question.QuestionServiceComponent
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.{`X-Total-Count`, MakeAuthenticationDirectives}
import org.make.core.Order
import org.make.core.Validation._
import org.make.core.auth.UserRights
import org.make.core.idea._
import org.make.core.idea.indexed.IndexedIdea
import org.make.core.operation.OperationId
import org.make.core.question.{Question, QuestionId}
import org.make.core.{HttpCodes, ParameterExtractors, Validation}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field
import scala.concurrent.Future

@Api(value = "Moderation Idea")
@Path(value = "/moderation/ideas")
trait ModerationIdeaApi extends Directives {

  @ApiOperation(
    value = "list-ideas",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[IdeaResponse]]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "name", paramType = "query", dataType = "string"),
      new ApiImplicitParam(
        name = "questionId",
        paramType = "query",
        dataType = "string",
        example = "57b1d160-2593-46bd-b7ad-f5e99ba3aa0d"
      ),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "int", example = "10"),
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "int", example = "0"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string")
    )
  )
  @Path(value = "/")
  def listIdeas: Route

  @ApiOperation(
    value = "get-idea",
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
      new ApiImplicitParam(
        name = "ideaId",
        paramType = "path",
        dataType = "string",
        example = "a10086bb-4312-4486-8f57-91b5e92b3eb9"
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[IdeaResponse])))
  @Path(value = "/{ideaId}")
  def getIdea: Route

  @ApiOperation(
    value = "create-idea",
    httpMethod = "POST",
    code = HttpCodes.Created,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value =
      Array(new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.idea.CreateIdeaRequest"))
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.Created, message = "Created", response = classOf[IdeaResponse]))
  )
  @Path(value = "/")
  def createIdea: Route

  @ApiOperation(
    value = "update-idea",
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
      new ApiImplicitParam(name = "ideaId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.idea.UpdateIdeaRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[IdeaIdResponse])))
  @Path(value = "/{ideaId}")
  def updateIdea: Route

  def routes: Route = createIdea ~ updateIdea ~ listIdeas ~ getIdea
}

trait ModerationIdeaApiComponent {
  def moderationIdeaApi: ModerationIdeaApi
}

trait DefaultModerationIdeaApiComponent
    extends ModerationIdeaApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  this: MakeDirectivesDependencies with IdeaServiceComponent with QuestionServiceComponent =>

  override lazy val moderationIdeaApi: ModerationIdeaApi = new DefaultModerationIdeaApi

  class DefaultModerationIdeaApi extends ModerationIdeaApi {

    val ideaId: PathMatcher1[IdeaId] = Segment.map(id => IdeaId(id))

    override def listIdeas: Route = {
      get {
        path("moderation" / "ideas") {
          parameters(
            "name".?,
            "questionId".as[QuestionId].?,
            "_end".as[Int].?,
            "_start".as[Int].?,
            "_sort".?,
            "_order".as[Order].?
          ) { (name, questionId, limit, skip, sort, order) =>
            makeOperation("GetAllIdeas") { requestContext =>
              makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                requireModerationRole(userAuth.user) {
                  Validation.validate(sort.map { sortValue =>
                    val choices =
                      Seq(
                        "ideaId",
                        "name",
                        "status",
                        "createdAt",
                        "updatedAt",
                        "operationId",
                        "questionId",
                        "themeId",
                        "question",
                        "language",
                        "country"
                      )
                    Validation.validChoices(
                      fieldName = "_sort",
                      message = Some(
                        s"Invalid sort. Got $sortValue but expected one of: ${choices.mkString("\"", "\", \"", "\"")}"
                      ),
                      Seq(sortValue),
                      choices
                    )
                  }.toList: _*)
                  val filters: IdeaFiltersRequest =
                    IdeaFiltersRequest(
                      name = name,
                      questionId = questionId,
                      limit = limit,
                      skip = skip,
                      sort = sort,
                      order = order
                    )
                  provideAsync(ideaService.fetchAll(filters.toSearchQuery(requestContext))) { ideas =>
                    complete(
                      (
                        StatusCodes.OK,
                        List(`X-Total-Count`(ideas.total.toString)),
                        ideas.results.map(IdeaResponse.apply)
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

    override def getIdea: Route = {
      get {
        path("moderation" / "ideas" / ideaId) { ideaId =>
          makeOperation("GetIdea") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireModerationRole(userAuth.user) {
                provideAsyncOrNotFound(ideaService.fetchOne(ideaId)) { idea =>
                  complete(IdeaResponse(idea))
                }
              }
            }
          }
        }
      }
    }

    override def createIdea: Route = post {
      path("moderation" / "ideas") {
        makeOperation("CreateIdea") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[CreateIdeaRequest]) { request: CreateIdeaRequest =>
                  Validation.validate(
                    Validation.requirePresent(
                      fieldName = "question",
                      fieldValue = request.questionId,
                      message = Some("question should not be empty")
                    )
                  )

                  provideAsyncOrNotFound(
                    request.questionId
                      .map(questionId => questionService.getQuestion(questionId))
                      .getOrElse(Future.successful(None))
                  ) { question: Question =>
                    provideAsync(ideaService.fetchOneByName(question.questionId, request.name)) { idea =>
                      Validation.validate(
                        Validation.requireNotPresent(
                          fieldName = "name",
                          fieldValue = idea,
                          message = Some("idea already exist. Duplicates are not allowed")
                        )
                      )

                      onSuccess(ideaService.insert(name = request.name, question = question)) { idea =>
                        complete(StatusCodes.Created -> IdeaResponse(idea))
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

    override def updateIdea: Route = put {
      path("moderation" / "ideas" / ideaId) { ideaId =>
        makeOperation("UpdateIdea") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[UpdateIdeaRequest]) { request: UpdateIdeaRequest =>
                  provideAsyncOrNotFound(ideaService.fetchOne(ideaId)) { idea =>
                    provideAsyncOrNotFound(
                      idea.questionId
                        .map(questionId => questionService.getQuestion(questionId))
                        .getOrElse(Future.successful(None))
                    ) { question =>
                      provideAsync(ideaService.fetchOneByName(question.questionId, request.name)) { duplicateIdea =>
                        if (!duplicateIdea.map(_.ideaId).contains(ideaId)) {
                          Validation.validate(
                            Validation.requireNotPresent(
                              fieldName = "name",
                              fieldValue = duplicateIdea,
                              message = Some("idea already exist. Duplicates are not allowed")
                            )
                          )
                        }
                        onSuccess(ideaService.update(ideaId = ideaId, name = request.name, status = request.status)) {
                          _ =>
                            complete(IdeaIdResponse(ideaId))
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
  }
}

final case class CreateIdeaRequest(
  name: String,
  @(ApiModelProperty @field)(dataType = "string", example = "57b1d160-2593-46bd-b7ad-f5e99ba3aa0d")
  questionId: Option[QuestionId]
) {
  validate(Validation.validateUserInput("name", name, None))
}

object CreateIdeaRequest {
  implicit val decoder: Decoder[CreateIdeaRequest] = deriveDecoder[CreateIdeaRequest]
}

final case class UpdateIdeaRequest(
  name: String,
  @(ApiModelProperty @field)(dataType = "string", example = "Activated")
  status: IdeaStatus
) {
  validate(Validation.validateUserInput("name", name, None))
}

object UpdateIdeaRequest {
  implicit val decoder: Decoder[UpdateIdeaRequest] = deriveDecoder[UpdateIdeaRequest]
}

final case class IdeaIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "a10086bb-4312-4486-8f57-91b5e92b3eb9") id: IdeaId,
  @(ApiModelProperty @field)(dataType = "string", example = "a10086bb-4312-4486-8f57-91b5e92b3eb9") ideaId: IdeaId
)

object IdeaIdResponse {
  implicit val encoder: Encoder[IdeaIdResponse] = deriveEncoder[IdeaIdResponse]
  implicit val decoder: Decoder[IdeaIdResponse] = deriveDecoder[IdeaIdResponse]

  def apply(id: IdeaId): IdeaIdResponse = IdeaIdResponse(id, id)
}

final case class IdeaResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "a10086bb-4312-4486-8f57-91b5e92b3eb9") id: IdeaId,
  name: String,
  @(ApiModelProperty @field)(dataType = "string", example = "f4767b7b-06c1-479d-8bc1-6e2a2de97f22")
  operationId: Option[OperationId],
  @(ApiModelProperty @field)(dataType = "string", example = "57b1d160-2593-46bd-b7ad-f5e99ba3aa0d")
  questionId: Option[QuestionId],
  @(ApiModelProperty @field)(dataType = "string", example = "Activated")
  status: IdeaStatus
)

object IdeaResponse {
  implicit val encoder: Encoder[IdeaResponse] = deriveEncoder[IdeaResponse]
  implicit val decoder: Decoder[IdeaResponse] = deriveDecoder[IdeaResponse]

  def apply(idea: Idea): IdeaResponse = {
    IdeaResponse(
      id = idea.ideaId,
      name = idea.name,
      operationId = idea.operationId,
      questionId = idea.questionId,
      status = idea.status
    )
  }

  def apply(idea: IndexedIdea): IdeaResponse = {
    IdeaResponse(
      id = idea.ideaId,
      name = idea.name,
      operationId = idea.operationId,
      questionId = idea.questionId,
      status = idea.status
    )
  }
}
