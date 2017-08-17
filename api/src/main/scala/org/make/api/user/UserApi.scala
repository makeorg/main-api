package org.make.api.user

import java.time.{LocalDate, ZonedDateTime}
import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.server._
import io.circe.generic.auto._
import io.swagger.annotations._
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.user.social.SocialServiceComponent
import org.make.core.HttpCodes
import org.make.core.Validation.{mandatoryField, validate, validateEmail, validateField}
import org.make.core.profile.Profile
import org.make.core.user.Role.RoleAdmin
import org.make.core.user.UserEvent.{ResendValidationEmailEvent, ResetPasswordEvent}
import org.make.core.user.{Role, User, UserId}

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
    with PersistentUserServiceComponent =>

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
        makeTrace("GetUser") {
          makeOAuth2 { userAuth: AuthInfo[User] =>
            authorize(userId == userAuth.user.userId) {
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
        makeTrace("GetMe") {
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
      makeTrace("SocialLogin") {
        decodeRequest {
          entity(as[SocialLoginRequest]) { request: SocialLoginRequest =>
            extractClientIP { clientIp =>
              val ip = clientIp.toOption.map(_.getHostAddress).getOrElse("unknown")
              onSuccess(
                socialService
                  .login(request.provider, request.token, Some(ip))
              ) { accessToken =>
                complete(StatusCodes.Created -> accessToken)
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
      makeTrace("RegisterUser") {
        decodeRequest {
          entity(as[RegisterUserRequest]) { request: RegisterUserRequest =>
            extractClientIP { clientIp =>
              onSuccess(
                userService
                  .register(
                    email = request.email,
                    firstName = request.firstName,
                    lastName = request.lastName,
                    password = Some(request.password),
                    lastIp = clientIp.toOption.map(_.getHostAddress),
                    dateOfBirth = request.dateOfBirth
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

  @ApiOperation(value = "Reset password", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @Path(value = "/reset-password")
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "body", paramType = "body", dataType = "org.make.api.user.ResetPasswordRequest")
    )
  )
  def resetPasswordRoute(implicit ctx: EC = ECGlobal): Route = {
    post {
      path("user" / "reset-password") {
        makeTrace("ResetPassword") {
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[User]] =>
            decodeRequest(entity(as[ResetPasswordRequest]) { request =>
              provideAsyncOrNotFound(persistentUserService.findUserIdByEmail(request.email)) { id =>
                eventBusService.publish(ResetPasswordEvent(userId = id, connectedUserId = userAuth.map(_.user.userId)))
                complete(StatusCodes.OK)
              }
            })
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
      makeTrace("ResendValidateEmail") {
        makeOAuth2 { userAuth =>
          authorize(userId == userAuth.user.userId || userAuth.user.roles.contains(RoleAdmin)) {
            eventBusService.publish(ResendValidationEmailEvent(userId = userId, connectedUserId = userAuth.user.userId))
            complete(StatusCodes.NoContent)
          }
        }
      }
    }
  }

  val userRoutes: Route = resetPasswordRoute ~ register ~ socialLogin ~ getUser ~ getMe

  val userId: PathMatcher1[UserId] =
    Segment.flatMap(id => Try(UserId(id)).toOption)

}

case class RegisterUserRequest(email: String,
                               password: String,
                               dateOfBirth: Option[LocalDate],
                               firstName: Option[String],
                               lastName: Option[String]) {

  validate(
    mandatoryField("dateOfBirth", dateOfBirth),
    mandatoryField("firstName", firstName),
    mandatoryField("lastName", lastName),
    mandatoryField("email", email),
    validateEmail("email", email),
    mandatoryField("password", password),
    validateField("password", Option(password).exists(_.length > 5), "Password must be at least 6 characters")
  )
}

case class SocialLoginRequest(provider: String, token: String)

final case class ResetPasswordRequest(email: String) {
  validate(mandatoryField("email", email), validateEmail("email", email))

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
