package org.make.api.user

import java.time.LocalDate
import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.StatusCodes.{Forbidden, NotFound}
import akka.http.scaladsl.server._
import io.circe.generic.auto._
import io.swagger.annotations._
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.core.HttpCodes
import org.make.core.Validation.{mandatoryField, validate, validateEmail, validateField}
import org.make.core.user.{User, UserId}

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "User")
@Path(value = "/user")
trait UserApi extends MakeAuthentication {
  this: UserServiceComponent with MakeDataHandlerComponent with IdGeneratorComponent =>

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
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[User])))
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "userId", paramType = "path", dataType = "string")))
  @Path(value = "/{userId}")
  def getUser: Route = {
    get {
      path("user" / userId) { userId =>
        makeTrace("GetUser") {
          makeOAuth2 { userAuth: AuthInfo[User] =>
            if (userId == userAuth.user.userId) {
              onSuccess(userService.getUser(userId)) {
                case Some(user) => complete(user)
                case None       => complete(NotFound)
              }
            } else {
              complete(Forbidden)
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "register-user", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.RegisterUserRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[User])))
  def register: Route = post {
    path("user") {
      makeTrace("RegisterUser") {
        decodeRequest {
          entity(as[RegisterUserRequest]) { request: RegisterUserRequest =>
            extractClientIP { ip =>
              onSuccess(
                userService
                  .register(
                    email = request.email,
                    firstName = request.firstName,
                    lastName = request.lastName,
                    password = request.password,
                    lastIp = ip.toOption.map(_.getHostAddress).getOrElse("unknown"),
                    dateOfBirth = request.dateOfBirth
                  )
              ) { result =>
                complete(StatusCodes.Created -> result)
              }
            }
          }
        }
      }
    }
  }

  val userRoutes: Route = register ~ getUser

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
