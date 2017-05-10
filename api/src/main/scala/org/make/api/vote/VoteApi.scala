package org.make.api.vote

import java.time.ZonedDateTime
import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.server._
import de.knutwalker.akka.http.support.CirceHttpSupport
import io.circe.generic.auto._
import io.swagger.annotations._
import org.make.api.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.core.CirceFormatters
import org.make.core.citizen.Citizen
import org.make.core.proposition.PropositionId
import org.make.core.vote.{Vote, VoteId}

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "Vote")
@Path(value = "/Vote")
trait VoteApi extends CirceFormatters with CirceHttpSupport with Directives with MakeAuthentication {
  this: VoteServiceComponent with MakeDataHandlerComponent =>


  @ApiOperation(value = "get-Vote", httpMethod = "GET", code = 200)
  @ApiResponses(value = Array(
    new ApiResponse(code = 200, message = "Ok", response = classOf[Vote])
  ))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "propositionId", paramType = "path", dataType = "String"),
    new ApiImplicitParam(name = "VoteId", paramType = "path", dataType = "string")
  ))
  @Path(value = "/{propopsitionId}/{voteId}")
  def getVote: Route = {
    get {
      path("proposition" / propositionId / "vote" / voteId) { (propositionId, voteId) =>
        onSuccess(voteService.getVote(voteId, propositionId)) {
          case Some(vote) => complete(Vote)
          case None => complete(NotFound)
        }
      }
    }
  }

  @ApiOperation(value = "agree-vote", httpMethod = "POST", code = 200, authorizations = Array(
    new Authorization(value = "MakeApi", scopes = Array(
      new AuthorizationScope(scope = "user", description = "application user"),
      new AuthorizationScope(scope = "admin", description = "BO Admin")
    ))
  ))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.vote.VoteAgreeRequest")
  ))
  @ApiResponses(value = Array(
    new ApiResponse(code = 200, message = "Ok", response = classOf[Vote])
  ))
  @Path(value = "/{propositionId}")
  def agree: Route =
    makeOAuth2 { user: AuthInfo[Citizen] =>
      post {
        path("proposition" / propositionId) { propositionId =>
          decodeRequest {
            entity(as[VoteAgreeRequest]) {
              request: VoteAgreeRequest =>
                onSuccess(voteService.agree(
                  propositionId = propositionId,
                  citizenId = user.user.citizenId,
                  createdAt = ZonedDateTime.now
                )) {
                  complete(_)
                }
            }
          }
        }
      }
    }


  @ApiOperation(value = "disagree-vote", httpMethod = "POST", code = 200, authorizations = Array(
    new Authorization(value = "MakeApi", scopes = Array(
      new AuthorizationScope (scope = "user", description = "application user"),
      new AuthorizationScope (scope = "admin", description = "BO Admin")
    ))
  ))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.vote.VoteDisagreeRequest")
  ))
  @ApiResponses(value = Array(
    new ApiResponse(code = 200, message = "Ok", response = classOf[Vote])
  ))
  @Path(value = "/{propositionId}")
  def disagree: Route =
    makeOAuth2 { user: AuthInfo[Citizen] =>
      post {
        path("proposition" / propositionId) { propositionId =>
          decodeRequest {
            entity(as[VoteDisagreeRequest]) {
              request: VoteDisagreeRequest =>
                onSuccess(voteService.disagree(
                  propositionId = propositionId,
                  citizenId = user.user.citizenId,
                  createdAt = ZonedDateTime.now
                )) {
                  complete(_)
                }
            }
          }
        }
      }
    }

  @ApiOperation(value = "unsure-vote", httpMethod = "POST", code = 200, authorizations = Array(
    new Authorization(value = "MakeApi", scopes = Array(
      new AuthorizationScope (scope = "user", description = "application user"),
      new AuthorizationScope (scope = "admin", description = "BO Admin")
    ))
  ))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.vote.VoteUnsureRequest")
  ))
  @ApiResponses(value = Array(
    new ApiResponse(code = 200, message = "Ok", response = classOf[Vote])
  ))
  @Path(value = "/{propositionId}")
  def unsure: Route =
    makeOAuth2 { user: AuthInfo[Citizen] =>
      post {
        path("proposition" / propositionId) { propositionId =>
          decodeRequest {
            entity(as[VoteUnsureRequest]) {
              request: VoteUnsureRequest =>
                onSuccess(voteService.unsure(
                  propositionId = propositionId,
                  citizenId = user.user.citizenId,
                  createdAt = ZonedDateTime.now
                )) {
                  complete(_)
                }
            }
          }
        }
      }
    }

  val voteRoutes: Route = agree ~ getVote ~ disagree ~ unsure
  val voteId: PathMatcher1[VoteId] = Segment.flatMap(id => Try(VoteId(id)).toOption)
  val propositionId: PathMatcher1[PropositionId] = Segment.flatMap(id => Try(PropositionId(id)).toOption)
}

case class VoteAgreeRequest(content: String)
case class VoteDisagreeRequest(content: String)
case class VoteUnsureRequest(content: String)
