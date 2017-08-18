package org.make.api.proposal

import java.time.ZonedDateTime
import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.StatusCodes.Forbidden
import akka.http.scaladsl.server._
import io.circe.generic.auto._
import io.swagger.annotations._
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.HttpCodes
import org.make.core.Validation.{maxLength, validate}
import org.make.core.proposal.{Proposal, ProposalId}
import org.make.core.user.User

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "Proposal")
@Path(value = "/proposal")
trait ProposalApi extends MakeAuthenticationDirectives {
  this: ProposalServiceComponent with MakeDataHandlerComponent with IdGeneratorComponent =>

  @ApiOperation(value = "get-proposal", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Proposal])))
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string")))
  @Path(value = "/{proposalId}")
  def getProposal: Route = {
    get {
      path("proposal" / proposalId) { proposalId =>
        makeTrace("GetProposal") { requestContext =>
          provideAsyncOrNotFound(proposalService.getProposal(proposalId, requestContext)) { proposal =>
            complete(proposal)
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
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Proposal])))
  def propose: Route =
    post {
      path("proposal") {
        makeTrace("Propose") { context =>
          makeOAuth2 { auth: AuthInfo[User] =>
            decodeRequest {
              entity(as[ProposeProposalRequest]) { request: ProposeProposalRequest =>
                onSuccess(
                  proposalService
                    .propose(
                      user = auth.user,
                      context = context,
                      createdAt = ZonedDateTime.now,
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
  def update: Route =
    put {
      path("proposal" / proposalId) { proposalId =>
        makeTrace("EditProposal") { requestContext =>
          makeOAuth2 { user: AuthInfo[User] =>
            decodeRequest {
              entity(as[UpdateProposalRequest]) { request: UpdateProposalRequest =>
                provideAsyncOrNotFound(proposalService.getProposal(proposalId, requestContext)) { proposal =>
                  authorize(proposal.author == user.user.userId) {
                    onSuccess(
                      proposalService
                        .update(
                          proposalId = proposalId,
                          context = requestContext,
                          updatedAt = ZonedDateTime.now,
                          content = request.content
                        )
                    ) {
                      case Some(prop) => complete(prop)
                      case None       => complete(Forbidden)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

  val proposalRoutes: Route = propose ~ getProposal ~ update

  val proposalId: PathMatcher1[ProposalId] =
    Segment.flatMap(id => Try(ProposalId(id)).toOption)

}

case class ProposeProposalRequest(content: String) {
  private val maxProposalLength = 140
  validate(maxLength("content", maxProposalLength, content))
}
case class ProposeProposalResponse(proposalId: ProposalId)
case class UpdateProposalRequest(content: String)
