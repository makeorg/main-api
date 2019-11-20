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
import org.make.core.{HttpCodes, ParameterExtractors}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

@Api(
  value = "Admin Personality",
  authorizations = Array(
    new Authorization(
      value = "MakeApi",
      scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
    )
  )
)
@Path(value = "/admin/personalities")
trait AdminPersonalityApi extends Directives {

  @ApiOperation(value = "post-personality", httpMethod = "POST", code = HttpCodes.Created)
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.Created, message = "Created", response = classOf[PersonalityIdResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.personality.CreatePersonalityRequest"
      )
    )
  )
  @Path(value = "/")
  def adminPostPersonality: Route

  @ApiOperation(value = "put-personality", httpMethod = "PUT", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityIdResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.personality.UpdatePersonalityRequest"
      ),
      new ApiImplicitParam(name = "personalityId", paramType = "path", dataType = "string")
    )
  )
  @Path(value = "/{personalityId}")
  def adminPutPersonality: Route

  @ApiOperation(value = "get-personalities", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[PersonalityResponse]]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "userId", paramType = "query", required = false, dataType = "string"),
      new ApiImplicitParam(name = "questionId", paramType = "query", required = false, dataType = "string"),
      new ApiImplicitParam(name = "personalityRole", paramType = "query", required = false, dataType = "string")
    )
  )
  @Path(value = "/")
  def adminGetPersonalities: Route

  @ApiOperation(value = "get-personality", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityResponse]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "personalityId", paramType = "path", dataType = "string"))
  )
  @Path(value = "/{personalityId}")
  def adminGetPersonality: Route

  @ApiOperation(value = "delete-personality", httpMethod = "DELETE", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "OK")))
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "personalityId", paramType = "path", dataType = "string"))
  )
  @Path(value = "/{personalityId}")
  def adminDeletePersonality: Route

  def routes: Route =
    adminGetPersonality ~ adminGetPersonalities ~ adminPostPersonality ~ adminPutPersonality ~ adminDeletePersonality
}

trait AdminPersonalityApiComponent {
  def adminPersonalityApi: AdminPersonalityApi
}

trait DefaultAdminPersonalityApiComponent
    extends AdminPersonalityApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  this: PersonalityServiceComponent
    with MakeDataHandlerComponent
    with SessionHistoryCoordinatorServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent =>

  override lazy val adminPersonalityApi: AdminPersonalityApi = new DefaultAdminPersonalityApi

  class DefaultAdminPersonalityApi extends AdminPersonalityApi {

    private val personalityId: PathMatcher1[PersonalityId] = Segment.map(id => PersonalityId(id))

    override def adminPostPersonality: Route = {
      post {
        path("admin" / "personalities") {
          makeOperation("ModerationPostPersonality") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[CreatePersonalityRequest]) { request =>
                    onSuccess(personalityService.createPersonality(request)) { result =>
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

    override def adminPutPersonality: Route = {
      put {
        path("admin" / "personalities" / personalityId) { personalityId =>
          makeOperation("ModerationPutPersonality") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[UpdatePersonalityRequest]) { request =>
                    provideAsyncOrNotFound(personalityService.updatePersonality(personalityId, request)) { result =>
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

    override def adminGetPersonalities: Route = {
      get {
        path("admin" / "personalities") {
          makeOperation("ModerationGetPersonalities") { _ =>
            parameters(
              (
                '_start.as[Int].?,
                '_end.as[Int].?,
                '_sort.?,
                '_order.?,
                'userId.as[UserId].?,
                'questionId.as[QuestionId].?,
                'personalityRole.as[PersonalityRole].?
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
                      personalityService.find(start.getOrElse(0), end, sort, order, userId, questionId, personalityRole)
                    ) { result =>
                      provideAsync(personalityService.count(userId, questionId, personalityRole)) { count =>
                        complete(
                          (StatusCodes.OK, List(`X-Total-Count`(count.toString)), result.map(PersonalityResponse.apply))
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

    override def adminGetPersonality: Route = {
      get {
        path("admin" / "personalities" / personalityId) { personalityId =>
          makeOperation("ModerationGetPersonality") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsyncOrNotFound(personalityService.getPersonality(personalityId)) { personality =>
                  complete(PersonalityResponse(personality))
                }
              }
            }
          }
        }
      }
    }

    override def adminDeletePersonality: Route = {
      delete {
        path("admin" / "personalities" / personalityId) { personalityId =>
          makeOperation("ModerationDeletePersonality") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsync(personalityService.deletePersonality(personalityId)) { _ =>
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

final case class CreatePersonalityRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  userId: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "6a90575f-f625-4025-a485-8769e8a26967")
  questionId: QuestionId,
  @(ApiModelProperty @field)(dataType = "string", example = "CANDIDATE")
  personalityRole: PersonalityRole
)

object CreatePersonalityRequest {
  implicit val decoder: Decoder[CreatePersonalityRequest] = deriveDecoder[CreatePersonalityRequest]
}

final case class UpdatePersonalityRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  userId: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "CANDIDATE")
  personalityRole: PersonalityRole
)

object UpdatePersonalityRequest {
  implicit val decoder: Decoder[UpdatePersonalityRequest] = deriveDecoder[UpdatePersonalityRequest]
}

final case class PersonalityResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "5c95a5b1-3722-4f49-93ec-2c2fcb5da051")
  id: PersonalityId,
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  userId: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "CANDIDATE")
  personalityRole: PersonalityRole
)

object PersonalityResponse {
  def apply(personality: Personality): PersonalityResponse = PersonalityResponse(
    id = personality.personalityId,
    userId = personality.userId,
    personalityRole = personality.personalityRole
  )

  implicit val decoder: Decoder[PersonalityResponse] = deriveDecoder[PersonalityResponse]
  implicit val encoder: Encoder[PersonalityResponse] = deriveEncoder[PersonalityResponse]
}

final case class PersonalityIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e") personalityId: PersonalityId
)

object PersonalityIdResponse {
  implicit val encoder: Encoder[PersonalityIdResponse] = deriveEncoder[PersonalityIdResponse]
}
