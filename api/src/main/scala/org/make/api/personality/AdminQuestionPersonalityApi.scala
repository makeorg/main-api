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
import org.make.core.personality.{Personality, PersonalityId, PersonalityRole}
import org.make.core.question.QuestionId
import org.make.core.user.UserId
import org.make.core.{HttpCodes, ParameterExtractors, ValidationError}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

@Api(
  value = "Admin Question Personalities",
  authorizations = Array(
    new Authorization(
      value = "MakeApi",
      scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
    )
  )
)
@Path(value = "/admin/question-personalities")
trait AdminQuestionPersonalityApi extends Directives {

  @ApiOperation(value = "post-question-personality", httpMethod = "POST", code = HttpCodes.Created)
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.Created, message = "Created", response = classOf[PersonalityIdResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.personality.CreateQuestionPersonalityRequest"
      )
    )
  )
  @Path(value = "/")
  def adminPostQuestionPersonality: Route

  @ApiOperation(value = "put-question-personality", httpMethod = "PUT", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityIdResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.personality.UpdateQuestionPersonalityRequest"
      ),
      new ApiImplicitParam(name = "personalityId", paramType = "path", dataType = "string")
    )
  )
  @Path(value = "/{personalityId}")
  def adminPutQuestionPersonality: Route

  @ApiOperation(value = "get-question-personalities", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(
      new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[QuestionPersonalityResponse]])
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "userId", paramType = "query", required = false, dataType = "string"),
      new ApiImplicitParam(name = "questionId", paramType = "query", required = false, dataType = "string"),
      new ApiImplicitParam(
        name = "personalityRole",
        paramType = "query",
        required = false,
        dataType = "string",
        allowableValues = "CANDIDATE"
      )
    )
  )
  @Path(value = "/")
  def adminGetQuestionPersonalities: Route

  @ApiOperation(value = "get-question-personality", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[QuestionPersonalityResponse]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "personalityId", paramType = "path", dataType = "string"))
  )
  @Path(value = "/{personalityId}")
  def adminGetQuestionPersonality: Route

  @ApiOperation(value = "delete-question-personality", httpMethod = "DELETE", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "OK")))
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "personalityId", paramType = "path", dataType = "string"))
  )
  @Path(value = "/{personalityId}")
  def adminDeleteQuestionPersonality: Route

  def routes: Route =
    adminGetQuestionPersonality ~ adminGetQuestionPersonalities ~ adminPostQuestionPersonality ~
      adminPutQuestionPersonality ~ adminDeleteQuestionPersonality
}

trait AdminQuestionPersonalityApiComponent {
  def adminQuestionPersonalityApi: AdminQuestionPersonalityApi
}

trait DefaultAdminQuestionPersonalityApiComponent
    extends AdminQuestionPersonalityApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  this: QuestionPersonalityServiceComponent
    with MakeDataHandlerComponent
    with SessionHistoryCoordinatorServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent =>

  override lazy val adminQuestionPersonalityApi: AdminQuestionPersonalityApi = new DefaultAdminQuestionPersonalityApi

  class DefaultAdminQuestionPersonalityApi extends AdminQuestionPersonalityApi {

    private val personalityId: PathMatcher1[PersonalityId] = Segment.map(id => PersonalityId(id))

    override def adminPostQuestionPersonality: Route = {
      post {
        path("admin" / "question-personalities") {
          makeOperation("ModerationPostQuestionPersonality") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[CreateQuestionPersonalityRequest]) { request =>
                    provideAsync(
                      questionPersonalityService.find(
                        start = 0,
                        end = None,
                        sort = None,
                        order = None,
                        userId = Some(request.userId),
                        questionId = Some(request.questionId),
                        personalityRole = None
                      )
                    ) {
                      case duplicates if duplicates.nonEmpty =>
                        complete(
                          StatusCodes.BadRequest -> ValidationError(
                            "userId",
                            "already_defined",
                            Some(
                              s"User with id : ${request.userId.value} is already defined as a personality for this question"
                            )
                          )
                        )
                      case _ =>
                        onSuccess(questionPersonalityService.createPersonality(request)) { result =>
                          complete(StatusCodes.Created -> PersonalityIdResponse(result.personalityId))
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

    override def adminPutQuestionPersonality: Route = {
      put {
        path("admin" / "question-personalities" / personalityId) { personalityId =>
          makeOperation("ModerationPutQuestionPersonality") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[UpdateQuestionPersonalityRequest]) { request =>
                    provideAsyncOrNotFound(questionPersonalityService.updatePersonality(personalityId, request)) {
                      result =>
                        complete(StatusCodes.OK -> PersonalityIdResponse(result.personalityId))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def adminGetQuestionPersonalities: Route = {
      get {
        path("admin" / "question-personalities") {
          makeOperation("ModerationGetQuestionPersonalities") { _ =>
            parameters(
              (
                Symbol("_start").as[Int].?,
                Symbol("_end").as[Int].?,
                Symbol("_sort").?,
                Symbol("_order").?,
                Symbol("userId").as[UserId].?,
                Symbol("questionId").as[QuestionId].?,
                Symbol("personalityRole").as[PersonalityRole].?
              )
            ) {
              (start: Option[Int],
               end: Option[Int],
               sort: Option[String],
               order: Option[String],
               userId: Option[UserId],
               questionId: Option[QuestionId],
               personalityRole: Option[PersonalityRole]) =>
                makeOAuth2 { auth: AuthInfo[UserRights] =>
                  requireAdminRole(auth.user) {
                    provideAsync(
                      questionPersonalityService
                        .find(start.getOrElse(0), end, sort, order, userId, questionId, personalityRole)
                    ) { result =>
                      provideAsync(questionPersonalityService.count(userId, questionId, personalityRole)) { count =>
                        complete(
                          (
                            StatusCodes.OK,
                            List(`X-Total-Count`(count.toString)),
                            result.map(QuestionPersonalityResponse.apply)
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

    override def adminGetQuestionPersonality: Route = {
      get {
        path("admin" / "question-personalities" / personalityId) { personalityId =>
          makeOperation("ModerationGetQuestionPersonality") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsyncOrNotFound(questionPersonalityService.getPersonality(personalityId)) { personality =>
                  complete(QuestionPersonalityResponse(personality))
                }
              }
            }
          }
        }
      }
    }

    override def adminDeleteQuestionPersonality: Route = {
      delete {
        path("admin" / "question-personalities" / personalityId) { personalityId =>
          makeOperation("ModerationDeleteQuestionPersonality") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsync(questionPersonalityService.deletePersonality(personalityId)) { _ =>
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

final case class CreateQuestionPersonalityRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  userId: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "6a90575f-f625-4025-a485-8769e8a26967")
  questionId: QuestionId,
  @(ApiModelProperty @field)(dataType = "string", example = "CANDIDATE")
  personalityRole: PersonalityRole
)

object CreateQuestionPersonalityRequest {
  implicit val decoder: Decoder[CreateQuestionPersonalityRequest] = deriveDecoder[CreateQuestionPersonalityRequest]
}

final case class UpdateQuestionPersonalityRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  userId: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "CANDIDATE")
  personalityRole: PersonalityRole
)

object UpdateQuestionPersonalityRequest {
  implicit val decoder: Decoder[UpdateQuestionPersonalityRequest] = deriveDecoder[UpdateQuestionPersonalityRequest]
}

final case class QuestionPersonalityResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "5c95a5b1-3722-4f49-93ec-2c2fcb5da051")
  id: PersonalityId,
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  userId: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "CANDIDATE")
  personalityRole: PersonalityRole
)

object QuestionPersonalityResponse {
  def apply(personality: Personality): QuestionPersonalityResponse = QuestionPersonalityResponse(
    id = personality.personalityId,
    userId = personality.userId,
    personalityRole = personality.personalityRole
  )

  implicit val decoder: Decoder[QuestionPersonalityResponse] = deriveDecoder[QuestionPersonalityResponse]
  implicit val encoder: Encoder[QuestionPersonalityResponse] = deriveEncoder[QuestionPersonalityResponse]
}

final case class PersonalityIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e") personalityId: PersonalityId
)

object PersonalityIdResponse {
  implicit val encoder: Encoder[PersonalityIdResponse] = deriveEncoder[PersonalityIdResponse]
}
