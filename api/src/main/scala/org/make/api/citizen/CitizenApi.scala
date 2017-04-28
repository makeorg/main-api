package org.make.api.citizen

import java.time.LocalDate
import javax.ws.rs.Path

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes.{Forbidden, NotFound}
import akka.http.scaladsl.server._
import io.swagger.annotations._
import org.make.api.Formatters
import org.make.api.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.core.citizen.{Citizen, CitizenId}

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "Citizen")
@Path(value = "/citizen")
trait CitizenApi extends Formatters with Directives with MakeAuthentication {
  this: CitizenServiceComponent with MakeDataHandlerComponent =>


  @ApiOperation(value = "get-citizen", httpMethod = "GET", code = 200, authorizations = Array(
    new Authorization(value = "MakeApi", scopes = Array(
      new AuthorizationScope (scope = "user", description = "application user"),
      new AuthorizationScope (scope = "admin", description = "BO Admin")
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
    makeOAuth2 { user: AuthInfo[Citizen] =>
      get {
        path("citizen" / citizenId) { citizenId =>
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


  @ApiOperation(value = "register-citizen", httpMethod = "POST", code = 200)
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.citizen.RegisterCitizenRequest")
  ))
  @ApiResponses(value = Array(
    new ApiResponse(code = 200, message = "Ok", response = classOf[Citizen])
  ))
  def register: Route = post {
    path("citizen") {
      decodeRequest {
        entity(as[RegisterCitizenRequest]) {
          request: RegisterCitizenRequest =>
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



  val citizenRoutes: Route = register ~ getCitizen

  val citizenId: PathMatcher1[CitizenId] = Segment.flatMap(id => Try(CitizenId(id)).toOption)

}

case class RegisterCitizenRequest(
                                   email: String,
                                   password: String,
                                   dateOfBirth: LocalDate,
                                   firstName: String,
                                   lastName: String
                                 )

