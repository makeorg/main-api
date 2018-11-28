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

package org.make.api.organisation

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, TotalCountHeader}
import org.make.api.user._
import org.make.core.Validation.{maxLength, validate, _}
import org.make.core.auth.UserRights
import org.make.core.profile.Profile
import org.make.core.reference.{Country, Language}
import org.make.core.user.{User, UserId}
import org.make.core.{CirceFormatters, HttpCodes, Validation}
import scalaoauth2.provider.AuthInfo

@Api(
  value = "Moderation Organisation",
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
@Path(value = "/moderation/organisations")
trait ModerationOrganisationApi extends MakeAuthenticationDirectives with StrictLogging {
  this: OrganisationServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent =>

  @ApiOperation(value = "post-organisation", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserId])))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.organisation.ModerationCreateOrganisationRequest"
      )
    )
  )
  @Path(value = "/")
  def moderationPostOrganisation: Route = {
    post {
      path("moderation" / "organisations") {
        makeOperation("ModerationPostOrganisation") { requestContext =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[ModerationCreateOrganisationRequest]) { request: ModerationCreateOrganisationRequest =>
                  onSuccess(
                    organisationService
                      .register(
                        OrganisationRegisterData(
                          name = request.organisationName,
                          email = request.email,
                          password = request.password,
                          avatar = request.avatarUrl,
                          description = request.description,
                          country = request.country.orElse(requestContext.country).getOrElse(Country("FR")),
                          language = request.language.orElse(requestContext.language).getOrElse(Language("fr"))
                        ),
                        requestContext
                      )
                  ) { result =>
                    complete(StatusCodes.Created -> UserResponse(result))
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "put-organisation", httpMethod = "PUT", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserId])))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.organisation.ModerationUpdateOrganisationRequest"
      ),
      new ApiImplicitParam(name = "organisationId", paramType = "path", dataType = "string")
    )
  )
  @Path(value = "/{organisationId}")
  def moderationPutOrganisation: Route = {
    put {
      path("moderation" / "organisations" / organisationId) { organisationId =>
        makeOperation("ModerationUpdateOrganisation") { requestContext =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[ModerationUpdateOrganisationRequest]) { request: ModerationUpdateOrganisationRequest =>
                  provideAsyncOrNotFound(organisationService.getOrganisation(organisationId)) { organisation =>
                    val maybeEmail = request.email match {
                      case Some(email) if email == organisation.email => None
                      case email                                      => email
                    }
                    onSuccess(
                      organisationService
                        .update(
                          organisationId,
                          OrganisationUpdateData(
                            name = request.organisationName,
                            email = maybeEmail,
                            avatar = request.profile.flatMap(_.avatarUrl),
                            description = request.profile.flatMap(_.description)
                          ),
                          requestContext
                        )
                    ) { organisationId =>
                      complete(StatusCodes.OK -> Map("organisationId" -> organisationId))
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

  @ApiOperation(value = "get-organisation", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "organisationId", paramType = "path", dataType = "string"))
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserResponse])))
  @Path(value = "/{organisationId}")
  def moderationGetOrganisation: Route =
    get {
      path("moderation" / "organisations" / organisationId) { organisationId =>
        makeOperation("ModerationGetOrganisation") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              provideAsyncOrNotFound(organisationService.getOrganisation(organisationId)) { user =>
                complete(OrganisationResponse(user))
              }
            }
          }
        }
      }
    }

  @ApiOperation(value = "get-organisations", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[UserResponse]]))
  )
  @Path(value = "/")
  def moderationGetOrganisations: Route =
    get {
      path("moderation" / "organisations") {
        makeOperation("ModerationGetOrganisations") { _ =>
          parameters(('_start.as[Int].?, '_end.as[Int].?, '_sort.?, '_order.?)) {
            (start: Option[Int], end: Option[Int], sort: Option[String], order: Option[String]) =>
              makeOAuth2 { auth: AuthInfo[UserRights] =>
                requireModerationRole(auth.user) {
                  order.foreach { orderValue =>
                    Validation.validate(
                      Validation
                        .validChoices("_order", Some("Invalid order"), Seq(orderValue.toLowerCase), Seq("desc", "asc"))
                    )
                  }
                  provideAsync(organisationService.count()) { count =>
                    provideAsync(organisationService.find(start.getOrElse(0), end, sort, order)) { result =>
                      complete(
                        (StatusCodes.OK, List(TotalCountHeader(count.toString)), result.map(OrganisationResponse.apply))
                      )
                    }
                  }
                }
              }
          }
        }
      }
    }

  val moderationOrganisationRoutes
    : Route = moderationPostOrganisation ~ moderationPutOrganisation ~ moderationGetOrganisation ~ moderationGetOrganisations

  private val organisationId: PathMatcher1[UserId] = Segment.map(id => UserId(id))
}

@ApiModel
final case class ModerationCreateOrganisationRequest(organisationName: String,
                                                     email: String,
                                                     password: Option[String],
                                                     description: Option[String],
                                                     avatarUrl: Option[String],
                                                     country: Option[Country],
                                                     language: Option[Language]) {
  OrganisationValidation.validateCreate(organisationName = organisationName, email = email, description = description)
}

object ModerationCreateOrganisationRequest {
  implicit val decoder: Decoder[ModerationCreateOrganisationRequest] =
    deriveDecoder[ModerationCreateOrganisationRequest]
}

final case class ModerationUpdateOrganisationRequest(organisationName: Option[String] = None,
                                                     email: Option[String] = None,
                                                     profile: Option[Profile]) {
  OrganisationValidation.validateUpdate(
    organisationName = organisationName,
    description = profile.flatMap(_.description)
  )
}

object ModerationUpdateOrganisationRequest {
  implicit val decoder: Decoder[ModerationUpdateOrganisationRequest] =
    deriveDecoder[ModerationUpdateOrganisationRequest]
}

private object OrganisationValidation {
  private val maxNameLength = 256
  private val maxDescriptionLength = 450

  def validateCreate(organisationName: String, email: String, description: Option[String]): Unit = {
    validate(
      Some(mandatoryField("email", email)),
      Some(mandatoryField("name", organisationName)),
      Some(maxLength("name", maxNameLength, organisationName)),
      description.map(value => maxLength("description", maxDescriptionLength, value))
    )
  }

  def validateUpdate(organisationName: Option[String], description: Option[String]): Unit = {
    validate(
      organisationName.map(value => maxLength("organisationName", maxNameLength, value)),
      description.map(value      => maxLength("description", maxDescriptionLength, value))
    )
  }
}

case class OrganisationResponse(id: UserId,
                                email: String,
                                organisationName: Option[String],
                                profile: Option[Profile],
                                country: Country,
                                language: Language)

object OrganisationResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[OrganisationResponse] = deriveEncoder[OrganisationResponse]
  implicit val decoder: Decoder[OrganisationResponse] = deriveDecoder[OrganisationResponse]

  def apply(user: User): OrganisationResponse = OrganisationResponse(
    id = user.userId,
    email = user.email,
    organisationName = user.organisationName,
    profile = user.profile,
    country = user.country,
    language = user.language,
  )
}
