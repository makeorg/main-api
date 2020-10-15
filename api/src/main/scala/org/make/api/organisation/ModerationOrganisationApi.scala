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
import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.boolean.And
import eu.timepit.refined.collection.MaxSize
import eu.timepit.refined.string.Url
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.circe.refined._
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{`X-Total-Count`, IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.user.{ProfileRequest, ProfileResponse}
import org.make.core.Validation.{validateOptional, _}
import org.make.core.auth.UserRights
import org.make.core.reference.{Country, Language}
import org.make.core.user.{User, UserId}
import org.make.core.{CirceFormatters, HttpCodes, Order, ParameterExtractors}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

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
trait ModerationOrganisationApi extends Directives {

  @ApiOperation(value = "post-organisation", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OrganisationIdResponse]))
  )
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
  def moderationPostOrganisation: Route

  @ApiOperation(value = "put-organisation", httpMethod = "PUT", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OrganisationIdResponse]))
  )
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
  def moderationPutOrganisation: Route

  @ApiOperation(value = "get-organisation", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "organisationId", paramType = "path", dataType = "string"))
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OrganisationResponse]))
  )
  @Path(value = "/{organisationId}")
  def moderationGetOrganisation: Route

  @ApiOperation(value = "get-organisations", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[OrganisationResponse]]))
  )
  @Path(value = "/")
  def moderationGetOrganisations: Route

  def routes: Route =
    moderationPostOrganisation ~ moderationPutOrganisation ~ moderationGetOrganisation ~ moderationGetOrganisations

}

trait ModerationOrganisationApiComponent {
  def moderationOrganisationApi: ModerationOrganisationApi
}

trait DefaultModerationOrganisationApiComponent
    extends ModerationOrganisationApiComponent
    with MakeAuthenticationDirectives
    with StrictLogging
    with ParameterExtractors {
  this: OrganisationServiceComponent
    with MakeDataHandlerComponent
    with SessionHistoryCoordinatorServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent =>

  override lazy val moderationOrganisationApi: ModerationOrganisationApi = new DefaultModerationOrganisationApi

  class DefaultModerationOrganisationApi extends ModerationOrganisationApi {

    private val organisationId: PathMatcher1[UserId] = Segment.map(id => UserId(id))

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
                            name = request.organisationName.value,
                            email = request.email,
                            password = request.password,
                            avatar = request.avatarUrl.map(_.value),
                            description = request.description.map(_.value),
                            country = request.country.orElse(requestContext.country).getOrElse(Country("FR")),
                            language = request.language.orElse(requestContext.language).getOrElse(Language("fr")),
                            website = request.website.map(_.value)
                          ),
                          requestContext
                        )
                    ) { result =>
                      complete(StatusCodes.Created -> OrganisationIdResponse(result.userId))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    def moderationPutOrganisation: Route = {
      put {
        path("moderation" / "organisations" / organisationId) { organisationId =>
          makeOperation("ModerationUpdateOrganisation") { requestContext =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[ModerationUpdateOrganisationRequest]) { request: ModerationUpdateOrganisationRequest =>
                    provideAsyncOrNotFound(organisationService.getOrganisation(organisationId)) { organisation =>
                      onSuccess(
                        organisationService
                          .update(
                            organisation = organisation.copy(
                              organisationName = Some(request.organisationName.value),
                              email = request.email.getOrElse(organisation.email).toLowerCase,
                              profile = request.profile.flatMap(_.toProfile)
                            ),
                            moderatorId = Some(auth.user.userId),
                            oldEmail = organisation.email,
                            requestContext = requestContext
                          )
                      ) { organisationId =>
                        complete(StatusCodes.OK -> OrganisationIdResponse(organisationId))
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

    def moderationGetOrganisation: Route =
      get {
        path("moderation" / "organisations" / organisationId) { organisationId =>
          makeOperation("ModerationGetOrganisation") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsyncOrNotFound(organisationService.getOrganisation(organisationId)) { user =>
                  complete(OrganisationResponse(user))
                }
              }
            }
          }
        }
      }

    def moderationGetOrganisations: Route =
      get {
        path("moderation" / "organisations") {
          makeOperation("ModerationGetOrganisations") { _ =>
            parameters("_start".as[Int].?, "_end".as[Int].?, "_sort".?, "_order".as[Order].?, "organisationName".?) {
              (
                start: Option[Int],
                end: Option[Int],
                sort: Option[String],
                order: Option[Order],
                organisationName: Option[String]
              ) =>
                makeOAuth2 { auth: AuthInfo[UserRights] =>
                  requireAdminRole(auth.user) {
                    provideAsync(organisationService.count(organisationName)) { count =>
                      provideAsync(organisationService.find(start.getOrElse(0), end, sort, order, organisationName)) {
                        result =>
                          complete(
                            (
                              StatusCodes.OK,
                              List(`X-Total-Count`(count.toString)),
                              result.map(OrganisationResponse.apply)
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

@ApiModel
final case class ModerationCreateOrganisationRequest(
  @(ApiModelProperty @field)(dataType = "string")
  organisationName: String Refined MaxSize[W.`256`.T],
  @(ApiModelProperty @field)(dataType = "string", example = "yopmail+test@make.org")
  email: String,
  @(ApiModelProperty @field)(dataType = "string", required = false)
  password: Option[String],
  @(ApiModelProperty @field)(dataType = "string", required = false)
  description: Option[String Refined MaxSize[W.`450`.T]],
  @(ApiModelProperty @field)(dataType = "string", required = false, example = "https://example.com/avatar.png")
  avatarUrl: Option[String Refined And[Url, MaxSize[W.`2048`.T]]],
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Option[Country],
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Option[Language],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/website")
  website: Option[String Refined Url]
) {
  OrganisationValidation.validateCreate(
    organisationName = organisationName.value,
    email = email,
    description = description.map(_.value)
  )
}

object ModerationCreateOrganisationRequest {
  implicit val decoder: Decoder[ModerationCreateOrganisationRequest] =
    deriveDecoder[ModerationCreateOrganisationRequest]
}

final case class ModerationUpdateOrganisationRequest(
  @(ApiModelProperty @field)(dataType = "string", required = false)
  organisationName: String Refined MaxSize[W.`256`.T],
  @(ApiModelProperty @field)(dataType = "string", required = false, example = "yopmail+test@make.org")
  email: Option[String] = None,
  profile: Option[ProfileRequest]
) {
  OrganisationValidation.validateUpdate(
    organisationName = organisationName.value,
    email = email,
    description = profile.flatMap(_.description.map(_.value)),
    profile
  )
}

object ModerationUpdateOrganisationRequest {
  implicit val decoder: Decoder[ModerationUpdateOrganisationRequest] =
    deriveDecoder[ModerationUpdateOrganisationRequest]
}

private object OrganisationValidation {
  def validateCreate(organisationName: String, email: String, description: Option[String]): Unit = {
    validateOptional(
      Some(mandatoryField("email", email)),
      Some(mandatoryField("name", organisationName)),
      Some(validateUserInput("organisationName", organisationName, None)),
      Some(validateUserInput("email", email, None)),
      Some(validateEmail("email", email, None)),
      description.map(value => validateUserInput("description", value, None))
    )
  }

  def validateUpdate(
    organisationName: String,
    email: Option[String],
    description: Option[String],
    profileRequest: Option[ProfileRequest]
  ): Unit = {
    validateOptional(
      Some(validateUserInput("organisationName", organisationName, None)),
      description.map(value => validateUserInput("description", value, None)),
      email.map(value       => validateUserInput("email", value, None))
    )

    validateOptional(email.map(value => validateEmail("email", value, None)))
    profileRequest.foreach(ProfileRequest.validateProfileRequest)
  }
}

final case class OrganisationResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d5612156-4954-49f7-9c78-0eda3d44164c")
  id: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "yopmail+test@make.org")
  email: String,
  organisationName: Option[String],
  profile: Option[ProfileResponse],
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language
)

object OrganisationResponse extends CirceFormatters {
  implicit val encoder: Encoder[OrganisationResponse] = deriveEncoder[OrganisationResponse]
  implicit val decoder: Decoder[OrganisationResponse] = deriveDecoder[OrganisationResponse]

  def apply(user: User): OrganisationResponse = OrganisationResponse(
    id = user.userId,
    email = user.email,
    organisationName = user.organisationName,
    profile = user.profile.map(ProfileResponse.fromProfile),
    country = user.country,
    language = user.language
  )
}

final case class OrganisationProfileRequest(
  organisationName: String,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/avatar.png")
  avatarUrl: Option[String Refined Url],
  description: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/website")
  website: Option[String Refined Url],
  optInNewsletter: Boolean
) {
  private val maxDescriptionLength = 450

  validateOptional(
    Some(requireNonEmpty("organisationName", organisationName, Some("organisationName should not be an empty string"))),
    Some(validateUserInput("firstName", organisationName, None)),
    description.map(value => maxLength("description", maxDescriptionLength, value)),
    Some(validateOptionalUserInput("description", description, None))
  )
}

object OrganisationProfileRequest {
  implicit val encoder: Encoder[OrganisationProfileRequest] = deriveEncoder[OrganisationProfileRequest]
  implicit val decoder: Decoder[OrganisationProfileRequest] = deriveDecoder[OrganisationProfileRequest]
}

final case class OrganisationProfileResponse(
  organisationName: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/avatar.png")
  avatarUrl: Option[String],
  description: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/website")
  website: Option[String],
  optInNewsletter: Boolean
)

object OrganisationProfileResponse {
  implicit val encoder: Encoder[OrganisationProfileResponse] = deriveEncoder[OrganisationProfileResponse]
  implicit val decoder: Decoder[OrganisationProfileResponse] = deriveDecoder[OrganisationProfileResponse]
}

final case class OrganisationIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e") userId: UserId
)

object OrganisationIdResponse {
  implicit val encoder: Encoder[OrganisationIdResponse] = deriveEncoder[OrganisationIdResponse]
}
