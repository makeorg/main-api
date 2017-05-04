package org.make.api.proposition

import java.time.ZonedDateTime
import javax.ws.rs.Path

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes.{Forbidden, NotFound}
import akka.http.scaladsl.server._
import io.swagger.annotations._
import org.make.api.Formatters
import org.make.api.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.core.citizen.Citizen
import org.make.core.proposition.{Proposition, PropositionId}

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "Proposition")
@Path(value = "/proposition")
trait PropositionApi extends Formatters with Directives with MakeAuthentication {
  this: PropositionServiceComponent with MakeDataHandlerComponent =>


  @ApiOperation(value = "get-proposition", httpMethod = "GET", code = 200)
  @ApiResponses(value = Array(
    new ApiResponse(code = 200, message = "Ok", response = classOf[Proposition])
  ))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "propositionId", paramType = "path", dataType = "string")
  ))
  @Path(value = "/{propositionId}")
  def getProposition: Route = {
    get {
      path("proposition" / propositionId) { propositionId =>
        onSuccess(propositionService.getProposition(propositionId)) {
          case Some(proposition) => complete(proposition)
          case None => complete(NotFound)
        }
      }
    }
  }


  @ApiOperation(value = "propose-proposition", httpMethod = "POST", code = 200, authorizations = Array(
    new Authorization(value = "MakeApi", scopes = Array(
      new AuthorizationScope (scope = "user", description = "application user"),
      new AuthorizationScope (scope = "admin", description = "BO Admin")
    ))
  ))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.proposition.ProposePropositionRequest")
  ))
  @ApiResponses(value = Array(
    new ApiResponse(code = 200, message = "Ok", response = classOf[Proposition])
  ))
  def propose: Route =
    makeOAuth2 { user: AuthInfo[Citizen] =>
      post {
        path("proposition") {
          decodeRequest {
            entity(as[ProposePropositionRequest]) {
              request: ProposePropositionRequest =>
                onSuccess(propositionService.propose(
                  citizenId = user.user.citizenId,
                  createdAt = ZonedDateTime.now,
                  content = request.content
                )) {
                  complete(_)
                }
            }
          }
        }
      }
    }

  @ApiOperation(value = "propose-proposition", httpMethod = "POST", code = 200, authorizations = Array(
    new Authorization(value = "MakeApi", scopes = Array(
      new AuthorizationScope (scope = "user", description = "application user"),
      new AuthorizationScope (scope = "admin", description = "BO Admin")
    ))
  ))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.proposition.ProposePropositionRequest")
  ))
  @ApiResponses(value = Array(
    new ApiResponse(code = 200, message = "Ok", response = classOf[Proposition])
  ))
  def update: Route =
    makeOAuth2 { user: AuthInfo[Citizen] =>
      put {
        path("proposition" / propositionId) { propositionId =>
          decodeRequest {
            entity(as[UpdatePropositionRequest]) { request: UpdatePropositionRequest =>
              onSuccess(propositionService.getProposition(propositionId)) {
                case Some(proposition) =>
                  if (proposition.citizenId == user.user.citizenId) {
                    onSuccess(propositionService.update(
                      propositionId = propositionId,
                      updatedAt = ZonedDateTime.now,
                      content = request.content
                    )) {
                      case Some(prop) => complete(prop)
                      case None => complete(Forbidden)
                    }
                  } else {
                    complete(Forbidden)
                  }
                case None => complete(NotFound)
              }
            }
          }
        }
      }
    }

  val propositionRoutes: Route = propose ~ getProposition ~ update

  val propositionId: PathMatcher1[PropositionId] = Segment.flatMap(id => Try(PropositionId(id)).toOption)

}

case class ProposePropositionRequest(content: String)
case class UpdatePropositionRequest(content: String)
