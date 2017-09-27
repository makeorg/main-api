package org.make.api.proposal

import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.proposal._
import org.make.core.proposal.indexed.{IndexedProposal, Vote, VoteKey, VoteResponse}
import org.make.core.user.Role.{RoleAdmin, RoleModerator}
import org.make.core.user.User
import org.make.core.{DateHelper, HttpCodes}

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "Proposal")
@Path(value = "/proposals")
trait ProposalApi extends MakeAuthenticationDirectives with StrictLogging {
  this: ProposalServiceComponent with MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  @ApiOperation(value = "get-proposal", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[IndexedProposal]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string")))
  @Path(value = "/{proposalId}")
  def getProposal: Route = {
    get {
      path("proposals" / proposalId) { proposalId =>
        makeTrace("GetProposal") { requestContext =>
          provideAsyncOrNotFound(proposalService.getProposalById(proposalId, requestContext)) { proposal =>
            complete(proposal)
          }
        }
      }
    }
  }

  @ApiOperation(value = "search-proposals", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[IndexedProposal]]))
  )
  @ApiImplicitParams(
    value =
      Array(new ApiImplicitParam(name = "body", paramType = "body", dataType = "org.make.api.proposal.SearchRequest"))
  )
  @Path(value = "/search")
  def search: Route = {
    post {
      path("proposals" / "search") {
        makeTrace("Search") { requestContext =>
          // TODO if user logged in, must return additional information for propositions that belong to user
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[User]] =>
            decodeRequest {
              entity(as[SearchRequest]) { request: SearchRequest =>
                provideAsync(
                  proposalService
                    .search(userAuth.map(_.user.userId), request.toSearchQuery, requestContext)
                ) { proposals =>
                  complete(proposals)
                }
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "search-all-proposals",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[IndexedProposal]]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.proposal.ExhaustiveSearchRequest"
      )
    )
  )
  @Path(value = "/search/all")
  def searchAll: Route = {
    post {
      path("proposals" / "search" / "all") {
        makeTrace("SearchAll") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[User] =>
            authorize(userAuth.user.roles.exists(role => role == RoleAdmin || role == RoleModerator)) {
              decodeRequest {
                entity(as[ExhaustiveSearchRequest]) { request: ExhaustiveSearchRequest =>
                  provideAsync(
                    proposalService.search(Some(userAuth.user.userId), request.toSearchQuery, requestContext)
                  ) { proposals =>
                    complete(proposals)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "duplicates",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string")))
  @Path(value = "/{proposalId}/duplicates")
  def getDuplicates: Route = {
    get {
      path("proposals" / proposalId / "duplicates") { proposalId =>
        makeTrace("Duplicates") { requestContext =>
          makeOAuth2 { userAuth =>
            authorize(userAuth.user.roles.exists(role => role == RoleAdmin || role == RoleModerator)) {
              provideAsync(
                proposalService.getDuplicates(userId = userAuth.user.userId, proposalId = proposalId, requestContext)
              ) { proposals =>
                complete(proposals)
              }
            }
          }
        }
      }
    }
  }
  @ApiOperation(
    value = "propose-proposal",
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
        dataType = "org.make.api.proposal.ProposeProposalRequest"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposeProposalResponse]))
  )
  def postProposal: Route =
    post {
      path("proposals") {
        makeTrace("PostProposal") { requestContext =>
          makeOAuth2 { auth: AuthInfo[User] =>
            decodeRequest {
              entity(as[ProposeProposalRequest]) { request: ProposeProposalRequest =>
                onSuccess(
                  proposalService
                    .propose(
                      user = auth.user,
                      requestContext = requestContext,
                      createdAt = DateHelper.now(),
                      content = request.content
                    )
                ) { proposalId =>
                  complete(StatusCodes.Created -> ProposeProposalResponse(proposalId))
                }
              }
            }
          }
        }
      }
    }

  @ApiOperation(
    value = "update-proposal",
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
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.proposal.UpdateProposalRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Proposal])))
  @Path(value = "/{proposalId}")
  def updateProposal: Route =
    put {
      path("proposals" / proposalId) { proposalId =>
        makeTrace("EditProposal") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[User] =>
            decodeRequest {
              entity(as[UpdateProposalRequest]) { request: UpdateProposalRequest =>
                provideAsyncOrNotFound(proposalService.getEventSourcingProposal(proposalId, requestContext)) {
                  proposal =>
                    authorize(
                      proposal.author == userAuth.user.userId || userAuth.user.roles
                        .exists(role => role == RoleAdmin || role == RoleModerator)
                    ) {
                      onSuccess(
                        proposalService
                          .update(
                            proposalId = proposalId,
                            requestContext = requestContext,
                            updatedAt = DateHelper.now(),
                            content = request.content
                          )
                      ) {
                        case Some(prop) => complete(prop)
                        case None       => complete(StatusCodes.Forbidden)
                      }
                    }
                }
              }
            }
          }
        }
      }
    }

  @ApiOperation(
    value = "validate-proposal",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(new Authorization(value = "MakeApi"))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.proposal.ValidateProposalRequest"
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Proposal])))
  @Path(value = "/{proposalId}/accept")
  def acceptProposal: Route = post {
    path("proposals" / proposalId / "accept") { proposalId =>
      makeTrace("ValidateProposal") { requestContext =>
        makeOAuth2 { auth: AuthInfo[User] =>
          requireModerationRole(auth.user) {
            decodeRequest {
              entity(as[ValidateProposalRequest]) { request =>
                provideAsyncOrNotFound(
                  proposalService.validateProposal(
                    proposalId = proposalId,
                    moderator = auth.user.userId,
                    requestContext = requestContext,
                    request = request
                  )
                ) { proposal: Proposal =>
                  complete(proposal)
                }
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "refuse-proposal",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(new Authorization(value = "MakeApi"))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.proposal.RefuseProposalRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Proposal])))
  @Path(value = "/{proposalId}/refuse")
  def refuseProposal: Route = post {
    path("proposals" / proposalId / "refuse") { proposalId =>
      makeTrace("RefuseProposal") { requestContext =>
        makeOAuth2 { auth: AuthInfo[User] =>
          requireModerationRole(auth.user) {
            decodeRequest {
              entity(as[RefuseProposalRequest]) { refuseProposalRequest =>
                provideAsyncOrNotFound(
                  proposalService.refuseProposal(
                    proposalId = proposalId,
                    moderator = auth.user.userId,
                    requestContext = requestContext,
                    request = refuseProposalRequest
                  )
                ) { proposal: Proposal =>
                  complete(proposal)
                }
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "vote-proposal", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.proposal.VoteProposalRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Vote])))
  @Path(value = "/{proposalId}/vote")
  def vote: Route = post {
    path("proposals" / proposalId / "vote") { proposalId =>
      makeTrace("VoteProposal") { requestContext =>
        optionalMakeOAuth2 { maybeAuth: Option[AuthInfo[User]] =>
          decodeRequest {
            entity(as[VoteProposalRequest]) { request =>
              val maybeVoteKey = VoteKey.matchVoteKey(request.key)
              maybeVoteKey match {
                case None => complete(StatusCodes.BadRequest)
                case Some(voteKey) =>
                  provideAsync(
                    proposalService.voteProposal(
                      proposalId = proposalId,
                      userId = maybeAuth.map(_.user.userId),
                      requestContext = requestContext,
                      voteKey = voteKey
                    )
                  ) {
                    case Some(vote) =>
                      complete(VoteResponse.parseVote(vote, maybeAuth.map(_.user.userId), requestContext.sessionId))
                    case None => complete(StatusCodes.NotFound)
                  }
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "unvote-proposal", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.proposal.VoteProposalRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Vote])))
  @Path(value = "/{proposalId}/unvote")
  def unvote: Route = post {
    path("proposals" / proposalId / "unvote") { proposalId =>
      makeTrace("UnvoteProposal") { requestContext =>
        optionalMakeOAuth2 { maybeAuth: Option[AuthInfo[User]] =>
          decodeRequest {
            entity(as[VoteProposalRequest]) { request =>
              val maybeVoteKey = VoteKey.matchVoteKey(request.key)
              maybeVoteKey match {
                case None => complete(StatusCodes.BadRequest)
                case Some(voteKey) =>
                  provideAsync(
                    proposalService.unvoteProposal(
                      proposalId = proposalId,
                      userId = maybeAuth.map(_.user.userId),
                      requestContext = requestContext,
                      voteKey = voteKey
                    )
                  ) {
                    case Some(vote) =>
                      complete(VoteResponse.parseVote(vote, maybeAuth.map(_.user.userId), requestContext.sessionId))
                    case None => complete(StatusCodes.NotFound)
                  }
              }
            }
          }
        }
      }
    }
  }

  val proposalRoutes: Route =
    getDuplicates ~
      postProposal ~
      getProposal ~
      updateProposal ~
      acceptProposal ~
      refuseProposal ~
      search ~
      searchAll ~
      vote ~
      unvote

  val proposalId: PathMatcher1[ProposalId] =
    Segment.flatMap(id => Try(ProposalId(id)).toOption)

}
