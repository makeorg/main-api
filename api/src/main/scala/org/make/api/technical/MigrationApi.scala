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

package org.make.api.technical

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import grizzled.slf4j.Logging
import io.swagger.annotations._
import org.make.api.ActorSystemComponent
import org.make.api.proposal.{
  PatchProposalRequest,
  ProposalCoordinatorServiceComponent,
  UpdateQualificationRequest,
  UpdateVoteRequest
}
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.core._
import org.make.core.proposal.{
  BaseVoteOrQualification,
  Key,
  ProposalId,
  ProposalStatus,
  Qualification,
  QualificationKey,
  Vote,
  VoteKey
}

import javax.ws.rs.Path
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Api(value = "Migrations")
@Path(value = "/migrations")
trait MigrationApi extends Directives {

  def emptyRoute: Route

  @ApiOperation(
    value = "snapshot-all-proposals",
    httpMethod = "POST",
    code = HttpCodes.NoContent,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "dry", paramType = "query", dataType = "boolean")))
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
  @Path(value = "/snapshot-all-proposals")
  def snapshotAllProposals: Route

  def routes: Route = snapshotAllProposals
}

trait MigrationApiComponent {
  def migrationApi: MigrationApi
}

trait DefaultMigrationApiComponent extends MigrationApiComponent with MakeAuthenticationDirectives with Logging {
  this: MakeDirectivesDependencies
    with ActorSystemComponent
    with DateHelperComponent
    with ReadJournalComponent
    with ProposalCoordinatorServiceComponent =>

  override lazy val migrationApi: MigrationApi = new DefaultMigrationApi

  class DefaultMigrationApi extends MigrationApi {
    override def emptyRoute: Route =
      get {
        path("migrations") {
          complete(StatusCodes.OK)
        }
      }

    override def snapshotAllProposals: Route = post {
      path("migrations" / "snapshot-all-proposals") {
        makeOperation("SnapshotAllProposals") { requestContext =>
          makeOAuth2 { userAuth =>
            requireAdminRole(userAuth.user) {
              parameters("dry".as[Boolean].?) { dry =>
                type Field[Element, Request] = (String, Element => Int, Request => Request)

                val voteFields: Seq[Field[Vote, UpdateVoteRequest]] = Seq(
                  ("count", _.count, _.copy(count = Some(0))),
                  ("countVerified", _.countVerified, _.copy(countVerified = Some(0))),
                  ("countSegment", _.countSegment, _.copy(countSegment = Some(0))),
                  ("countSequence", _.countSequence, _.copy(countSequence = Some(0)))
                )
                val qualificationFields: Seq[Field[Qualification, UpdateQualificationRequest]] = Seq(
                  ("count", _.count, _.copy(count = Some(0))),
                  ("countVerified", _.countVerified, _.copy(countVerified = Some(0))),
                  ("countSegment", _.countSegment, _.copy(countSegment = Some(0))),
                  ("countSequence", _.countSequence, _.copy(countSequence = Some(0)))
                )

                def getUpdates[E, R](id: ProposalId, key: Key, e: E, fields: Seq[Field[E, R]]): Seq[R => R] = {
                  fields.flatMap {
                    case (name, get, set) =>
                      val count = get(e)
                      if (count < 0) {
                        logger.warn(s"Proposal $id has negative $key $name: $count")
                        Some(set)
                      } else {
                        None
                      }
                  }
                }

                def buildRequests[K <: Key, Element <: BaseVoteOrQualification[K], Request, SubParam](
                  id: ProposalId,
                  buildSubParams: Element => Seq[SubParam],
                  elements: Seq[Element],
                  fields: Seq[Field[Element, Request]],
                  requestBuilder: (K, Seq[SubParam]) => Request
                ): Seq[Request] = {
                  elements.flatMap { e =>
                    val updates = getUpdates(id, e.key, e, fields)
                    val subParams = buildSubParams(e)
                    if (updates.isEmpty && subParams.isEmpty) {
                      None
                    } else {
                      Some(updates.foldLeft(requestBuilder(e.key, subParams)) { (request, set) =>
                        set(request)
                      })
                    }
                  }
                }

                proposalJournal
                  .currentPersistenceIds()
                  .map(ProposalId.apply)
                  .mapAsync(4) { id =>
                    proposalCoordinatorService.getProposal(id).map {
                      case Some(proposal)
                          if Seq(ProposalStatus.Accepted, ProposalStatus.Archived, ProposalStatus.Refused)
                            .contains(proposal.status) =>
                        val updateVoteRequests =
                          buildRequests[VoteKey, Vote, UpdateVoteRequest, UpdateQualificationRequest](
                            id = proposal.proposalId,
                            buildSubParams = vote =>
                              buildRequests[QualificationKey, Qualification, UpdateQualificationRequest, Nothing](
                                id = proposal.proposalId,
                                buildSubParams = _ => Nil,
                                elements = vote.qualifications,
                                fields = qualificationFields,
                                requestBuilder = { case (key, _) => UpdateQualificationRequest(key) }
                              ),
                            elements = proposal.votes,
                            fields = voteFields,
                            requestBuilder = {
                              case (key, qualifs) => UpdateVoteRequest(key, None, None, None, None, qualifs)
                            }
                          )
                        val update =
                          if (updateVoteRequests.isEmpty) Future.successful(Some(proposal))
                          else {
                            logger.info(s"Update proposal $id votes with $updateVoteRequests")
                            if (dry.contains(false))
                              proposalCoordinatorService
                                .updateVotes(
                                  userAuth.user.userId,
                                  id,
                                  requestContext,
                                  dateHelper.now(),
                                  updateVoteRequests
                                )
                            else Future.successful(Some(proposal))
                          }
                        update.flatMap {
                          case Some(_) if (dry.contains(false)) =>
                            proposalCoordinatorService
                              .patch(id, userAuth.user.userId, PatchProposalRequest(), requestContext)
                          case other =>
                            if (other.isEmpty) logger.error(s"Updating votes of proposal $id failed")
                            Future.successful(None)
                        }
                      case Some(_) => ()
                      case None    => logger.error(s"No proposal found with id $id")
                    }
                  }
                  .run()
                complete(StatusCodes.NoContent)
              }
            }
          }
        }
      }
    }
  }
}
