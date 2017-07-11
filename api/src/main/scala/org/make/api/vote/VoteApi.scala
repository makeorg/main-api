package org.make.api.vote

import java.time.ZonedDateTime
import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.server._
import io.circe.generic.auto._
import io.swagger.annotations._
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.HttpCodes
import org.make.core.proposition.PropositionId
import org.make.core.user.User
import org.make.core.vote.VoteStatus.VoteStatus
import org.make.core.vote.{Vote, VoteId}

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
      new ApiImplicitParam(name = "propositionId", paramType = "path", dataType = "String"),
      new ApiImplicitParam(name = "voteId", paramType = "path", dataType = "string")
    )
  )
  @Path(value = "/{propopsitionId}/{voteId}")
  def getVote: Route = {
    get {
      path("proposition" / refPropositionId / "vote" / voteId) { (propositionId, voteId) =>
        makeTrace("GetVote") {
          onSuccess(voteService.getVote(voteId, propositionId)) {
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
  @Path(value = "/{propositionId}")
  def vote: Route =
    makeOAuth2 { user: AuthInfo[User] =>
      post {
        path("vote" / refPropositionId) { propositionId =>
          makeTrace("Vote") {
            decodeRequest {
              entity(as[VoteRequest]) { request: VoteRequest =>
                onSuccess(
                  voteService.vote(
                    propositionId = propositionId,
                    userId = user.user.userId,
                    createdAt = ZonedDateTime.now,
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
  val refPropositionId: PathMatcher1[PropositionId] =
    Segment.flatMap(id => Try(PropositionId(id)).toOption)
}

case class VoteRequest(status: VoteStatus)
