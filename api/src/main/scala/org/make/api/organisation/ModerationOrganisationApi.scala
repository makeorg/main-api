package org.make.api.organisation

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.StrictLogging
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.user._
import org.make.core.HttpCodes
import org.make.core.Validation.{maxLength, validate, _}
import org.make.core.auth.UserRights
import org.make.core.user.UserId
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
                          name = request.name,
                          email = request.email,
                          password = Some(request.password),
                          avatar = request.avatar,
                          country = request.country.orElse(requestContext.country).getOrElse("FR"),
                          language = request.language.orElse(requestContext.language).getOrElse("fr")
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
        makeOperation("ModerationUpdateOrganisation") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[ModerationUpdateOrganisationRequest]) { request: ModerationUpdateOrganisationRequest =>
                  provideAsyncOrNotFound(organisationService.getOrganisation(organisationId)) { _ =>
                    onSuccess(
                      organisationService
                        .update(
                          organisationId,
                          OrganisationUpdateData(name = request.name, email = request.email, avatar = request.avatar)
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
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[UserResponse]]))
  )
  @Path(value = "/")
  def moderationGetOrganisations: Route =
    get {
      path("moderation" / "organisations") {
        makeOperation("ModerationGetOrganisations") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              provideAsync(organisationService.getOrganisations) { result =>
                complete(result.map(UserResponse.apply))
              }
            }
          }
        }

      }
    }

  val moderationOrganisationRoutes
    : Route = moderationPostOrganisation ~ moderationPutOrganisation ~ moderationGetOrganisations

  private val organisationId: PathMatcher1[UserId] = Segment.map(id => UserId(id))
}

@ApiModel
final case class ModerationCreateOrganisationRequest(name: String,
                                                     email: String,
                                                     password: String,
                                                     avatar: Option[String],
                                                     country: Option[String],
                                                     language: Option[String]) {
  OrganisationValidation.validateCreate(name = name, email = email)
}

object ModerationCreateOrganisationRequest {
  implicit val decoder: Decoder[ModerationCreateOrganisationRequest] =
    deriveDecoder[ModerationCreateOrganisationRequest]
}

final case class ModerationUpdateOrganisationRequest(name: Option[String] = None,
                                                     email: Option[String] = None,
                                                     avatar: Option[String] = None) {
  name.foreach(name => OrganisationValidation.validateUpdate(name = name))
}

object ModerationUpdateOrganisationRequest {
  implicit val decoder: Decoder[ModerationUpdateOrganisationRequest] =
    deriveDecoder[ModerationUpdateOrganisationRequest]
}

private object OrganisationValidation {
  private val maxNameLength = 256

  def validateCreate(name: String, email: String): Unit = {
    validate(mandatoryField("email", email), mandatoryField("name", name), maxLength("name", maxNameLength, name))
  }

  def validateUpdate(name: String): Unit =
    validate(maxLength("name", maxNameLength, name))
}
