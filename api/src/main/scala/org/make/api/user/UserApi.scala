package org.make.api.user

import java.net.URLEncoder
import java.time.{LocalDate, ZonedDateTime}
import javax.ws.rs.Path

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Set-Cookie`, HttpCookie}
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, ObjectEncoder}
import io.circe.generic.semiauto._
import io.swagger.annotations._
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.user.social.SocialServiceComponent
import org.make.api.userhistory.UserEvent.{ResendValidationEmailEvent, ResetPasswordEvent, UserValidatedAccountEvent}
import org.make.core.Validation.{mandatoryField, validate, validateEmail, validateField}
import org.make.core.auth.UserRights
import org.make.core.profile.Profile
import org.make.core.user.Role.RoleAdmin
import org.make.core.user.{Role, User, UserId}
import org.make.core.{CirceFormatters, DateHelper, HttpCodes}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "User")
@Path(value = "/user")
trait UserApi extends MakeAuthenticationDirectives with StrictLogging {
  this: UserServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with SocialServiceComponent
    with EventBusServiceComponent
    with PersistentUserServiceComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent
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
        makeTrace("GetUser") { _ =>
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
        makeTrace("GetMe") { _ =>
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
      makeTrace("SocialLogin") { requestContext =>
        decodeRequest {
          entity(as[SocialLoginRequest]) { request: SocialLoginRequest =>
            extractClientIP { clientIp =>
              val ip = clientIp.toOption.map(_.getHostAddress).getOrElse("unknown")
              onSuccess(
                socialService
                  .login(request.provider, request.token, Some(ip), requestContext)
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
                        maxAge = Some(makeSettings.SessionCookie.lifetime.toMillis),
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
      makeTrace("RegisterUser") { requestContext =>
        decodeRequest {
          entity(as[RegisterUserRequest]) { request: RegisterUserRequest =>
            extractClientIP { clientIp =>
              onSuccess(
                userService
                  .register(
                    UserRegisterData(
                      email = request.email,
                      firstName = request.firstName,
                      lastName = request.lastName,
                      password = Some(request.password),
                      lastIp = clientIp.toOption.map(_.getHostAddress),
                      dateOfBirth = request.dateOfBirth,
                      profession = request.profession,
                      postalCode = request.postalCode
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
  }

  @ApiOperation(value = "verifiy user email", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "")))
  @Path(value = "/:userId/validate/:verificationToken")
  @ApiImplicitParams(value = Array())
  def validateAccountRoute: Route = {
    post {
      path("user" / userId / "validate" / Segment) { (userId: UserId, verificationToken: String) =>
        makeTrace("UserValidation") { requestContext =>
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
                    eventBusService.publish(UserValidatedAccountEvent(userId = userId, requestContext = requestContext))
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
        makeTrace("ResetPasswordRequest") { requestContext =>
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
            decodeRequest(entity(as[ResetPasswordRequest]) { request =>
              provideAsyncOrNotFound(persistentUserService.findUserIdByEmail(request.email)) { userId =>
                userService.requestPasswordReset(userId)
                eventBusService.publish(
                  ResetPasswordEvent(
                    userId = userId,
                    connectedUserId = userAuth.map(_.user.userId),
                    requestContext = requestContext
                  )
                )
                complete(StatusCodes.NoContent)
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
  @ApiImplicitParams(value = Array())
  def resetPasswordCheckRoute: Route = {
    post {
      path("user" / "reset-password" / "check-validity" / userId / Segment) { (userId: UserId, resetToken: String) =>
        makeTrace("ResetPasswordCheck") { _ =>
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
    value = Array(new ApiImplicitParam(name = "body", paramType = "body", dataType = "org.make.api.user.ResetPassword"))
  )
  def resetPasswordRoute: Route = {
    post {
      path("user" / "reset-password" / "change-password" / userId) { userId =>
        makeTrace("ResetPassword") { _ =>
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
  @Path(value = "/:userId/resend-validation-email")
  @ApiImplicitParams(value = Array())
  def resendValidationEmail: Route = post {
    path("user" / userId / "resend-validation-email") { userId =>
      makeTrace("ResendValidateEmail") { requestContext =>
        makeOAuth2 { userAuth =>
          authorize(userId == userAuth.user.userId || userAuth.user.roles.contains(RoleAdmin)) {
            eventBusService.publish(
              ResendValidationEmailEvent(
                userId = userId,
                connectedUserId = userAuth.user.userId,
                requestContext = requestContext
              )
            )
            complete(StatusCodes.NoContent)
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
      makeTrace("SubscribeToNewsletter") { _ =>
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
              Http(actorSystem).singleRequest(httpRequest)(ActorMaterializer()(actorSystem))

            onSuccess(futureHttpResponse) { httpResponse =>
              httpResponse.status match {
                case StatusCodes.OK => complete(StatusCodes.NoContent)
                case status         => complete(StatusCodes.ServiceUnavailable)
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
    resetPasswordRequestRoute ~
    resetPasswordCheckRoute ~
    resetPasswordRoute ~
    subscribeToNewsLetter ~
    validateAccountRoute

  val userId: PathMatcher1[UserId] =
    Segment.flatMap(id => Try(UserId(id)).toOption)
}

case class RegisterUserRequest(email: String,
                               password: String,
                               dateOfBirth: Option[LocalDate],
                               firstName: Option[String],
                               lastName: Option[String],
                               profession: Option[String],
                               postalCode: Option[String]) {

  validate(
    mandatoryField("firstName", firstName),
    mandatoryField("email", email),
    validateEmail("email", email),
    mandatoryField("password", password),
    validateField("password", Option(password).exists(_.length >= 8), "Password must be at least 8 characters")
  )
}

object RegisterUserRequest extends CirceFormatters {
  implicit val decoder: Decoder[RegisterUserRequest] = deriveDecoder[RegisterUserRequest]
}

case class SocialLoginRequest(provider: String, token: String)

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

case class UserResponse(userId: UserId,
                        email: String,
                        firstName: Option[String],
                        lastName: Option[String],
                        enabled: Boolean,
                        verified: Boolean,
                        lastConnection: ZonedDateTime,
                        roles: Seq[Role],
                        profile: Option[Profile])

object UserResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[UserResponse] = deriveEncoder[UserResponse]
  implicit val decoder: Decoder[UserResponse] = deriveDecoder[UserResponse]

  def apply(user: User): UserResponse = UserResponse(
    userId = user.userId,
    email = user.email,
    firstName = user.firstName,
    lastName = user.lastName,
    enabled = user.enabled,
    verified = user.verified,
    lastConnection = user.lastConnection,
    roles = user.roles,
    profile = user.profile
  )
}
