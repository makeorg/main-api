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
import akka.persistence.query.EventEnvelope
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.scaladsl.{Sink, Source}
import akka.Done
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.{DefaultMailJetConfigurationComponent, MakeSettingsComponent}
import org.make.api.proposal.{
  ProposalCoordinatorServiceComponent,
  ProposalServiceComponent,
  UpdateProposalVotesVerifiedCommand
}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.crm.CrmServiceComponent
import org.make.api.user.{
  PersistentUserServiceComponent,
  PersistentUserToAnonymizeServiceComponent,
  UserServiceComponent
}
import org.make.api.userhistory.UserEvent.{SnapshotUser, UserRegisteredEvent}
import org.make.api.userhistory.{
  LogUserSearchSequencesEvent,
  LogUserUpdateSequenceEvent,
  UserHistoryCoordinatorComponent
}
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.{ProposalId, Vote}
import org.make.core.question.QuestionId
import org.make.core.tag.{Tag => _}
import org.make.core.user.UserId
import org.make.core.{DateHelper, HttpCodes, RequestContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
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
    value = "replay-user-vote-event",
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
  @Path(value = "/replay-user-vote")
  def replayUserVoteEvent: Route

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

  @ApiOperation(
    value = "extract-mailjet-anonymized-users",
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
  @Path(value = "/extract-anon-users")
  def extractAnonUser: Route

  def routes: Route =
    emptyRoute ~ replayUserRegistrationEvent ~ replayUserHistoryEvent ~ resetQualificationCount ~ extractAnonUser
}

trait MigrationApiComponent {
  def migrationApi: MigrationApi
}

trait DefaultMigrationApiComponent extends MigrationApiComponent with MakeAuthenticationDirectives with StrictLogging {
  this: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent
    with ProposalServiceComponent
    with ProposalCoordinatorServiceComponent
    with UserServiceComponent
    with PersistentUserServiceComponent
    with PersistentUserToAnonymizeServiceComponent
    with UserHistoryCoordinatorComponent
    with ReadJournalComponent
    with ActorSystemComponent
    with CrmServiceComponent
    with DefaultMailJetConfigurationComponent =>

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

    override def replayUserVoteEvent: Route = post {
      path("migrations" / "replay-user-vote") {
        withoutRequestTimeout {
          makeOperation("ReplayVoteEvent") { requestContext =>
            makeOAuth2 { userAuth =>
              requireAdminRole(userAuth.user) {
                val decider: Supervision.Decider = Supervision.resumingDecider
                implicit val materializer: ActorMaterializer = ActorMaterializer(
                  ActorMaterializerSettings(actorSystem).withSupervisionStrategy(decider)
                )(actorSystem)
                val streamUser = userService.getUsersWithoutRegisterQuestion.flatMap { users =>
                  Source(users.toIndexedSeq)
                    .mapAsync(5) { user =>
                      proposalService
                        .searchProposalsVotedByUser(
                          userId = user.userId,
                          filterVotes = None,
                          filterQualifications = None,
                          requestContext = requestContext
                        )
                        .map((_, user))
                    }
                    .mapAsync(5) {
                      case (proposals, user) =>
                        val registerQuestionId: Option[QuestionId] =
                          proposals.results.headOption.flatMap(_.question.map(_.questionId))
                        userService.update(
                          user.copy(profile = user.profile.map(_.copy(registerQuestionId = registerQuestionId))),
                          requestContext
                        )
                    }
                    .runWith(Sink.ignore)
                }
                provideAsync(streamUser) { _ =>
                  complete(StatusCodes.NoContent)
                }
              }
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

    private def addMailsToUserToAnonymize(listId: String): Future[Done] = {
      implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
      val limit = 1000
      Source
        .unfoldAsync(1) { page =>
          crmService.getUsersMailFromList(listId = listId, offset = (page - 1) * limit, limit = limit).map {
            getUsersMails =>
              if (getUsersMails.data.isEmpty) {
                None
              } else {
                Some((page + 1, getUsersMails.data.map(_.email)))
              }
          }
        }
        .mapAsync(1)(
          mailjetMails =>
            persistentUserService
              .findAllByEmail(mailjetMails)
              .map(users => (users.map(_.email), mailjetMails))
        )
        .map {
          case (usersMail, mailjetMails) => mailjetMails.filterNot(usersMail.contains).distinct
        }
        .mapConcat(_.toList)
        .mapAsync(10)(persistentUserToAnonymizeService.create)
        .groupedWithin(1000, 500.milliseconds)
        .map(n => logger.info(s"${n.size} mails where added to user_to_anonymize"))
        .runForeach(_ => ())
    }

    override def extractAnonUser: Route = post {
      path("migrations" / "extract-anon-users") {
        withoutRequestTimeout {
          makeOperation("ExtractAnonUser") { _ =>
            makeOAuth2 { userAuth =>
              requireAdminRole(userAuth.user) {
                val futureAnonDone: Future[Done.type] = for {
                  _ <- addMailsToUserToAnonymize(mailJetConfiguration.optInListId)
                  _ <- addMailsToUserToAnonymize(mailJetConfiguration.hardBounceListId)
                  _ <- addMailsToUserToAnonymize(mailJetConfiguration.unsubscribeListId)
                } yield Done
                provideAsync(futureAnonDone) { _ =>
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
