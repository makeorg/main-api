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
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Json, Printer}
import io.circe.syntax._
import io.swagger.annotations._
import org.make.api.demographics.AdminDemographicsCardRequest.LabelValue
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.{`X-Total-Count`, MakeAuthenticationDirectives}
import org.make.core._
import org.make.core.auth.UserRights
import org.make.core.demographics.DemographicsCard.Layout
import org.make.core.demographics.{DemographicsCard, DemographicsCardId}
import org.make.core.reference.Language
import org.make.core.technical.Pagination.{End, Start}
import scalaoauth2.provider.AuthInfo

import javax.ws.rs.Path
import scala.annotation.meta.field

@Api(value = "Admin DemographicsCards")
@Path(value = "/admin/demographics-cards")
trait AdminDemographicsCardApi extends Directives {

  @ApiOperation(
    value = "list-demographics-cards",
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
      new ApiImplicitParam(name = "language", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "dataType", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(
      new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[AdminDemographicsCardResponse]])
    )
  )
  @Path(value = "/")
  def list: Route

  @ApiOperation(
    value = "get-demographics-card-by-id",
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
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[AdminDemographicsCardResponse]))
  )
  @Path(value = "/{id}")
  def getById: Route

  @ApiOperation(
    value = "create-demographics-card",
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
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.demographics.AdminDemographicsCardRequest"
      )
    )
  )
  @ApiResponses(
    value = Array(
      new ApiResponse(code = HttpCodes.Created, message = "Ok", response = classOf[AdminDemographicsCardResponse])
    )
  )
  @Path(value = "/")
  def create: Route

  @ApiOperation(
    value = "update-demographics-card",
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
      new ApiImplicitParam(name = "id", paramType = "path", dataType = "string"),
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.demographics.AdminDemographicsCardRequest"
      )
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[AdminDemographicsCardResponse]))
  )
  @Path(value = "/{id}")
  def update: Route

  def routes: Route = list ~ getById ~ create ~ update

}

trait AdminDemographicsCardApiComponent {
  def adminDemographicsCardApi: AdminDemographicsCardApi
}

trait DefaultAdminDemographicsCardApiComponent
    extends AdminDemographicsCardApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  self: MakeDirectivesDependencies with DemographicsCardServiceComponent =>

  override val adminDemographicsCardApi: AdminDemographicsCardApi = new AdminDemographicsCardApi {

    private val printer: Printer = Printer.noSpaces

    private val id: PathMatcher1[DemographicsCardId] = Segment.map(DemographicsCardId.apply)

    override def list: Route = get {
      path("admin" / "demographics-cards") {
        parameters(
          "_start".as[Start].?,
          "_end".as[End].?,
          "_sort".?,
          "_order".as[Order].?,
          "language".as[Language].?,
          "dataType".?
        ) {
          (
            start: Option[Start],
            end: Option[End],
            sort: Option[String],
            order: Option[Order],
            language: Option[Language],
            dataType: Option[String]
          ) =>
            makeOperation("AdminDemographicsCardsList") { _ =>
              makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                requireAdminRole(userAuth.user) {
                  provideAsync(demographicsCardService.list(start, end, sort, order, language, dataType)) {
                    demographicsCards =>
                      provideAsync(demographicsCardService.count(language, dataType)) { count =>
                        complete(
                          (
                            StatusCodes.OK,
                            List(`X-Total-Count`(count.toString)),
                            demographicsCards.map(AdminDemographicsCardResponse.apply)
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

    override def getById: Route = get {
      path("admin" / "demographics-cards" / id) { id =>
        makeOperation("AdminDemographicsCardsGetById") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              provideAsyncOrNotFound(demographicsCardService.get(id)) { demographicsCard =>
                complete(AdminDemographicsCardResponse(demographicsCard))
              }
            }
          }
        }
      }
    }

    override def create: Route = post {
      path("admin" / "demographics-cards") {
        makeOperation("AdminDemographicsCardsCreate") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireSuperAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[AdminDemographicsCardRequest]) { request: AdminDemographicsCardRequest =>
                  Validation.validate(
                    Seq(
                      Validation.maxLength("name", 256, request.name),
                      Validation.maxLength("title", 64, request.title),
                      Validation.requireValidSlug("dataType", Some(request.dataType), Some("Invalid slug"))
                    ) ++ request.validateParameters: _*
                  )
                  provideAsync(
                    demographicsCardService
                      .create(
                        name = request.name,
                        layout = request.layout,
                        dataType = request.dataType,
                        language = request.language,
                        title = request.title,
                        parameters = printer.print(request.parameters)
                      )
                  ) { demographicsCard =>
                    complete(StatusCodes.Created, AdminDemographicsCardResponse(demographicsCard))
                  }
                }
              }
            }
          }
        }
      }
    }

    override def update: Route = put {
      path("admin" / "demographics-cards" / id) { id =>
        makeOperation("AdminDemographicsCardsUpdate") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireSuperAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[AdminDemographicsCardRequest]) { request: AdminDemographicsCardRequest =>
                  Validation.validate(
                    Seq(
                      Validation.maxLength("name", 256, request.name),
                      Validation.maxLength("title", 64, request.title),
                      Validation.requireValidSlug("dataType", Some(request.dataType), Some("Invalid slug"))
                    ) ++ request.validateParameters: _*
                  )
                  provideAsyncOrNotFound(
                    demographicsCardService
                      .update(
                        id = id,
                        name = request.name,
                        layout = request.layout,
                        dataType = request.dataType,
                        language = request.language,
                        title = request.title,
                        parameters = printer.print(request.parameters)
                      )
                  ) { demographicsCard =>
                    complete(AdminDemographicsCardResponse(demographicsCard))
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

final case class AdminDemographicsCardRequest(
  name: String,
  @(ApiModelProperty @field)(
    dataType = "string",
    allowableValues = "Select,OneColumnRadio,ThreeColumnsRadio",
    example = "OneColumnRadio"
  )
  layout: Layout,
  @(ApiModelProperty @field)(dataType = "string", example = "age,gender,region")
  dataType: String,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language,
  title: String,
  @(ApiModelProperty @field)(dataType = "object")
  parameters: Json
) {
  @(ApiModelProperty @field)(hidden = true)
  val validateParameters: Seq[Requirement] = {
    def validLabelsLengths(layout: Layout, labelLength: Int = 64) = parameters.as[Seq[LabelValue]] match {
      case Right(value) =>
        Validation.validateField(
          field = "parameters",
          key = "invalid_value",
          condition = value.forall(_.label.length < labelLength),
          message = s"At least one parameter label exceeds limit label length of $labelLength for layout $layout"
        )
      case Left(error) =>
        Validation
          .validateField(field = "parameters", key = "invalid_value", condition = false, message = error.message)

    }
    def validateSize(layout: Layout, size: Int) =
      Validation.validateField(
        field = "parameters",
        key = "invalid_value",
        condition = parameters.asArray.exists(_.size < size),
        message = s"$layout card can contain max $size parameters"
      )
    layout match {
      case l @ Layout.OneColumnRadio    => Seq(validLabelsLengths(l), validateSize(l, 3))
      case l @ Layout.ThreeColumnsRadio => Seq(validLabelsLengths(l), validateSize(l, 9))
      case l                            => Seq(validLabelsLengths(l))
    }
  }
}

object AdminDemographicsCardRequest {
  implicit val codec: Codec[AdminDemographicsCardRequest] = deriveCodec

  final case class LabelValue(label: String, value: String)
  object LabelValue {
    implicit val codec: Codec[LabelValue] = deriveCodec
  }
}

final case class AdminDemographicsCardResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "331ec138-1a68-4432-99a1-983a4200e1d1")
  id: DemographicsCardId,
  name: String,
  @(ApiModelProperty @field)(
    dataType = "string",
    allowableValues = "Select,OneColumnRadio,ThreeColumnsRadio",
    example = "OneColumnRadio"
  )
  layout: Layout,
  @(ApiModelProperty @field)(dataType = "string", example = "age,gender,region")
  dataType: String,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language,
  title: String,
  @(ApiModelProperty @field)(dataType = "object")
  parameters: Json
)

object AdminDemographicsCardResponse {

  def apply(card: DemographicsCard): AdminDemographicsCardResponse =
    AdminDemographicsCardResponse(
      id = card.id,
      name = card.name,
      layout = card.layout,
      dataType = card.dataType,
      language = card.language,
      title = card.title,
      parameters = card.parameters.asJson
    )

  implicit val codec: Codec[AdminDemographicsCardResponse] = deriveCodec

}
