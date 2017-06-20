package org.make.api.proposition

import java.time.ZonedDateTime
import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes.{Forbidden, NotFound}
import akka.http.scaladsl.server._
import io.circe.generic.auto._
import io.swagger.annotations._
import org.make.api.technical.MakeDirectives
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.HttpCodes
import org.make.core.user.User
import org.make.core.proposition.{Proposition, PropositionId}

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "Proposition")
@Path(value = "/proposition")
trait PropositionApi
    extends MakeDirectives { this: PropositionServiceComponent with MakeDataHandlerComponent =>

  @ApiOperation(value = "get-proposition", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Proposition])))
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "propositionId", paramType = "path", dataType = "string"))
  )
  @Path(value = "/{propositionId}")
  def getProposition: Route = {
    get {
      path("proposition" / propositionId) { propositionId =>
        makeTrace("GetProposition") {
          onSuccess(propositionService.getProposition(propositionId)) {
            case Some(proposition) => complete(proposition)
            case None              => complete(NotFound)
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "propose-proposition",
    httpMethod = "POST",
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
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.proposition.ProposePropositionRequest"
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Proposition])))
  def propose: Route =
    post {
      path("proposition") {
        makeTrace("Propose") {
          makeOAuth2 { user: AuthInfo[User] =>
            decodeRequest {
              entity(as[ProposePropositionRequest]) { request: ProposePropositionRequest =>
                onSuccess(
                  propositionService
                    .propose(userId = user.user.userId, createdAt = ZonedDateTime.now, content = request.content)
                ) {
                  complete(_)
                }
              }
            }
          }
        }
      }
    }

  @ApiOperation(
    value = "update-proposition",
    httpMethod = "PUT",
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
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.proposition.UpdatePropositionRequest"
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Proposition])))
  @Path(value = "/{propositionId}")
  def update: Route =
    put {
      path("proposition" / propositionId) { propositionId =>
        makeTrace("EditProposition") {
          makeOAuth2 { user: AuthInfo[User] =>
            decodeRequest {
              entity(as[UpdatePropositionRequest]) { request: UpdatePropositionRequest =>
                onSuccess(propositionService.getProposition(propositionId)) {
                  case Some(proposition) =>
                    if (proposition.userId == user.user.userId) {
                      onSuccess(
                        propositionService.update(
                          propositionId = propositionId,
                          updatedAt = ZonedDateTime.now,
                          content = request.content
                        )
                      ) {
                        case Some(prop) => complete(prop)
                        case None       => complete(Forbidden)
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
    }

  val propositionRoutes: Route = propose ~ getProposition ~ update

  val propositionId: PathMatcher1[PropositionId] =
    Segment.flatMap(id => Try(PropositionId(id)).toOption)

}

case class ProposePropositionRequest(content: String)
case class UpdatePropositionRequest(content: String)
