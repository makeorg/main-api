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

package org.make.api.user

import java.net.URLEncoder
import java.time.{LocalDate, ZonedDateTime}

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Set-Cookie`, HttpCookie}
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal.{ProposalServiceComponent, ProposalsResultResponse, ProposalsResultSeededResponse}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{
  EventBusServiceComponent,
  IdGeneratorComponent,
  MakeAuthenticationDirectives,
  ReadJournalComponent
}
import org.make.api.user.UserUpdateEvent.{UserUpdateValidatedEvent, UserUpdatedOptInNewsletterEvent}
import org.make.api.user.social.SocialServiceComponent
import org.make.api.userhistory.UserEvent.{ResendValidationEmailEvent, ResetPasswordEvent, UserValidatedAccountEvent}
import org.make.api.userhistory.UserHistoryCoordinatorServiceComponent
import org.make.core.Validation._
import org.make.core.auth.UserRights
import org.make.core.profile.{Gender, Profile}
import org.make.core.proposal.{SearchFilters, SearchQuery, UserSearchFilter}
import org.make.core.reference.{Country, Language}
import org.make.core.user.Role.RoleAdmin
import org.make.core.user.{MailingErrorLog, Role, User, UserId}
import org.make.core.{CirceFormatters, DateHelper, HttpCodes}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

@Api(value = "User")
@Path(value = "/user")
trait UserApi extends MakeAuthenticationDirectives with StrictLogging {
  this: UserServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with SocialServiceComponent
    with ProposalServiceComponent
    with EventBusServiceComponent
    with PersistentUserServiceComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with ReadJournalComponent
    with ActorSystemComponent =>

  @Path(value = "/{userId}")
  @ApiOperation(
    value = "get-user",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserResponse])))
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "userId", paramType = "path", dataType = "string")))
  def getUser: Route = {
    get {
      path("user" / userId) { userId =>
        makeOperation("GetUser") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            authorize(userId == userAuth.user.userId || userAuth.user.roles.contains(RoleAdmin)) {
              onSuccess(userService.getUser(userId)) {
                case Some(user) => complete(UserResponse(user))
                case None       => complete(NotFound)
              }
            }
          }
        }
      }
    }
  }

  @Path(value = "/me")
  @ApiOperation(
    value = "get-me",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserResponse])))
  def getMe: Route = {
    get {
      path("user" / "me") {
        makeOperation("GetMe") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            provideAsyncOrNotFound(userService.getUser(userAuth.user.userId)) { user =>
              complete(UserResponse(user))
            }
          }
        }
      }
    }
  }

  @Path(value = "/login/social")
  @ApiOperation(value = "Login Social", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value =
      Array(new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.SocialLoginRequest"))
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[String])))
  def socialLogin: Route = post {
    path("user" / "login" / "social") {
      makeOperation("SocialLogin") { requestContext =>
        decodeRequest {
          entity(as[SocialLoginRequest]) { request: SocialLoginRequest =>
            extractClientIP { clientIp =>
              val ip = clientIp.toOption.map(_.getHostAddress).getOrElse("unknown")
              onSuccess(
                socialService
                  .login(
                    request.provider,
                    request.token,
                    request.country.orElse(requestContext.country).getOrElse(Country("FR")),
                    request.language.orElse(requestContext.language).getOrElse(Language("fr")),
                    Some(ip),
                    requestContext
                  )
                  .flatMap { social =>
                    sessionHistoryCoordinatorService
                      .convertSession(requestContext.sessionId, social.userId)
                      .map(_ => social)
                  }
              ) { social =>
                mapResponseHeaders(
                  _ ++ Seq(
                    `Set-Cookie`(
                      HttpCookie(
                        name = makeSettings.SessionCookie.name,
                        value = social.token.access_token,
                        secure = makeSettings.SessionCookie.isSecure,
                        httpOnly = true,
                        maxAge = Some(makeSettings.SessionCookie.lifetime.toSeconds),
                        path = Some("/")
                      )
                    )
                  )
                ) {
                  complete(StatusCodes.Created -> social.token)
                }
              }
            }
          }
        }
      }
    }
  }

  @Path(value = "/{userId}/votes")
  @ApiOperation(value = "voted-proposals", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "userId", paramType = "path", dataType = "string")))
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsResultResponse]))
  )
  def getVotedProposalsByUser: Route = get {
    path("user" / userId / "votes") { userId: UserId =>
      makeOperation("UserVotedProposals") { requestContext =>
        makeOAuth2 { userAuth: AuthInfo[UserRights] =>
          if (userAuth.user.userId != userId) {
            complete(StatusCodes.Forbidden)
          } else {
            provideAsync(proposalService.searchProposalsVotedByUser(userId = userId, requestContext = requestContext)) {
              proposalsSearchResult =>
                complete(proposalsSearchResult)
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "register-user", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.RegisterUserRequest"),
      new ApiImplicitParam(
        value = "X-Forwarded-For",
        paramType = "header",
        dataType = "string",
        name = "X-Forwarded-For"
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserResponse])))
  def register: Route = post {
    path("user") {
      makeOperation("RegisterUser") { requestContext =>
        decodeRequest {
          entity(as[RegisterUserRequest]) { request: RegisterUserRequest =>
            onSuccess(
              userService
                .register(
                  UserRegisterData(
                    email = request.email,
                    firstName = request.firstName,
                    lastName = request.lastName,
                    password = Some(request.password),
                    lastIp = requestContext.ipAddress,
                    dateOfBirth = request.dateOfBirth,
                    profession = request.profession,
                    postalCode = request.postalCode,
                    country = request.country.orElse(requestContext.country).getOrElse(Country("FR")),
                    language = request.language.orElse(requestContext.language).getOrElse(Language("fr"))
                  ),
                  requestContext
                )
                .flatMap { user =>
                  sessionHistoryCoordinatorService
                    .convertSession(requestContext.sessionId, user.userId)
                    .map(_ => user)
                }
            ) { result =>
              complete(StatusCodes.Created -> UserResponse(result))
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "verifiy user email", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "")))
  @Path(value = "/{userId}/validate/:verificationToken")
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "userId", paramType = "path", dataType = "string")))
  def validateAccountRoute: Route = {
    post {
      path("user" / userId / "validate" / Segment) { (userId: UserId, verificationToken: String) =>
        makeOperation("UserValidation") { requestContext =>
          provideAsyncOrNotFound(
            persistentUserService
              .findUserByUserIdAndVerificationToken(userId, verificationToken)
          ) { user =>
            if (user.verificationTokenExpiresAt.forall(_.isBefore(DateHelper.now()))) {
              complete(StatusCodes.BadRequest)
            } else {
              onSuccess(
                userService
                  .validateEmail(verificationToken = verificationToken)
                  .map { result =>
                    eventBusService.publish(
                      UserValidatedAccountEvent(
                        userId = userId,
                        country = user.country,
                        language = user.language,
                        requestContext = requestContext,
                        isSocialLogin = false
                      )
                    )
                    eventBusService.publish(UserUpdateValidatedEvent(userId = Some(user.userId)))

                    result
                  }
              ) { _ =>
                complete(StatusCodes.NoContent)
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "Reset password request token", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "")))
  @Path(value = "/reset-password/request-reset")
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "body", paramType = "body", dataType = "org.make.api.user.ResetPasswordRequest")
    )
  )
  def resetPasswordRequestRoute: Route = {
    post {
      path("user" / "reset-password" / "request-reset") {
        makeOperation("ResetPasswordRequest") { requestContext =>
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
            decodeRequest(entity(as[ResetPasswordRequest]) { request =>
              provideAsyncOrNotFound(persistentUserService.findByEmail(request.email)) { user =>
                onSuccess(userService.requestPasswordReset(user.userId).map { result =>
                  eventBusService.publish(
                    ResetPasswordEvent(
                      userId = user.userId,
                      connectedUserId = userAuth.map(_.user.userId),
                      country = user.country,
                      language = user.language,
                      requestContext = requestContext
                    )
                  )
                  result
                }) { _ =>
                  complete(StatusCodes.NoContent)
                }
              }
            })
          }
        }
      }
    }
  }

  @ApiOperation(value = "Reset password token check", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "")))
  @Path(value = "/reset-password/check-validity/:userId/:resetToken")
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "userId", paramType = "path", dataType = "string")))
  def resetPasswordCheckRoute: Route = {
    post {
      path("user" / "reset-password" / "check-validity" / userId / Segment) { (userId: UserId, resetToken: String) =>
        makeOperation("ResetPasswordCheck") { _ =>
          provideAsyncOrNotFound(persistentUserService.findUserByUserIdAndResetToken(userId, resetToken)) { user =>
            if (user.resetTokenExpiresAt.exists(_.isAfter(DateHelper.now()))) {
              complete(StatusCodes.NoContent)
            } else {
              complete(StatusCodes.BadRequest)
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "Reset password", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "")))
  @Path(value = "/reset-password/change-password/:userId")
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "body", paramType = "body", dataType = "org.make.api.user.ResetPassword"),
      new ApiImplicitParam(name = "userId", paramType = "path", dataType = "string")
    )
  )
  def resetPasswordRoute: Route = {
    post {
      path("user" / "reset-password" / "change-password" / userId) { userId =>
        makeOperation("ResetPassword") { _ =>
          decodeRequest {
            entity(as[ResetPassword]) { request: ResetPassword =>
              provideAsyncOrNotFound(persistentUserService.findUserByUserIdAndResetToken(userId, request.resetToken)) {
                user =>
                  if (user.resetTokenExpiresAt.forall(_.isBefore(DateHelper.now()))) {
                    complete(StatusCodes.BadRequest)
                  } else {
                    onSuccess(
                      userService
                        .updatePassword(userId = userId, resetToken = request.resetToken, password = request.password)
                    ) { _ =>
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

  @ApiOperation(value = "Resend validation email", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No content")))
  @Path(value = "/{userId}/resend-validation-email")
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "userId", paramType = "path", dataType = "string")))
  def resendValidationEmail: Route = post {
    path("user" / userId / "resend-validation-email") { userId =>
      makeOperation("ResendValidateEmail") { requestContext =>
        makeOAuth2 { userAuth =>
          authorize(userId == userAuth.user.userId || userAuth.user.roles.contains(RoleAdmin)) {
            provideAsyncOrNotFound(persistentUserService.get(userId)) { user =>
              eventBusService.publish(
                ResendValidationEmailEvent(
                  userId = userId,
                  connectedUserId = userAuth.user.userId,
                  country = user.country,
                  language = user.language,
                  requestContext = requestContext
                )
              )
              complete(StatusCodes.NoContent)
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "subscribe-newsletter", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.SubscribeToNewsLetter")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Unit])))
  @Path(value = "/newsletter")
  def subscribeToNewsLetter: Route = post {
    path("user" / "newsletter") {
      makeOperation("SubscribeToNewsletter") { _ =>
        decodeRequest {
          entity(as[SubscribeToNewsLetter]) { request: SubscribeToNewsLetter =>
            // Keep as info in case sending form would fail
            logger.info(s"inscribing ${request.email} to newsletter")

            val encodedEmailFieldName = URLEncoder.encode("fields[Email]", "UTF-8")
            val encodedEmail = URLEncoder.encode(request.email, "UTF-8")
            val encodedName = URLEncoder.encode("VFF Séquence Test", "UTF-8")

            val httpRequest = HttpRequest(
              method = HttpMethods.POST,
              uri = makeSettings.newsletterUrl,
              entity = HttpEntity(
                ContentType(MediaTypes.`application/x-www-form-urlencoded`, HttpCharsets.`UTF-8`),
                s"$encodedEmailFieldName=$encodedEmail&name=$encodedName"
              )
            )
            logger.debug(s"Subscribe newsletter request: ${httpRequest.toString}")
            val futureHttpResponse: Future[HttpResponse] =
              Http(actorSystem).singleRequest(httpRequest)

            onSuccess(futureHttpResponse) { httpResponse =>
              httpResponse.status match {
                case StatusCodes.OK => complete(StatusCodes.NoContent)
                case _              => complete(StatusCodes.ServiceUnavailable)
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "reload-history",
    httpMethod = "POST",
    code = HttpCodes.NoContent,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "userId", paramType = "path", dataType = "string")))
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "Ok", response = classOf[Unit])))
  @Path(value = "/{userId}/reload-history")
  def rebuildUserHistory: Route = post {
    path("user" / userId / "reload-history") { userId =>
      makeOperation("ReloadUserHistory") { _ =>
        makeOAuth2 { userAuth =>
          requireAdminRole(userAuth.user) {
            userHistoryCoordinatorService.reloadHistory(userId)
            complete(StatusCodes.NoContent)
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "reload-all-users-history",
    httpMethod = "POST",
    code = HttpCodes.NoContent,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "Ok", response = classOf[Unit])))
  @Path(value = "/reload-history")
  def rebuildAllUsersHistory: Route = post {
    path("user" / "reload-history") {
      makeOperation("ReloadAllUsersHistory") { _ =>
        makeOAuth2 { userAuth =>
          requireAdminRole(userAuth.user) {
            implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
            userJournal
              .currentPersistenceIds()
              .runForeach(id => userHistoryCoordinatorService.reloadHistory(UserId(id)))

            complete(StatusCodes.NoContent)
          }
        }
      }
    }
  }

  @Path(value = "/{userId}/proposals")
  @ApiOperation(value = "user-proposals", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "userId", paramType = "path", dataType = "string")))
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsResultSeededResponse]))
  )
  def getProposalsByUser: Route = get {
    path("user" / userId / "proposals") { userId: UserId =>
      makeOperation("UserProposals") { requestContext =>
        makeOAuth2 { userAuth: AuthInfo[UserRights] =>
          val connectedUserId: UserId = userAuth.user.userId
          if (connectedUserId != userId) {
            complete(StatusCodes.Forbidden)
          } else {
            provideAsync(
              proposalService.searchForUser(
                userId = Some(userId),
                query = SearchQuery(filters = Some(SearchFilters(user = Some(UserSearchFilter(userId = userId))))),
                requestContext = requestContext,
              )
            ) { proposalsSearchResult =>
              complete(proposalsSearchResult)
            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "user-update",
    httpMethod = "PATCH",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserResponse])))
  @ApiImplicitParams(
    value =
      Array(new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.UpdateUserRequest"))
  )
  @Path(value = "/")
  def patchUser: Route =
    patch {
      path("user") {
        makeOperation("PatchUser") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            decodeRequest {
              entity(as[UpdateUserRequest]) { request: UpdateUserRequest =>
                provideAsyncOrNotFound(userService.getUser(userAuth.user.userId)) { user =>
                  val optInNewsletterHasChanged: Boolean = (request.optInNewsletter, user.profile) match {
                    case (Some(value), Some(profileValue)) => value != profileValue.optInNewsletter
                    case (Some(_), None)                   => true
                    case _                                 => false
                  }

                  val profile: Option[Profile] = user.profile
                  val updatedProfile: Option[Profile] = profile.map(
                    _.copy(
                      dateOfBirth = request.dateOfBirth.orElse(user.profile.flatMap(_.dateOfBirth)),
                      profession = request.profession.orElse(user.profile.flatMap(_.profession)),
                      postalCode = request.postalCode.orElse(user.profile.flatMap(_.postalCode)),
                      phoneNumber = request.phoneNumber.orElse(user.profile.flatMap(_.phoneNumber)),
                      optInNewsletter = request.optInNewsletter.getOrElse(user.profile.exists(_.optInNewsletter)),
                      gender = Gender
                        .matchGender(request.gender.getOrElse(""))
                        .orElse(Gender.matchGender(user.profile.flatMap(_.gender).toString)),
                      genderName = request.genderName.orElse(user.profile.flatMap(_.genderName))
                    )
                  )

                  onSuccess(
                    userService.update(
                      user.copy(
                        firstName = request.firstName.orElse(user.firstName),
                        lastName = request.lastName.orElse(user.lastName),
                        country = request.country.getOrElse(user.country),
                        language = request.language.getOrElse(user.language),
                        profile = updatedProfile
                      ),
                      requestContext
                    )
                  ) { user: User =>
                    if (optInNewsletterHasChanged) {
                      eventBusService.publish(
                        UserUpdatedOptInNewsletterEvent(
                          userId = Some(user.userId),
                          eventDate = DateHelper.now(),
                          optInNewsletter = user.profile.exists(_.optInNewsletter)
                        )
                      )
                    }
                    complete(StatusCodes.NoContent -> UserResponse(user))
                  }
                }
              }
            }
          }
        }
      }
    }

  val userRoutes: Route = getMe ~
    getUser ~
    register ~
    socialLogin ~
    rebuildAllUsersHistory ~
    rebuildUserHistory ~
    resetPasswordRequestRoute ~
    resetPasswordCheckRoute ~
    resetPasswordRoute ~
    subscribeToNewsLetter ~
    validateAccountRoute ~
    getVotedProposalsByUser ~
    getProposalsByUser ~
    patchUser

  val userId: PathMatcher1[UserId] =
    Segment.flatMap(id => Try(UserId(id)).toOption)
}

case class RegisterUserRequest(email: String,
                               password: String,
                               dateOfBirth: Option[LocalDate],
                               firstName: Option[String],
                               lastName: Option[String],
                               profession: Option[String],
                               postalCode: Option[String],
                               country: Option[Country],
                               language: Option[Language]) {

  validate(
    mandatoryField("firstName", firstName),
    mandatoryField("email", email),
    validateEmail("email", email),
    mandatoryField("password", password),
    validateField("password", Option(password).exists(_.length >= 8), "Password must be at least 8 characters"),
    validateField("postalCode", postalCode.forall(_.length <= 10), "postal code cannot be longer than 10 characters"),
    mandatoryField("language", language),
    mandatoryField("country", country)
  )
}

object RegisterUserRequest extends CirceFormatters {
  implicit val decoder: Decoder[RegisterUserRequest] = deriveDecoder[RegisterUserRequest]
}

case class UpdateUserRequest(dateOfBirth: Option[LocalDate],
                             firstName: Option[String],
                             lastName: Option[String],
                             profession: Option[String],
                             postalCode: Option[String],
                             phoneNumber: Option[String],
                             optInNewsletter: Option[Boolean],
                             gender: Option[String],
                             genderName: Option[String],
                             country: Option[Country],
                             language: Option[Language]) {
  private val maxLanguageLength = 3
  private val maxCountryLength = 3

  if (firstName.nonEmpty) {
    validate(mandatoryField("firstName", firstName))
  }

  if (postalCode.nonEmpty) {
    validateField("postalCode", postalCode.forall(_.length <= 10), "postal code cannot be longer than 10 characters")
  }

  if (language.nonEmpty) {
    validate(maxLength("language", maxLanguageLength, language.get.value))
  }

  if (country.nonEmpty) {
    validate(maxLength("country", maxCountryLength, country.get.value))
  }

  if (gender.nonEmpty) {
    validate(
      validateField(
        "gender",
        Gender.matchGender(gender.get).isDefined,
        s"gender should be on of this specified values: ${Gender.genders.keys.mkString(",")}"
      )
    )
  }

}

object UpdateUserRequest extends CirceFormatters {
  implicit val decoder: Decoder[UpdateUserRequest] = deriveDecoder[UpdateUserRequest]
}

case class SocialLoginRequest(provider: String, token: String, country: Option[Country], language: Option[Language]) {
  validate(mandatoryField("language", language), mandatoryField("country", country))
}

object SocialLoginRequest {
  implicit val decoder: Decoder[SocialLoginRequest] = deriveDecoder[SocialLoginRequest]
}

final case class ResetPasswordRequest(email: String) {
  validate(mandatoryField("email", email), validateEmail("email", email))
}

object ResetPasswordRequest {
  implicit val decoder: Decoder[ResetPasswordRequest] = deriveDecoder[ResetPasswordRequest]
}

final case class ResetPassword(resetToken: String, password: String) {
  validate(mandatoryField("resetToken", resetToken))
  validate(mandatoryField("password", password))
}

object ResetPassword {
  implicit val decoder: Decoder[ResetPassword] = deriveDecoder[ResetPassword]
}

final case class SubscribeToNewsLetter(email: String) {
  validate(mandatoryField("email", email), validateEmail("email", email))
}

object SubscribeToNewsLetter {
  implicit val decoder: Decoder[SubscribeToNewsLetter] = deriveDecoder[SubscribeToNewsLetter]
}

case class MailingErrorLogResponse(error: String, date: ZonedDateTime)
object MailingErrorLogResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[MailingErrorLogResponse] = deriveEncoder[MailingErrorLogResponse]
  implicit val decoder: Decoder[MailingErrorLogResponse] = deriveDecoder[MailingErrorLogResponse]

  def apply(mailingErrorLog: MailingErrorLog): MailingErrorLogResponse =
    MailingErrorLogResponse(error = mailingErrorLog.error, date = mailingErrorLog.date)
}

case class UserResponse(userId: UserId,
                        email: String,
                        firstName: Option[String],
                        lastName: Option[String],
                        organisationName: Option[String],
                        enabled: Boolean,
                        emailVerified: Boolean,
                        isOrganisation: Boolean,
                        lastConnection: ZonedDateTime,
                        roles: Seq[Role],
                        profile: Option[Profile],
                        country: Country,
                        language: Language,
                        isHardBounce: Boolean,
                        lastMailingError: Option[MailingErrorLogResponse],
                        hasPassword: Boolean)

object UserResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[UserResponse] = deriveEncoder[UserResponse]
  implicit val decoder: Decoder[UserResponse] = deriveDecoder[UserResponse]

  def apply(user: User): UserResponse = UserResponse(
    userId = user.userId,
    email = user.email,
    firstName = user.firstName,
    lastName = user.lastName,
    organisationName = user.organisationName,
    enabled = user.enabled,
    emailVerified = user.emailVerified,
    isOrganisation = user.isOrganisation,
    lastConnection = user.lastConnection,
    roles = user.roles,
    profile = user.profile,
    country = user.country,
    language = user.language,
    isHardBounce = user.isHardBounce,
    lastMailingError = user.lastMailingError.map(MailingErrorLogResponse(_)),
    hasPassword = user.hashedPassword.isDefined
  )
}
