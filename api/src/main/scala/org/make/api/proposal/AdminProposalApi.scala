/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.proposal

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, PathMatcher1, Route}
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.proposal.ProposalId
import org.make.core.question.Question
import org.make.core.{DateHelper, HttpCodes, ParameterExtractors}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Api(value = "AdminProposal")
@Path(value = "/admin/proposals")
trait AdminProposalApi extends Directives {
  @ApiOperation(
    value = "fix-trolled-proposal",
    httpMethod = "PUT",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.proposal.UpdateProposalVotesRequest"
      ),
      new ApiImplicitParam(name = "proposalId", paramType = "path", required = true, value = "", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationProposalResponse]))
  )
  @Path(value = "/{proposalId}/fix-trolled-proposal")
  def updateProposalVotes: Route

  @ApiOperation(
    value = "reset-unverified-proposal-votes",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.Accepted, message = "Accepted")))
  @Path(value = "/reset-votes")
  def resetVotes: Route

  @ApiOperation(
    value = "patch-proposal",
    httpMethod = "PATCH",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationProposalResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "body", paramType = "body", dataType = "org.make.api.proposal.PatchProposalRequest")
    )
  )
  @Path(value = "/{proposalId}")
  def patchProposal: Route

  def routes: Route = patchProposal ~ updateProposalVotes ~ resetVotes
}

trait AdminProposalApiComponent {
  def adminProposalApi: AdminProposalApi
}

trait DefaultAdminProposalApiComponent
    extends AdminProposalApiComponent
    with MakeAuthenticationDirectives
    with StrictLogging
    with ParameterExtractors {

  this: ProposalServiceComponent
    with ProposalCoordinatorServiceComponent
    with QuestionServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent =>

  override lazy val adminProposalApi: AdminProposalApi = new DefaultAdminProposalApi

  class DefaultAdminProposalApi extends AdminProposalApi {
    val adminProposalId: PathMatcher1[ProposalId] = Segment.map(id => ProposalId(id))

    def updateProposalVotes: Route = put {
      path("admin" / "proposals" / adminProposalId / "fix-trolled-proposal") { proposalId =>
        makeOperation("EditProposalVotesVerified") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[UpdateProposalVotesRequest]) { request =>
                  provideAsyncOrNotFound(retrieveProposalQuestion(proposalId)) { _ =>
                    provideAsyncOrNotFound(
                      proposalService.updateVotes(
                        proposalId = proposalId,
                        moderator = userAuth.user.userId,
                        requestContext = requestContext,
                        updatedAt = DateHelper.now(),
                        votesVerified = request.votes
                      )
                    ) { moderationProposalResponse: ModerationProposalResponse =>
                      complete(moderationProposalResponse)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def patchProposal: Route = {
      patch {
        path("admin" / "proposals" / adminProposalId) { id =>
          makeOperation("PatchProposal") { context =>
            makeOAuth2 { auth =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[PatchProposalRequest]) { patch =>
                    provideAsyncOrNotFound(proposalService.patchProposal(id, auth.user.userId, context, patch)) {
                      proposal =>
                        complete(proposal)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    private def retrieveProposalQuestion(proposalId: ProposalId): Future[Option[Question]] = {
      proposalCoordinatorService.getProposal(proposalId).flatMap {
        case Some(proposal) =>
          proposal.questionId
            .map(questionService.getQuestion)
            .getOrElse(Future.successful(None))
        case None =>
          Future.successful(None)
      }
    }

    override def resetVotes: Route = post {
      path("admin" / "proposals" / "reset-votes") {
        withoutRequestTimeout {
          makeOperation("ResetVotes") { requestContext =>
            makeOAuth2 { userAuth =>
              requireAdminRole(userAuth.user) {
                proposalService.resetVotes(userAuth.user.userId, requestContext)
                complete(StatusCodes.Accepted)
              }
            }
          }
        }
      }
    }

  }
}
