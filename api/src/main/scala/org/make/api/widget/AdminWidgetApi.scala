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

package org.make.api.widget

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import cats.implicits._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.swagger.annotations.{
  Api,
  ApiImplicitParam,
  ApiImplicitParams,
  ApiModelProperty,
  ApiOperation,
  ApiResponse,
  ApiResponses,
  Authorization,
  AuthorizationScope
}
import org.make.api.question.QuestionServiceComponent
import org.make.api.technical.{`X-Total-Count`, MakeAuthenticationDirectives}
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.user.UserServiceComponent
import org.make.core.{CirceFormatters, HttpCodes, Order, ParameterExtractors, ValidationError}
import org.make.core.auth.UserRights
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.technical.Pagination.{End, Start}
import org.make.core.user.User
import org.make.core.widget.{SourceId, Widget, WidgetId}
import scalaoauth2.provider.AuthInfo

import java.time.ZonedDateTime
import javax.ws.rs.Path
import scala.annotation.meta.field

@Api(value = "Admin Widgets")
@Path(value = "/admin/widgets")
trait AdminWidgetApi extends Directives {

  @ApiOperation(
    value = "list-widgets",
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
      new ApiImplicitParam(name = "sourceId", required = true, paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[AdminWidgetResponse]]))
  )
  @Path(value = "/")
  def list: Route

  @ApiOperation(
    value = "get-widget-by-id",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "id", paramType = "path", dataType = "string")))
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[AdminWidgetResponse]))
  )
  @Path(value = "/{id}")
  def getById: Route

  @ApiOperation(
    value = "create-widget",
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
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.widget.AdminWidgetRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.Created, message = "Ok", response = classOf[AdminWidgetResponse]))
  )
  @Path(value = "/")
  def create: Route

  def routes: Route = list ~ getById ~ create

}

trait AdminWidgetApiComponent {
  def adminWidgetApi: AdminWidgetApi
}

trait DefaultAdminWidgetApiComponent
    extends AdminWidgetApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  self: MakeDirectivesDependencies
    with QuestionServiceComponent
    with SourceServiceComponent
    with UserServiceComponent
    with WidgetServiceComponent =>

  override val adminWidgetApi: AdminWidgetApi = new AdminWidgetApi {

    private val id: PathMatcher1[WidgetId] = Segment.map(WidgetId.apply)

    override def list: Route = get {
      path("admin" / "widgets") {
        parameters("sourceId".as[SourceId], "_start".as[Start].?, "_end".as[End].?, "_sort".?, "_order".as[Order].?) {
          (sourceId: SourceId, start: Option[Start], end: Option[End], sort: Option[String], order: Option[Order]) =>
            makeOperation("AdminWidgetsList") { _ =>
              makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                requireAdminRole(userAuth.user) {
                  val list = widgetService.list(sourceId, start, end, sort, order)
                  val count = widgetService.count(sourceId)
                  provideAsync(count) { total =>
                    provideAsync(list) { widgets =>
                      provideAsync(userService.getUsersByUserIds(widgets.map(_.author).distinct)) { authors =>
                        val authorsById = authors.toList.groupByNel(_.userId)
                        complete(
                          StatusCodes.OK,
                          List(`X-Total-Count`(total.toString)),
                          widgets.map(widget => AdminWidgetResponse(widget, authorsById.get(widget.author).map(_.head)))
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

    override def getById: Route = get {
      path("admin" / "widgets" / id) { id =>
        makeOperation("AdminWidgetsGetById") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              provideAsyncOrNotFound(widgetService.get(id)) { widget =>
                provideAsyncOrNotFound(userService.getUser(widget.author)) { author =>
                  complete(AdminWidgetResponse(widget, Some(author)))
                }
              }
            }
          }
        }
      }
    }

    override def create: Route = post {
      path("admin" / "widgets") {
        makeOperation("AdminWidgetsCreate") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[AdminWidgetRequest]) { request: AdminWidgetRequest =>
                  provideAsyncOrBadRequest(
                    sourceService.get(request.sourceId),
                    ValidationError("sourceId", "not_found", Some(s"Source ${request.sourceId.value} doesn't exist"))
                  ) { source =>
                    provideAsyncOrBadRequest(
                      questionService.getQuestion(request.questionId),
                      ValidationError(
                        "questionId",
                        "not_found",
                        Some(s"Question ${request.questionId.value} doesn't exist")
                      )
                    ) { question =>
                      if (question.countries.toList.contains(request.country)) {
                        provideAsyncOrNotFound(userService.getUser(userAuth.user.userId)) { author =>
                          provideAsync(
                            widgetService
                              .create(source, question, request.country, userAuth.user.userId)
                          ) { widget =>
                            complete(StatusCodes.Created, AdminWidgetResponse(widget, Some(author)))
                          }
                        }
                      } else {
                        complete(
                          StatusCodes.BadRequest -> Seq(
                            ValidationError(
                              "country",
                              "invalid_value",
                              Some(s"Country ${request.country} is not one of question's countries")
                            )
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
    }

  }

}

final case class AdminWidgetRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "331ec138-1a68-4432-99a1-983a4200e1d1")
  sourceId: SourceId,
  @(ApiModelProperty @field)(dataType = "string", example = "bf537f16-26d8-43b8-86ce-ae29e3ad6dad")
  questionId: QuestionId,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Country
)

object AdminWidgetRequest {
  implicit val codec: Codec[AdminWidgetRequest] = deriveCodec
}

final case class AdminWidgetResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "331ec138-1a68-4432-99a1-983a4200e1d1")
  id: WidgetId,
  @(ApiModelProperty @field)(dataType = "string", example = "bf537f16-26d8-43b8-86ce-ae29e3ad6dad")
  questionId: QuestionId,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Country,
  script: String,
  @(ApiModelProperty @field)(dataType = "string", example = "V1")
  version: Widget.Version,
  authorDisplayName: Option[String],
  @(ApiModelProperty @field)(dataType = "dateTime")
  createdAt: ZonedDateTime
)

object AdminWidgetResponse extends CirceFormatters {

  def apply(widget: Widget, author: Option[User]): AdminWidgetResponse =
    AdminWidgetResponse(
      id = widget.id,
      questionId = widget.questionId,
      country = widget.country,
      script = widget.script,
      version = widget.version,
      authorDisplayName = author.flatMap(_.displayName),
      createdAt = widget.createdAt
    )

  implicit val codec: Codec[AdminWidgetResponse] = deriveCodec

}
