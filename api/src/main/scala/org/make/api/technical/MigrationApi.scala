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

import akka.Done
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal.{ProposalCoordinatorServiceComponent, UpdateProposalVotesVerifiedCommand}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.user.UserServiceComponent
import org.make.api.userhistory.UserEvent.{SnapshotUser, UserRegisteredEvent}
import org.make.api.userhistory.{
  LogUserSearchSequencesEvent,
  LogUserUpdateSequenceEvent,
  UserHistoryCoordinatorComponent
}
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.{ProposalId, Vote}
import org.make.core.tag.{Tag => _}
import org.make.core.user.UserId
import org.make.core.{DateHelper, HttpCodes, RequestContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Api(value = "Migrations")
@Path(value = "/migrations")
trait MigrationApi extends Directives {

  def emptyRoute: Route

  @ApiOperation(
    value = "replay-user-registration-event",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @Path(value = "/replay-user-registration")
  def replayUserRegistrationEvent: Route

  @ApiOperation(
    value = "replay-user-history-event",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "Ok")))
  @Path(value = "/replay-user-history")
  def replayUserHistoryEvent: Route

  @ApiOperation(
    value = "reset-qualification-count",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @Path(value = "/reset-qualification-count")
  def resetQualificationCount: Route

  def routes: Route = emptyRoute ~ replayUserRegistrationEvent ~ replayUserHistoryEvent ~ resetQualificationCount
}

trait MigrationApiComponent {
  def migrationApi: MigrationApi
}

trait DefaultMigrationApiComponent extends MigrationApiComponent with MakeAuthenticationDirectives with StrictLogging {
  this: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent
    with ProposalCoordinatorServiceComponent
    with UserServiceComponent
    with UserHistoryCoordinatorComponent
    with ReadJournalComponent
    with ActorSystemComponent =>

  override lazy val migrationApi: MigrationApi = new MigrationApi {
    override def emptyRoute: Route =
      get {
        path("migrations") {
          complete(StatusCodes.OK)
        }
      }

    override def replayUserRegistrationEvent: Route = post {
      path("migrations" / "replay-user-registration") {
        makeOperation("ReplayRegistrationEvent") { _ =>
          makeOAuth2 { userAuth =>
            requireAdminRole(userAuth.user) {
              implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
              userJournal
                .currentPersistenceIds()
                .runForeach { id =>
                  userJournal
                    .currentEventsByPersistenceId(id, 0, Long.MaxValue)
                    .filter {
                      case EventEnvelope(_, _, _, _: UserRegisteredEvent) => true
                      case _                                              => false
                    }
                    .runForeach { eventEnvelope =>
                      val event = eventEnvelope.event.asInstanceOf[UserRegisteredEvent]
                      if (event.registerQuestionId != event.requestContext.questionId) {
                        userService.getUser(UserId(id)).map { maybeUser =>
                          maybeUser.map { user =>
                            userService.update(
                              user.copy(
                                profile = user.profile.map(
                                  _.copy(
                                    registerQuestionId =
                                      event.registerQuestionId.orElse(event.requestContext.questionId)
                                  )
                                )
                              ),
                              RequestContext.empty
                            )
                          }
                        }
                      }
                    }
                }
              complete(StatusCodes.NoContent)
            }
          }
        }
      }
    }

    override def replayUserHistoryEvent: Route = post {
      path("migrations" / "replay-user-history") {
        makeOperation("ReplayHistoryEvent") { _ =>
          makeOAuth2 { userAuth =>
            requireAdminRole(userAuth.user) {
              implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
              userJournal
                .currentPersistenceIds()
                .runForeach { id =>
                  userJournal
                    .currentEventsByPersistenceId(id, 0, Long.MaxValue)
                    .filter {
                      case EventEnvelope(_, _, _, _: LogUserUpdateSequenceEvent)  => true
                      case EventEnvelope(_, _, _, _: LogUserSearchSequencesEvent) => true
                      case _                                                      => false
                    }
                    .runForeach {
                      case EventEnvelope(_, persistenceId, sequenceNr, _: LogUserUpdateSequenceEvent) =>
                        logger.warn(
                          s"Event of type LogUserUpdateSequenceEvent with persistenceId=$persistenceId and sequenceNr=$sequenceNr"
                        )
                      case EventEnvelope(_, persistenceId, sequenceNr, _: LogUserSearchSequencesEvent) =>
                        logger.warn(
                          s"Event of type LogUserSearchSequencesEvent with persistenceId=$persistenceId and sequenceNr=$sequenceNr"
                        )
                      case _ => ()
                    }
                  userHistoryCoordinator ! SnapshotUser(UserId(id))
                }
              complete(StatusCodes.NoContent)
            }
          }
        }
      }
    }

    override def resetQualificationCount: Route = post {
      path("migrations" / "reset-qualification-count") {
        withoutRequestTimeout {
          makeOperation("ResetQualificationCount") { requestContext =>
            makeOAuth2 { userAuth =>
              requireAdminRole(userAuth.user) {
                implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
                val futureToComplete: Future[Done] = proposalJournal
                  .currentPersistenceIds()
                  .mapAsync(4) { id =>
                    val proposalId = ProposalId(id)
                    proposalCoordinatorService.getProposal(proposalId)
                  }
                  .filter { proposal =>
                    proposal.isDefined && proposal.exists(_.status == Accepted)
                  }
                  .mapAsync(4) { maybeProposal =>
                    val proposal = maybeProposal.get
                    def updateCommand(votes: Seq[Vote]): UpdateProposalVotesVerifiedCommand = {
                      val votesResetQualifVerified = votes.map { vote =>
                        vote.copy(
                          countVerified = vote.count,
                          qualifications = vote.qualifications.map(q => q.copy(countVerified = q.count))
                        )
                      }
                      UpdateProposalVotesVerifiedCommand(
                        moderator = userAuth.user.userId,
                        proposalId = proposal.proposalId,
                        requestContext = requestContext,
                        updatedAt = DateHelper.now(),
                        votesVerified = votesResetQualifVerified
                      )
                    }
                    proposalCoordinatorService.updateVotesVerified(updateCommand(proposal.votes))
                  }
                  .runForeach(_ => Done)
                provideAsync(futureToComplete) { _ =>
                  complete(StatusCodes.NoContent)
                }
              }
            }
          }
        }
      }
    }
  }
}
