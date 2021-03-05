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
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.{`X-Total-Count`, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.personality.{Personality, PersonalityId, PersonalityRole, PersonalityRoleId}
import org.make.core.question.QuestionId
import org.make.core.user.UserId
import org.make.core.{HttpCodes, Order, ParameterExtractors, ValidationError}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.make.core.technical.Pagination._

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

  @ApiOperation(value = "admin-post-question-personality", httpMethod = "POST", code = HttpCodes.Created)
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

  @ApiOperation(value = "admin-put-question-personality", httpMethod = "PUT", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityIdResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "personalityId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.personality.UpdateQuestionPersonalityRequest"
      )
    )
  )
  @Path(value = "/{personalityId}")
  def adminPutQuestionPersonality: Route

  @ApiOperation(value = "admin-get-question-personalities", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(
      new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[AdminQuestionPersonalityResponse]])
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "integer"),
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

  @ApiOperation(value = "admin-get-question-personality", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[AdminQuestionPersonalityResponse]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "personalityId", paramType = "path", dataType = "string"))
  )
  @Path(value = "/{personalityId}")
  def adminGetQuestionPersonality: Route

  @ApiOperation(value = "admin-delete-question-personality", httpMethod = "DELETE", code = HttpCodes.NoContent)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
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
  this: MakeDirectivesDependencies with QuestionPersonalityServiceComponent with PersonalityRoleServiceComponent =>

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
                        start = Start.zero,
                        end = None,
                        sort = None,
                        order = None,
                        userId = Some(request.userId),
                        questionId = Some(request.questionId),
                        personalityRoleId = None
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
              "_start".as[Start].?,
              "_end".as[End].?,
              "_sort".?,
              "_order".as[Order].?,
              "userId".as[UserId].?,
              "questionId".as[QuestionId].?,
              "personalityRoleId".as[PersonalityRoleId].?
            ) {
              (
                start: Option[Start],
                end: Option[End],
                sort: Option[String],
                order: Option[Order],
                userId: Option[UserId],
                questionId: Option[QuestionId],
                personalityRoleId: Option[PersonalityRoleId]
              ) =>
                makeOAuth2 { auth: AuthInfo[UserRights] =>
                  requireAdminRole(auth.user) {
                    provideAsync(
                      questionPersonalityService
                        .find(start.orZero, end, sort, order, userId, questionId, personalityRoleId)
                    ) { personalities =>
                      provideAsync(questionPersonalityService.count(userId, questionId, personalityRoleId)) { count =>
                        provideAsync(
                          personalityRoleService.find(
                            start = Start.zero,
                            end = None,
                            sort = None,
                            order = None,
                            roleIds = Some(personalities.map(_.personalityRoleId)),
                            name = None
                          )
                        ) { personalityRoles =>
                          @SuppressWarnings(Array("org.wartremover.warts.Throw"))
                          val result = personalities.map { personality =>
                            personalityRoles
                              .find(role => role.personalityRoleId == personality.personalityRoleId) match {
                              case Some(personalityRole) =>
                                AdminQuestionPersonalityResponse(personality, personalityRole)
                              case None =>
                                throw new IllegalStateException(
                                  s"Unable to find the personality role with id ${personality.personalityRoleId}"
                                )
                            }
                          }
                          complete((StatusCodes.OK, List(`X-Total-Count`(count.toString)), result))
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

    override def adminGetQuestionPersonality: Route = {
      get {
        path("admin" / "question-personalities" / personalityId) { personalityId =>
          makeOperation("ModerationGetQuestionPersonality") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsyncOrNotFound(questionPersonalityService.getPersonality(personalityId)) { personality =>
                  provideAsync(
                    personalityRoleService
                      .getPersonalityRole(personality.personalityRoleId)
                      .flatMap(
                        _.fold(
                          Future.failed[PersonalityRole](
                            new IllegalStateException(s"Personality with the id $personalityId does not exist")
                          )
                        )(Future.successful)
                      )
                  ) { personalityRole =>
                    complete(AdminQuestionPersonalityResponse(personality, personalityRole))
                  }
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
  @(ApiModelProperty @field)(dataType = "string", example = "0c3cbbf4-42c1-4801-b08a-d0e60d136041")
  personalityRoleId: PersonalityRoleId
)

object CreateQuestionPersonalityRequest {
  implicit val decoder: Decoder[CreateQuestionPersonalityRequest] = deriveDecoder[CreateQuestionPersonalityRequest]
}

final case class UpdateQuestionPersonalityRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  userId: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "0c3cbbf4-42c1-4801-b08a-d0e60d136041")
  personalityRoleId: PersonalityRoleId
)

object UpdateQuestionPersonalityRequest {
  implicit val decoder: Decoder[UpdateQuestionPersonalityRequest] = deriveDecoder[UpdateQuestionPersonalityRequest]
}

final case class AdminQuestionPersonalityResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "5c95a5b1-3722-4f49-93ec-2c2fcb5da051")
  id: PersonalityId,
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  userId: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "0c3cbbf4-42c1-4801-b08a-d0e60d136041")
  personalityRoleId: PersonalityRoleId
)

object AdminQuestionPersonalityResponse {
  def apply(personality: Personality, personalityRole: PersonalityRole): AdminQuestionPersonalityResponse =
    AdminQuestionPersonalityResponse(
      id = personality.personalityId,
      userId = personality.userId,
      personalityRoleId = personalityRole.personalityRoleId
    )

  implicit val decoder: Decoder[AdminQuestionPersonalityResponse] = deriveDecoder[AdminQuestionPersonalityResponse]
  implicit val encoder: Encoder[AdminQuestionPersonalityResponse] = deriveEncoder[AdminQuestionPersonalityResponse]
}

final case class PersonalityIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e") id: PersonalityId,
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e") personalityId: PersonalityId
)

object PersonalityIdResponse {
  implicit val encoder: Encoder[PersonalityIdResponse] = deriveEncoder[PersonalityIdResponse]

  def apply(id: PersonalityId): PersonalityIdResponse = PersonalityIdResponse(id, id)
}
