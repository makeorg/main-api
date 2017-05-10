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
    new ApiImplicitParam(name = "VoteId", paramType = "path", dataType = "string")
  ))
  @Path(value = "/{VoteId}")
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
    new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.vote.AgreeVoteRequest")
  ))
  @ApiResponses(value = Array(
    new ApiResponse(code = 200, message = "Ok", response = classOf[Vote])
  ))
  def agree: Route =
    makeOAuth2 { user: AuthInfo[Citizen] =>
      post {
        path("vote" / propositionId) { propositionId =>
          decodeRequest {
            entity(as[ProposeVoteRequest]) {
              request: ProposeVoteRequest =>
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
    new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.vote.DisagreeVoteRequest")
  ))
  @ApiResponses(value = Array(
    new ApiResponse(code = 200, message = "Ok", response = classOf[Vote])
  ))
  def disagree: Route =
    makeOAuth2 { user: AuthInfo[Citizen] =>
      post {
        path("vote" / propositionId) { propositionId =>
          decodeRequest {
            entity(as[ProposeVoteRequest]) {
              request: ProposeVoteRequest =>
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
    new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.vote.UnsureVoteRequest")
  ))
  @ApiResponses(value = Array(
    new ApiResponse(code = 200, message = "Ok", response = classOf[Vote])
  ))
  def unsure: Route =
    makeOAuth2 { user: AuthInfo[Citizen] =>
      post {
        path("vote" / propositionId) { propositionId =>
          decodeRequest {
            entity(as[ProposeVoteRequest]) {
              request: ProposeVoteRequest =>
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

case class ProposeVoteRequest(content: String)
case class UpdateVoteRequest(content: String)
