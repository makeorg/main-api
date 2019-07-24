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

package org.make.api.partner

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, TotalCountHeader}
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.Validation._
import org.make.core.{HttpCodes, ParameterExtractors, Requirement}
import org.make.core.auth.UserRights
import org.make.core.partner.{Partner, PartnerId, PartnerKind}
import org.make.core.question.QuestionId
import org.make.core.user.UserId
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

@Api(
  value = "Admin Partner",
  authorizations = Array(
    new Authorization(
      value = "MakeApi",
      scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
    )
  )
)
@Path(value = "/admin/partners")
trait AdminPartnerApi extends Directives {

  @ApiOperation(value = "post-partner", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.Created, message = "Created", response = classOf[PartnerIdResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "body", paramType = "body", dataType = "org.make.api.partner.CreatePartnerRequest")
    )
  )
  @Path(value = "/")
  def adminPostPartner: Route

  @ApiOperation(value = "put-partner", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PartnerIdResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "body", paramType = "body", dataType = "org.make.api.partner.UpdatePartnerRequest"),
      new ApiImplicitParam(name = "partnerId", paramType = "path", dataType = "string")
    )
  )
  @Path(value = "/{partnerId}")
  def adminPutPartner: Route

  @ApiOperation(value = "get-partners", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[PartnerResponse]]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "questionId", paramType = "query", required = false, dataType = "string"))
  )
  @Path(value = "/")
  def adminGetPartners: Route

  @ApiOperation(value = "get-partner", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PartnerResponse]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "partnerId", paramType = "path", dataType = "string")))
  @Path(value = "/{partnerId}")
  def adminGetPartner: Route

  @ApiOperation(value = "delete-partner", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "NoContent")))
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "partnerId", paramType = "path", dataType = "string")))
  @Path(value = "/{partnerId}")
  def adminDeletePartner: Route

  def routes: Route =
    adminGetPartner ~ adminGetPartners ~ adminPostPartner ~ adminPutPartner ~ adminDeletePartner
}

trait AdminPartnerApiComponent {
  def moderationPartnerApi: AdminPartnerApi
}

trait DefaultAdminPartnerApiComponent
    extends AdminPartnerApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  this: PartnerServiceComponent
    with MakeDataHandlerComponent
    with SessionHistoryCoordinatorServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent =>

  override val moderationPartnerApi: AdminPartnerApi = new AdminPartnerApi {

    private val partnerId: PathMatcher1[PartnerId] = Segment.map(id => PartnerId(id))

    override def adminPostPartner: Route = {
      post {
        path("admin" / "partners") {
          makeOperation("ModerationPostPartner") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[CreatePartnerRequest]) { request =>
                    onSuccess(partnerService.createPartner(request)) { result =>
                      complete(StatusCodes.Created -> PartnerIdResponse(result.partnerId))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def adminPutPartner: Route = {
      put {
        path("admin" / "partners" / partnerId) { partnerId =>
          makeOperation("ModerationPutPartner") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[UpdatePartnerRequest]) { request =>
                    provideAsyncOrNotFound(partnerService.updatePartner(partnerId, request)) { result =>
                      complete(StatusCodes.OK -> PartnerIdResponse(result.partnerId))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def adminGetPartners: Route = {
      get {
        path("admin" / "partners") {
          makeOperation("ModerationGetPartners") { _ =>
            parameters(
              (
                '_start.as[Int].?,
                '_end.as[Int].?,
                '_sort.?,
                '_order.?,
                'questionId.as[QuestionId].?,
                'organisationId.as[UserId].?
              )
            ) {
              (start: Option[Int],
               end: Option[Int],
               sort: Option[String],
               order: Option[String],
               questionId: Option[QuestionId],
               organisationId: Option[UserId]) =>
                makeOAuth2 { auth: AuthInfo[UserRights] =>
                  requireAdminRole(auth.user) {
                    provideAsync(partnerService.find(start.getOrElse(0), end, sort, order, questionId, organisationId)) {
                      result =>
                        provideAsync(partnerService.count(questionId, organisationId)) { count =>
                          complete(
                            (StatusCodes.OK, List(TotalCountHeader(count.toString)), result.map(PartnerResponse.apply))
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

    override def adminGetPartner: Route = {
      get {
        path("admin" / "partners" / partnerId) { partnerId =>
          makeOperation("ModerationGetPartner") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsyncOrNotFound(partnerService.getPartner(partnerId)) { partner =>
                  complete(PartnerResponse(partner))
                }
              }
            }
          }
        }
      }
    }

    override def adminDeletePartner: Route = {
      delete {
        path("admin" / "partners" / partnerId) { partnerId =>
          makeOperation("ModerationDeletePartner") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsync(partnerService.deletePartner(partnerId)) { _ =>
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

final case class CreatePartnerRequest(name: String,
                                      logo: Option[String],
                                      link: Option[String],
                                      organisationId: Option[UserId],
                                      partnerKind: PartnerKind,
                                      questionId: QuestionId,
                                      weight: Float) {
  validate(
    Requirement(
      field = "logo",
      "madatory",
      condition = () => organisationId.isEmpty && logo.nonEmpty || organisationId.nonEmpty,
      message = ()   => "logo must not be empty"
    ),
    Requirement(
      field = "link",
      "madatory",
      condition = () => partnerKind != PartnerKind.Founder || link.nonEmpty,
      message = ()   => "link must not be empty"
    )
  )
}

object CreatePartnerRequest {
  implicit val decoder: Decoder[CreatePartnerRequest] = deriveDecoder[CreatePartnerRequest]
}

final case class UpdatePartnerRequest(name: String,
                                      logo: Option[String],
                                      link: Option[String],
                                      organisationId: Option[UserId],
                                      partnerKind: PartnerKind,
                                      weight: Float) {
  validate(
    Requirement(
      field = "logo",
      "madatory",
      condition = () => organisationId.isEmpty && logo.nonEmpty || organisationId.nonEmpty,
      message = ()   => "logo must not be empty"
    ),
    Requirement(
      field = "link",
      "madatory",
      condition = () => partnerKind != PartnerKind.Founder || link.nonEmpty,
      message = ()   => "link must not be empty"
    )
  )
}

object UpdatePartnerRequest {
  implicit val decoder: Decoder[UpdatePartnerRequest] = deriveDecoder[UpdatePartnerRequest]
}

final case class PartnerResponse(id: PartnerId,
                                 name: String,
                                 logo: Option[String],
                                 link: Option[String],
                                 organisationId: Option[UserId],
                                 partnerKind: PartnerKind,
                                 weight: Float)

object PartnerResponse {
  def apply(partner: Partner): PartnerResponse = PartnerResponse(
    id = partner.partnerId,
    name = partner.name,
    logo = partner.logo,
    link = partner.link,
    organisationId = partner.organisationId,
    partnerKind = partner.partnerKind,
    weight = partner.weight
  )

  implicit val decoder: Decoder[PartnerResponse] = deriveDecoder[PartnerResponse]
  implicit val encoder: Encoder[PartnerResponse] = deriveEncoder[PartnerResponse]
}

final case class PartnerIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e") partnerId: PartnerId
)

object PartnerIdResponse {
  implicit val encoder: Encoder[PartnerIdResponse] = deriveEncoder[PartnerIdResponse]
}
