/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.api.demographics

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder}
import io.circe.{Codec, Decoder}
import io.swagger.annotations._
import org.make.api.question.QuestionServiceComponent

import javax.ws.rs.Path
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.{`X-Total-Count`, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.demographics._
import org.make.core.question.QuestionId
import org.make.core.{HttpCodes, Order, ParameterExtractors, Validation, ValidationError}
import scalaoauth2.provider.AuthInfo
import org.make.core.technical.Pagination._

import scala.annotation.meta.field

@Api(value = "Admin Active Demographics Cards")
@Path(value = "/admin/active-demographics-cards")
trait AdminActiveDemographicsCardApi extends Directives {

  @ApiOperation(
    value = "get-active-demographics-card",
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
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ActiveDemographicsCardResponse]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "id", paramType = "path", dataType = "string")))
  @Path(value = "/{id}")
  def getById: Route

  @ApiOperation(
    value = "create-active-demographics-Card",
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
        dataType = "org.make.api.demographics.CreateActiveDemographicsCardRequest"
      )
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ActiveDemographicsCardResponse]))
  )
  @Path(value = "/")
  def create: Route

  @ApiOperation(
    value = "list-active-demographics-cards",
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
      new ApiImplicitParam(name = "questionId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "cardId", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(
      new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[ActiveDemographicsCardResponse]])
    )
  )
  @Path(value = "/")
  def list: Route

  @ApiOperation(
    value = "delete-active-demographics-card",
    httpMethod = "DELETE",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "id", paramType = "path", dataType = "string")))
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @Path(value = "/{id}")
  def remove: Route

  def routes: Route = getById ~ create ~ list ~ remove
}

trait AdminActiveDemographicsCardApiComponent {
  def adminActiveDemographicsCardApi: AdminActiveDemographicsCardApi
}

trait DefaultAdminActiveDemographicsCardApiComponent
    extends AdminActiveDemographicsCardApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  this: MakeDirectivesDependencies
    with ActiveDemographicsCardServiceComponent
    with DemographicsCardServiceComponent
    with QuestionServiceComponent =>

  override lazy val adminActiveDemographicsCardApi: AdminActiveDemographicsCardApi =
    new AdminActiveDemographicsCardApi {

      val id: PathMatcher1[ActiveDemographicsCardId] = Segment.map(ActiveDemographicsCardId.apply)

      override def getById: Route = {
        get {
          path("admin" / "active-demographics-cards" / id) { id =>
            makeOperation("AdminGetActiveDemographicsCard") { _ =>
              makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                requireAdminRole(userAuth.user) {
                  provideAsyncOrNotFound(activeDemographicsCardService.get(id)) { activeDemographicsCard =>
                    complete(ActiveDemographicsCardResponse(activeDemographicsCard))
                  }
                }
              }
            }
          }
        }
      }

      override def create: Route = post {
        path("admin" / "active-demographics-cards") {
          makeOperation("AdminCreateActiveDemographicsCard") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                decodeRequest {
                  entity(as[CreateActiveDemographicsCardRequest]) { request =>
                    val card = demographicsCardService.get(request.demographicsCardId)
                    val question = questionService.getQuestion(request.questionId)
                    provideAsyncOrBadRequest(
                      card,
                      ValidationError(
                        "demographicsCardId",
                        "not_found",
                        Some(s"Demographics card ${request.demographicsCardId.value} doesn't exist")
                      )
                    ) { _ =>
                      provideAsyncOrBadRequest(
                        question,
                        ValidationError(
                          "questionId",
                          "not_found",
                          Some(s"Question ${request.questionId.value} doesn't exist")
                        )
                      ) { _ =>
                        provideAsync(
                          activeDemographicsCardService
                            .list(questionId = Some(request.questionId), cardId = Some(request.demographicsCardId))
                        ) { actives =>
                          Validation.validate(
                            Validation
                              .requireEmpty(
                                "questionId",
                                actives,
                                Some(
                                  s"An active card already exists for questionId ${request.questionId} and demographicsCardId ${request.demographicsCardId}"
                                )
                              )
                          )
                          provideAsync(
                            activeDemographicsCardService.create(request.demographicsCardId, request.questionId)
                          ) { activeDemographicsCard =>
                            complete(StatusCodes.Created -> ActiveDemographicsCardResponse(activeDemographicsCard))
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

      override def list: Route = {
        get {
          path("admin" / "active-demographics-cards") {
            makeOperation("AdminListActiveDemographicsCard") { _ =>
              parameters(
                "_start".as[Start].?,
                "_end".as[End].?,
                "_sort".?,
                "_order".as[Order].?,
                "questionId".as[QuestionId].?,
                "cardId".as[DemographicsCardId].?
              ) {
                (
                  start: Option[Start],
                  end: Option[End],
                  sort: Option[String],
                  order: Option[Order],
                  questionId: Option[QuestionId],
                  cardId: Option[DemographicsCardId]
                ) =>
                  makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                    requireAdminRole(userAuth.user) {
                      provideAsync(activeDemographicsCardService.count(questionId, cardId)) { count =>
                        onSuccess(
                          activeDemographicsCardService
                            .list(start, end, sort, order, questionId, cardId)
                        ) { activeDemographicsCards =>
                          complete(
                            (
                              StatusCodes.OK,
                              List(`X-Total-Count`(count.toString)),
                              activeDemographicsCards.map(ActiveDemographicsCardResponse.apply)
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

      override def remove: Route = delete {
        path("admin" / "active-demographics-cards" / id) { id =>
          makeOperation("AdminDeleteActiveDemographicsCard") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsyncOrNotFound(activeDemographicsCardService.get(id)) { _ =>
                  provideAsync(activeDemographicsCardService.delete(id)) { _ =>
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

final case class CreateActiveDemographicsCardRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "f403b357-a528-4657-b4e4-6dc07516f16a")
  demographicsCardId: DemographicsCardId,
  @(ApiModelProperty @field)(dataType = "string", example = "afe70bf6-ae1b-4fd0-9051-0732958ca810")
  questionId: QuestionId
)

object CreateActiveDemographicsCardRequest {
  implicit val decoder: Decoder[CreateActiveDemographicsCardRequest] = deriveDecoder
}

final case class ActiveDemographicsCardResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "1c895cb8-f4fe-45ff-a1c2-24db14324a0f")
  id: ActiveDemographicsCardId,
  @(ApiModelProperty @field)(dataType = "string", example = "f403b357-a528-4657-b4e4-6dc07516f16a")
  demographicsCardId: DemographicsCardId,
  @(ApiModelProperty @field)(dataType = "string", example = "afe70bf6-ae1b-4fd0-9051-0732958ca810")
  questionId: QuestionId
)

object ActiveDemographicsCardResponse {

  def apply(activeDemographicsCard: ActiveDemographicsCard): ActiveDemographicsCardResponse =
    ActiveDemographicsCardResponse(
      id = activeDemographicsCard.id,
      demographicsCardId = activeDemographicsCard.demographicsCardId,
      questionId = activeDemographicsCard.questionId
    )

  implicit val codec: Codec[ActiveDemographicsCardResponse] = deriveCodec

}
