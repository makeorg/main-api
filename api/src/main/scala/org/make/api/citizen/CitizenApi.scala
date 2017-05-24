package org.make.api.citizen

import java.time.LocalDate
import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes.{Forbidden, NotFound}
import akka.http.scaladsl.server._
import de.knutwalker.akka.http.support.CirceHttpSupport
import io.swagger.annotations._
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.core.CirceFormatters
import org.make.core.citizen.{Citizen, CitizenId}
import io.circe.generic.auto._
import kamon.akka.http.KamonTraceDirectives
import org.make.core.Validation.{requirement, requirements}

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "Citizen")
@Path(value = "/citizen")
trait CitizenApi extends CirceFormatters with Directives with KamonTraceDirectives with CirceHttpSupport with MakeAuthentication {
  this: CitizenServiceComponent with MakeDataHandlerComponent =>


  @ApiOperation(value = "get-citizen", httpMethod = "GET", code = 200, authorizations = Array(
    new Authorization(value = "MakeApi", scopes = Array(
      new AuthorizationScope(scope = "user", description = "application user"),
      new AuthorizationScope(scope = "admin", description = "BO Admin")
    ))
  ))
  @ApiResponses(value = Array(
    new ApiResponse(code = 200, message = "Ok", response = classOf[Citizen])
  ))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "citizenId", paramType = "path", dataType = "string")
  ))
  @Path(value = "/{citizenId}")
  def getCitizen: Route = {
    get {
      path("citizen" / citizenId) { citizenId =>
        traceName("GetCitizen") {
          makeOAuth2 { user: AuthInfo[Citizen] =>
            if (citizenId == user.user.citizenId) {
              onSuccess(citizenService.getCitizen(citizenId)) {
                case Some(citizen) => complete(citizen)
                case None => complete(NotFound)
              }
            } else {
              complete(Forbidden)
            }
          }
        }
      }
    }
  }


  @ApiOperation(value = "register-citizen", httpMethod = "POST", code = 200)
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.citizen.RegisterCitizenRequest")
  ))
  @ApiResponses(value = Array(
    new ApiResponse(code = 200, message = "Ok", response = classOf[Citizen])
  ))
  def register: Route = post {
    path("citizen") {
      traceName("RegisterCitizen") {
        decodeRequest {
          entity(as[RegisterCitizenRequest]) { request: RegisterCitizenRequest =>
            onSuccess(citizenService.register(
              email = request.email,
              dateOfBirth = request.dateOfBirth,
              firstName = request.firstName,
              lastName = request.lastName,
              password = request.password
            )) {
              complete(_)
            }
          }
        }
      }
    }
  }


  val citizenRoutes: Route = register ~ getCitizen

  val citizenId: PathMatcher1[CitizenId] = Segment.flatMap(id => Try(CitizenId(id)).toOption)

}

case class RegisterCitizenRequest(
                                   email: String,
                                   password: String,
                                   dateOfBirth: LocalDate,
                                   firstName: String,
                                   lastName: String
                                 ) {

  requirements(
    requirement(dateOfBirth != null, "Date of birth mustn't be null"),
    requirement(firstName != null, "First name mustn't be null"),
    requirement(lastName != null, "Last name mustn't be null"),
    requirement(email != null, "Email mustn't be null"),
    requirement(email != null && email.contains("@"), "Email must be an email"),
    requirement(password != null, "Password mustn't be null"),
    requirement(password != null && password.length > 5, "Password must be at least 6 characters")
  )

}

