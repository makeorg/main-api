package org.make.api.personality

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations.{ApiImplicitParam, _}
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{`X-Total-Count`, IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.user.{UserRegisterData, UserServiceComponent}
import org.make.core.Validation._
import org.make.core._
import org.make.core.auth.UserRights
import org.make.core.profile.{Gender, Profile}
import org.make.core.reference.{Country, Language}
import org.make.core.user.{Role, User, UserId, UserType}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

@Api(
  value = "Admin Personalities",
  authorizations = Array(
    new Authorization(
      value = "MakeApi",
      scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
    )
  )
)
@Path(value = "/admin/personalities")
trait AdminPersonalityApi extends Directives {

  @ApiOperation(value = "get-personality", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityResponse]))
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
  @Path(value = "/{userId}")
  def getPersonality: Route

  @ApiOperation(value = "get-personalities", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "email", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "firstName", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "lastName", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[PersonalityResponse]]))
  )
  @Path(value = "/")
  def getPersonalities: Route

  @ApiOperation(value = "create-personality", httpMethod = "POST", code = HttpCodes.Created)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.personality.CreatePersonalityRequest"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityResponse]))
  )
  @Path(value = "/")
  def createPersonality: Route

  @ApiOperation(value = "update-personality", httpMethod = "PUT", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.personality.UpdatePersonalityRequest"
      ),
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityResponse]))
  )
  @Path(value = "/{userId}")
  def updatePersonality: Route

  def routes: Route = getPersonality ~ getPersonalities ~ createPersonality ~ updatePersonality

}

trait AdminPersonalityApiComponent {
  def adminPersonalityApi: AdminPersonalityApi
}

trait DefaultAdminPersonalityApiComponent
    extends AdminPersonalityApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  this: MakeDataHandlerComponent
    with SessionHistoryCoordinatorServiceComponent
    with UserServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent =>

  override lazy val adminPersonalityApi: AdminPersonalityApi = new DefaultAdminPersonalityApi

  class DefaultAdminPersonalityApi extends AdminPersonalityApi {

    val userId: PathMatcher1[UserId] = Segment.map(UserId.apply)

    override def getPersonality: Route = get {
      path("admin" / "personalities" / userId) { userId =>
        makeOperation("GetPersonality") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              provideAsyncOrNotFound(userService.getUser(userId)) { user =>
                complete(PersonalityResponse(user))
              }
            }
          }
        }
      }
    }

    override def getPersonalities: Route = get {
      path("admin" / "personalities") {
        makeOperation("GetPersonalities") { _ =>
          parameters(('_start.as[Int].?, '_end.as[Int].?, '_sort.?, '_order.?, 'email.?, 'firstName.?, 'lastName.?)) {
            (start: Option[Int],
             end: Option[Int],
             sort: Option[String],
             order: Option[String],
             email: Option[String],
             firstName: Option[String],
             lastName: Option[String]) =>
              makeOAuth2 { auth: AuthInfo[UserRights] =>
                requireAdminRole(auth.user) {
                  provideAsync(
                    userService.adminCountUsers(
                      email = email,
                      firstName = firstName,
                      lastName = lastName,
                      role = None,
                      userType = Some(UserType.UserTypePersonality)
                    )
                  ) { count =>
                    provideAsync(
                      userService.adminFindUsers(
                        start.getOrElse(0),
                        end,
                        sort,
                        order,
                        email = email,
                        firstName = firstName,
                        lastName = lastName,
                        role = None,
                        userType = Some(UserType.UserTypePersonality)
                      )
                    ) { users =>
                      complete(
                        (StatusCodes.OK, List(`X-Total-Count`(count.toString)), users.map(PersonalityResponse.apply))
                      )
                    }
                  }
                }
              }
          }
        }
      }
    }

    override def createPersonality: Route = post {
      path("admin" / "personalities") {
        makeOperation("CreatePersonality") { requestContext =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[CreatePersonalityRequest]) { request: CreatePersonalityRequest =>
                  provideAsync(
                    userService
                      .registerPersonality(
                        UserRegisterData(
                          email = request.email,
                          firstName = request.firstName,
                          lastName = request.lastName,
                          password = None,
                          lastIp = None,
                          country = request.country,
                          language = request.language,
                          optIn = Some(false),
                          optInPartner = Some(false),
                          roles = Seq(Role.RoleCitizen),
                          publicProfile = true
                        ),
                        requestContext
                      )
                  ) { result =>
                    complete(StatusCodes.Created -> PersonalityResponse(result))
                  }
                }
              }
            }
          }
        }
      }
    }

    override def updatePersonality: Route =
      put {
        path("admin" / "personalities" / userId) { userId =>
          makeOperation("UpdatePersonality") { requestContext =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                decodeRequest {
                  entity(as[UpdatePersonalityRequest]) { request: UpdatePersonalityRequest =>
                    provideAsyncOrNotFound(userService.getUser(userId)) { user =>
                      val lowerCasedEmail: String = request.email.getOrElse(user.email).toLowerCase()
                      provideAsync(userService.getUserByEmail(lowerCasedEmail)) { maybeUser =>
                        maybeUser.foreach { userToCheck =>
                          Validation.validate(
                            Validation.validateField(
                              field = "email",
                              "already_registered",
                              condition = userToCheck.userId.value == user.userId.value,
                              message = s"Email $lowerCasedEmail already exists"
                            )
                          )
                        }
                        onSuccess(
                          userService.update(
                            user.copy(
                              email = lowerCasedEmail,
                              firstName = request.firstName.orElse(user.firstName),
                              lastName = request.lastName.orElse(user.lastName),
                              country = request.country.getOrElse(user.country),
                              language = request.language.getOrElse(user.language),
                              profile = user.profile
                                .map(
                                  _.copy(
                                    avatarUrl = request.avatarUrl.orElse(user.profile.flatMap(_.avatarUrl)),
                                    description = request.description.orElse(user.profile.flatMap(_.description)),
                                    gender = request.gender.orElse(user.profile.flatMap(_.gender)),
                                    genderName = request.genderName.orElse(user.profile.flatMap(_.genderName))
                                  )
                                )
                                .orElse(
                                  Profile.parseProfile(
                                    avatarUrl = request.avatarUrl,
                                    description = request.description,
                                    gender = request.gender,
                                    genderName = request.genderName
                                  )
                                )
                            ),
                            requestContext
                          )
                        ) { user: User =>
                          complete(StatusCodes.OK -> PersonalityResponse(user))
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

final case class CreatePersonalityRequest(
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "fr") language: Language,
  @(ApiModelProperty @field)(dataType = "string") avatarUrl: Option[String],
  @(ApiModelProperty @field)(dataType = "string") description: Option[String],
  @(ApiModelProperty @field)(dataType = "string") gender: Option[Gender],
  @(ApiModelProperty @field)(dataType = "string") genderName: Option[String]
) {
  validate(
    mandatoryField("firstName", firstName),
    validateOptionalUserInput("firstName", firstName, None),
    validateOptionalUserInput("lastName", lastName, None),
    mandatoryField("email", email),
    validateEmail("email", email.toLowerCase),
    validateUserInput("email", email, None),
    mandatoryField("language", language),
    mandatoryField("country", country)
  )
}

object CreatePersonalityRequest {
  implicit val decoder: Decoder[CreatePersonalityRequest] = deriveDecoder[CreatePersonalityRequest]
}

final case class UpdatePersonalityRequest(
  email: Option[String],
  firstName: Option[String],
  lastName: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Option[Country],
  @(ApiModelProperty @field)(dataType = "string", example = "fr") language: Option[Language],
  @(ApiModelProperty @field)(dataType = "string") avatarUrl: Option[String],
  @(ApiModelProperty @field)(dataType = "string") description: Option[String],
  @(ApiModelProperty @field)(dataType = "string") gender: Option[Gender],
  @(ApiModelProperty @field)(dataType = "string") genderName: Option[String]
) {
  private val maxLanguageLength = 3
  private val maxCountryLength = 3

  validateOptional(
    email.map(email       => validateEmail(fieldName = "email", fieldValue = email.toLowerCase)),
    email.map(email       => validateUserInput("email", email, None)),
    firstName.map(value   => requireNonEmpty("firstName", value, Some("firstName should not be an empty string"))),
    firstName.map(value   => validateUserInput("firstName", value, None)),
    lastName.map(value    => validateUserInput("lastName", value, None)),
    country.map(country   => maxLength("country", maxCountryLength, country.value)),
    language.map(language => maxLength("language", maxLanguageLength, language.value))
  )
}

object UpdatePersonalityRequest {
  implicit val decoder: Decoder[UpdatePersonalityRequest] = deriveDecoder[UpdatePersonalityRequest]
}

case class PersonalityResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d22c8e70-f709-42ff-8a52-9398d159c753") id: UserId,
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "fr") language: Language,
  @(ApiModelProperty @field)(dataType = "string") avatarUrl: Option[String],
  @(ApiModelProperty @field)(dataType = "string") description: Option[String],
  @(ApiModelProperty @field)(dataType = "string") gender: Option[Gender],
  @(ApiModelProperty @field)(dataType = "string") genderName: Option[String]
) {
  validate(
    validateUserInput("email", email, None),
    validateOptionalUserInput("firstName", firstName, None),
    validateOptionalUserInput("lastName", lastName, None)
  )
}

object PersonalityResponse extends CirceFormatters {
  implicit val encoder: Encoder[PersonalityResponse] = deriveEncoder[PersonalityResponse]
  implicit val decoder: Decoder[PersonalityResponse] = deriveDecoder[PersonalityResponse]

  def apply(user: User): PersonalityResponse = PersonalityResponse(
    id = user.userId,
    email = user.email,
    firstName = user.firstName,
    lastName = user.lastName,
    country = user.country,
    language = user.language,
    avatarUrl = user.profile.flatMap(_.avatarUrl),
    description = user.profile.flatMap(_.description),
    gender = user.profile.flatMap(_.gender),
    genderName = user.profile.flatMap(_.genderName)
  )
}
