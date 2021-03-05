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

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Url
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.circe.refined._
import io.swagger.annotations.{ApiImplicitParam, _}

import javax.ws.rs.Path
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.{`X-Total-Count`, MakeAuthenticationDirectives}
import org.make.api.user.{PersonalityRegisterData, UserServiceComponent}
import org.make.core.Validation._
import org.make.core._
import org.make.core.auth.UserRights
import org.make.core.profile.{Gender, Profile}
import org.make.core.reference.Country
import org.make.core.user.{User, UserId, UserType}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field
import org.make.core.technical.Pagination._

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
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "integer"),
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
    value =
      Array(new ApiResponse(code = HttpCodes.Created, message = "Created", response = classOf[PersonalityResponse]))
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
  this: MakeDirectivesDependencies with UserServiceComponent =>

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
          parameters(
            "_start".as[Start].?,
            "_end".as[End].?,
            "_sort".?,
            "_order".as[Order].?,
            "email".?,
            "firstName".?,
            "lastName".?
          ) {
            (
              start: Option[Start],
              end: Option[End],
              sort: Option[String],
              order: Option[Order],
              email: Option[String],
              firstName: Option[String],
              lastName: Option[String]
            ) =>
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
                        start.orZero,
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
                        PersonalityRegisterData(
                          email = request.email,
                          firstName = request.firstName,
                          lastName = request.lastName,
                          country = request.country,
                          gender = request.gender,
                          genderName = request.genderName,
                          description = request.description,
                          avatarUrl = request.avatarUrl,
                          website = request.website.map(_.value),
                          politicalParty = request.politicalParty
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
                    provideAsyncOrNotFound(userService.getUser(userId)) { personality =>
                      val lowerCasedEmail: String = request.email.getOrElse(personality.email).toLowerCase()
                      provideAsync(userService.getUserByEmail(lowerCasedEmail)) { maybeUser =>
                        maybeUser.foreach { userToCheck =>
                          Validation.validate(
                            Validation.validateField(
                              field = "email",
                              "already_registered",
                              condition = userToCheck.userId.value == personality.userId.value,
                              message = s"Email $lowerCasedEmail already exists"
                            )
                          )
                        }
                        onSuccess(
                          userService.updatePersonality(
                            personality = personality.copy(
                              email = lowerCasedEmail,
                              firstName = request.firstName.orElse(personality.firstName),
                              lastName = request.lastName.orElse(personality.lastName),
                              country = request.country.getOrElse(personality.country),
                              profile = personality.profile
                                .map(
                                  _.copy(
                                    avatarUrl = request.avatarUrl.orElse(personality.profile.flatMap(_.avatarUrl)),
                                    description = request.description.orElse(personality.profile.flatMap(_.description)),
                                    gender = request.gender.orElse(personality.profile.flatMap(_.gender)),
                                    genderName = request.genderName.orElse(personality.profile.flatMap(_.genderName)),
                                    politicalParty =
                                      request.politicalParty.orElse(personality.profile.flatMap(_.politicalParty)),
                                    website =
                                      request.website.map(_.value).orElse(personality.profile.flatMap(_.website))
                                  )
                                )
                                .orElse(
                                  Profile.parseProfile(
                                    avatarUrl = request.avatarUrl,
                                    description = request.description,
                                    gender = request.gender,
                                    genderName = request.genderName,
                                    politicalParty = request.politicalParty,
                                    website = request.website.map(_.value)
                                  )
                                )
                            ),
                            moderatorId = Some(userAuth.user.userId),
                            oldEmail = personality.email.toLowerCase,
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
  @(ApiModelProperty @field)(dataType = "string", example = "yopmail+test@make.org") email: String,
  firstName: Option[String],
  lastName: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/avatar.png") avatarUrl: Option[String],
  description: Option[String],
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "F,M,O") gender: Option[Gender],
  genderName: Option[String],
  politicalParty: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/website")
  website: Option[String Refined Url]
) {
  validate(
    mandatoryField("firstName", firstName),
    validateOptionalUserInput("firstName", firstName, None),
    validateOptionalUserInput("lastName", lastName, None),
    mandatoryField("email", email),
    validateEmail("email", email.toLowerCase),
    validateUserInput("email", email, None),
    mandatoryField("country", country)
  )
}

object CreatePersonalityRequest {
  implicit val decoder: Decoder[CreatePersonalityRequest] = deriveDecoder[CreatePersonalityRequest]
}

final case class UpdatePersonalityRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "yopmail+test@make.org") email: Option[String],
  firstName: Option[String],
  lastName: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Option[Country],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/avatar.png") avatarUrl: Option[String],
  description: Option[String],
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "F,M,O") gender: Option[Gender],
  genderName: Option[String],
  politicalParty: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/website")
  website: Option[String Refined Url]
) {
  private val maxCountryLength = 3

  validateOptional(
    email.map(email     => validateEmail(fieldName = "email", fieldValue = email.toLowerCase)),
    email.map(email     => validateUserInput("email", email, None)),
    firstName.map(value => requireNonEmpty("firstName", value, Some("firstName should not be an empty string"))),
    firstName.map(value => validateUserInput("firstName", value, None)),
    lastName.map(value  => validateUserInput("lastName", value, None)),
    country.map(country => maxLength("country", maxCountryLength, country.value))
  )
}

object UpdatePersonalityRequest {
  implicit val decoder: Decoder[UpdatePersonalityRequest] = deriveDecoder[UpdatePersonalityRequest]
}

final case class PersonalityResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d22c8e70-f709-42ff-8a52-9398d159c753") id: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "yopmail+test@make.org") email: String,
  firstName: Option[String],
  lastName: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/avatar.png") avatarUrl: Option[String],
  description: Option[String],
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "F,M,O") gender: Option[Gender],
  genderName: Option[String],
  politicalParty: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/website")
  website: Option[String]
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
    avatarUrl = user.profile.flatMap(_.avatarUrl),
    description = user.profile.flatMap(_.description),
    gender = user.profile.flatMap(_.gender),
    genderName = user.profile.flatMap(_.genderName),
    politicalParty = user.profile.flatMap(_.politicalParty),
    website = user.profile.flatMap(_.website)
  )
}
