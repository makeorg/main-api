package org.make.api.user

import java.time.{LocalDate, ZonedDateTime}
import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.model.headers.{`Set-Cookie`, HttpCookie}
import akka.http.scaladsl.server._
import io.circe.generic.auto._
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.user.social.SocialServiceComponent
import org.make.core.Validation.{mandatoryField, validate, validateEmail, validateField}
import org.make.core.profile.Profile
import org.make.core.user.Role.RoleAdmin
import org.make.core.user.UserEvent.{ResendValidationEmailEvent, ResetPasswordEvent}
import org.make.core.user.{Role, User, UserId}
import org.make.core.{DateHelper, HttpCodes}

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "User")
@Path(value = "/user")
trait UserApi extends MakeAuthenticationDirectives {
  this: UserServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with SocialServiceComponent
    with EventBusServiceComponent
    with PersistentUserServiceComponent
    with MakeSettingsComponent =>

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
          makeOAuth2 { userAuth: AuthInfo[User] =>
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
          makeOAuth2 { userAuth: AuthInfo[User] =>
            complete(UserResponse(userAuth.user))
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
      makeTrace("SocialLogin") { _ =>
        decodeRequest {
          entity(as[SocialLoginRequest]) { request: SocialLoginRequest =>
            extractClientIP { clientIp =>
              val ip = clientIp.toOption.map(_.getHostAddress).getOrElse("unknown")
              onSuccess(
                socialService
                  .login(request.provider, request.token, Some(ip))
              ) { accessToken =>
                mapResponseHeaders(
                  _ ++ Seq(
                    `Set-Cookie`(
                      HttpCookie(
                        name = makeSettings.SessionCookie.name,
                        value = accessToken.access_token,
                        secure = makeSettings.SessionCookie.isSecure,
                        httpOnly = true,
                        maxAge = Some(makeSettings.SessionCookie.lifetime.toMillis),
                        path = Some("/")
                      )
                    )
                  )
                ) {
                  complete(StatusCodes.Created -> accessToken)
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
  def validateAccountRoute(implicit ctx: EC = ECGlobal): Route = {
    post {
      path("user" / userId / "validate" / Segment) { (userId: UserId, verificationToken: String) =>
        makeTrace("UserValidation") { _ =>
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
  def resetPasswordRequestRoute(implicit ctx: EC = ECGlobal): Route = {
    post {
      path("user" / "reset-password" / "request-reset") {
        makeTrace("ResetPasswordRequest") { requestContext =>
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[User]] =>
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
  def resetPasswordCheckRoute(implicit ctx: EC = ECGlobal): Route = {
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
  def resetPasswordRoute(implicit ctx: EC = ECGlobal): Route = {
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
  @Path(value = "/user/:userId/resend-validation-email")
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

  val userRoutes: Route = getMe ~
    getUser ~
    register ~
    socialLogin ~
    resetPasswordRequestRoute ~
    resetPasswordCheckRoute ~
    resetPasswordRoute ~
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

case class SocialLoginRequest(provider: String, token: String)

final case class ResetPasswordRequest(email: String) {
  validate(mandatoryField("email", email), validateEmail("email", email))
}

final case class ResetPassword(resetToken: String, password: String) {
  validate(mandatoryField("resetToken", resetToken))
  validate(mandatoryField("password", password))
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

object UserResponse {
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
