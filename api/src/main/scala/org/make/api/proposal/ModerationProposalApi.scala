package org.make.api.proposal

import javax.ws.rs.Path

import akka.http.scaladsl.server._
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.user.UserServiceComponent
import org.make.core.auth.UserRights
import org.make.core.proposal.ProposalId
import org.make.core.proposal.indexed.ProposalsSearchResult
import org.make.core.{DateHelper, HttpCodes}

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "ModerationProposal")
@Path(value = "/moderation/proposals")
trait ModerationProposalApi extends MakeAuthenticationDirectives with StrictLogging {
  this: ProposalServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with UserServiceComponent =>

  @ApiOperation(
    value = "get-moderation-proposal",
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
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalResponse]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string")))
  @Path(value = "/{proposalId}")
  def getModerationProposal: Route = {
    get {
      path("moderation" / "proposals" / moderationProposalId) { proposalId =>
        makeTrace("GetModerationProposal") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              provideAsyncOrNotFound(proposalService.getModerationProposalById(proposalId)) { proposalResponse =>
                complete(proposalResponse)
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "moderation-search-proposals",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsSearchResult]))
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
  @Path(value = "/search")
  def searchAllProposals: Route = {
    post {
      path("moderation" / "proposals" / "search") {
        makeTrace("SearchAll") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireModerationRole(userAuth.user) {
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
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.proposal.UpdateProposalRequest"
      ),
      new ApiImplicitParam(name = "proposalId", paramType = "path", required = true, value = "", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalResponse]))
  )
  @Path(value = "/{proposalId}")
  def updateProposal: Route =
    put {
      path("moderation" / "proposals" / moderationProposalId) { proposalId =>
        makeTrace("EditProposal") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireModerationRole(userAuth.user) {
              decodeRequest {
                entity(as[UpdateProposalRequest]) { request =>
                  provideAsyncOrNotFound(
                    proposalService.update(
                      proposalId = proposalId,
                      moderator = userAuth.user.userId,
                      requestContext = requestContext,
                      updatedAt = DateHelper.now(),
                      request = request
                    )
                  ) { proposalResponse: ProposalResponse =>
                    complete(proposalResponse)
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
      ),
      new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalResponse]))
  )
  @Path(value = "/{proposalId}/accept")
  def acceptProposal: Route = post {
    path("moderation" / "proposals" / moderationProposalId / "accept") { proposalId =>
      makeTrace("ValidateProposal") { requestContext =>
        makeOAuth2 { auth: AuthInfo[UserRights] =>
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
                ) { proposalResponse: ProposalResponse =>
                  complete(proposalResponse)
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
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalResponse]))
  )
  @Path(value = "/{proposalId}/refuse")
  def refuseProposal: Route = post {
    path("moderation" / "proposals" / moderationProposalId / "refuse") { proposalId =>
      makeTrace("RefuseProposal") { requestContext =>
        makeOAuth2 { auth: AuthInfo[UserRights] =>
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
                ) { proposalResponse: ProposalResponse =>
                  complete(proposalResponse)
                }
              }
            }
          }
        }
      }
    }
  }

  val moderationProposalRoutes: Route =
    getModerationProposal ~
      searchAllProposals ~
      updateProposal ~
      acceptProposal ~
      refuseProposal

  val moderationProposalId: PathMatcher1[ProposalId] =
    Segment.flatMap(id => Try(ProposalId(id)).toOption)

}
