package org.make.api.vote

import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.server._
import io.circe.generic.auto._
import io.swagger.annotations._
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.proposal.ProposalId
import org.make.core.user.User
import org.make.core.vote.VoteStatus.VoteStatus
import org.make.core.vote.{Vote, VoteId}
import org.make.core.{DateHelper, HttpCodes}

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "Vote")
@Path(value = "/vote")
trait VoteApi extends MakeAuthenticationDirectives {
  this: VoteServiceComponent with MakeDataHandlerComponent with IdGeneratorComponent =>

  @ApiOperation(value = "get-vote", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Vote])))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "String"),
      new ApiImplicitParam(name = "voteId", paramType = "path", dataType = "string")
    )
  )
  @Path(value = "/{propopsalId}/{voteId}")
  def getVote: Route = {
    get {
      path("proposal" / refProposalId / "vote" / voteId) { (proposalId, voteId) =>
        makeTrace("GetVote") { _ =>
          onSuccess(voteService.getVote(voteId, proposalId)) {
            case Some(vote) => complete(vote)
            case None       => complete(NotFound)
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "vote",
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
    value = Array(new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.vote.VoteRequest"))
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Vote])))
  @Path(value = "/{proposalId}")
  def vote: Route =
    makeOAuth2 { user: AuthInfo[User] =>
      post {
        path("vote" / refProposalId) { proposalId =>
          makeTrace("Vote") { _ =>
            decodeRequest {
              entity(as[VoteRequest]) { request: VoteRequest =>
                onSuccess(
                  voteService.vote(
                    proposalId = proposalId,
                    userId = user.user.userId,
                    createdAt = DateHelper.now(),
                    status = request.status
                  )
                ) {
                  complete(_)
                }
              }
            }
          }
        }
      }
    }

  val voteRoutes: Route = vote ~ getVote
  val voteId: PathMatcher1[VoteId] =
    Segment.flatMap(id => Try(VoteId(id)).toOption)
  val refProposalId: PathMatcher1[ProposalId] =
    Segment.flatMap(id => Try(ProposalId(id)).toOption)
}

case class VoteRequest(status: VoteStatus)
