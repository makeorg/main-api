package org.make.api.personality

import akka.http.scaladsl.server._
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.user.{UserResponse, UserServiceComponent}
import org.make.core._
import org.make.core.user.UserId
import io.circe.generic.semiauto._
import io.circe.Encoder
import io.circe.Decoder
import org.make.core.user.User
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Url
import scala.annotation.meta.field
import io.circe.refined._
import org.make.core.profile.Profile

@Api(value = "Personalities")
@Path(value = "/personalities")
trait PersonalityApi extends Directives {

  @ApiOperation(value = "get-personality", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserResponse])))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      )
    )
  )
  @Path(value = "/{userId}")
  def getPersonality: Route

  @ApiOperation(value = "get-personality", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityProfileResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      )
    )
  )
  @Path(value = "/{userId}/profile")
  def getPersonalityProfile: Route

  @ApiOperation(
    value = "update-personality-profile",
    httpMethod = "PUT",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "user", description = "application user"))
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityProfileResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "userId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.personality.PersonalityProfileRequest"
      )
    )
  )
  @Path(value = "/{userId}/profile")
  def modifyPersonalityProfile: Route

  def routes: Route = getPersonality ~ getPersonalityProfile ~ modifyPersonalityProfile

}

trait PersonalityApiComponent {
  def personalityApi: PersonalityApi
}

trait DefaultPersonalityApiComponent extends PersonalityApiComponent with MakeAuthenticationDirectives {
  this: UserServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent =>

  override lazy val personalityApi: PersonalityApi = new DefaultPersonalityApi

  class DefaultPersonalityApi extends PersonalityApi {

    val userId: PathMatcher1[UserId] = Segment.map(id => UserId(id))

    override def getPersonality: Route =
      get {
        path("personalities" / userId) { userId =>
          makeOperation("GetPersonality") { _ =>
            provideAsyncOrNotFound(userService.getPersonality(userId)) { user =>
              complete(UserResponse(user))
            }
          }
        }
      }

    override def getPersonalityProfile: Route = {
      get {
        path("personalities" / userId / "profile") { userId =>
          makeOperation("GetPersonalityProfile") { _ =>
            provideAsyncOrNotFound(userService.getPersonality(userId)) { user =>
              complete(PersonalityProfileResponse.fromUser(user))
            }
          }
        }
      }
    }

    override def modifyPersonalityProfile: Route = {
      put {
        path("personalities" / userId / "profile") { personalityId =>
          makeOperation("UpdatePersonalityProfile") { requestContext =>
            decodeRequest {
              entity(as[PersonalityProfileRequest]) { request =>
                makeOAuth2 { currentUser =>
                  authorize(currentUser.user.userId == personalityId) {
                    provideAsyncOrNotFound(userService.getPersonality(personalityId)) { personality =>
                      val modifiedProfile = personality.profile
                        .orElse(Profile.parseProfile())
                        .map(
                          _.copy(
                            avatarUrl = request.avatarUrl.map(_.value),
                            description = request.description,
                            optInNewsletter = request.optInNewsletter,
                            website = request.website.map(_.value),
                            politicalParty = Some(request.politicalParty)
                          )
                        )

                      val modifiedPersonality = personality.copy(
                        firstName = Some(request.firstName),
                        lastName = Some(request.lastName),
                        profile = modifiedProfile
                      )

                      provideAsync(userService.update(modifiedPersonality, requestContext)) { result =>
                        complete(PersonalityProfileResponse.fromUser(result))
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

final case class PersonalityProfileResponse(
  firstName: Option[String],
  lastName: Option[String],
  avatarUrl: Option[String],
  description: Option[String],
  optInNewsletter: Option[Boolean],
  website: Option[String],
  politicalParty: Option[String]
)

object PersonalityProfileResponse {
  implicit val encoder: Encoder[PersonalityProfileResponse] = deriveEncoder[PersonalityProfileResponse]
  implicit val decoder: Decoder[PersonalityProfileResponse] = deriveDecoder[PersonalityProfileResponse]

  def fromUser(user: User): PersonalityProfileResponse = {
    PersonalityProfileResponse(
      firstName = user.firstName,
      lastName = user.lastName,
      avatarUrl = user.profile.flatMap(_.avatarUrl),
      description = user.profile.flatMap(_.description),
      website = user.profile.flatMap(_.website),
      politicalParty = user.profile.flatMap(_.politicalParty),
      optInNewsletter = user.profile.map(_.optInNewsletter)
    )
  }
}

final case class PersonalityProfileRequest(
  firstName: String,
  lastName: String,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/logo.jpg")
  avatarUrl: Option[String Refined Url],
  description: Option[String],
  optInNewsletter: Boolean,
  @(ApiModelProperty @field)(dataType = "string", example = "https://make.org")
  website: Option[String Refined Url],
  politicalParty: String
)

object PersonalityProfileRequest {
  implicit val encoder: Encoder[PersonalityProfileRequest] = deriveEncoder[PersonalityProfileRequest]
  implicit val decoder: Decoder[PersonalityProfileRequest] = deriveDecoder[PersonalityProfileRequest]
}
