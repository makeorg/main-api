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
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation.{
  CreateOperationOfQuestion,
  OperationOfQuestionServiceComponent,
  OperationServiceComponent
}
import org.make.api.proposal.ProposalServiceComponent
import org.make.api.question.AuthorRequest
import org.make.api.sequence.{SequenceConfigurationComponent, SequenceResult, SequenceServiceComponent}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.security.{SecurityConfigurationComponent, SecurityHelper}
import org.make.api.user.UserServiceComponent
import org.make.api.userhistory.UserEvent.{SnapshotUser, UserRegisteredEvent}
import org.make.api.userhistory.{
  LogUserSearchSequencesEvent,
  LogUserUpdateSequenceEvent,
  UserHistoryCoordinatorComponent,
  UserHistoryCoordinatorServiceComponent
}
import org.make.core.auth.UserRights
import org.make.core.operation.OperationOfQuestion
import org.make.core.proposal._
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.session.SessionId
import org.make.core.tag.{Tag => _}
import org.make.core.user.UserId
import org.make.core.{DateHelper, HttpCodes, RequestContext}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field
import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

@Api(value = "Migrations")
@Path(value = "/migrations")
trait MigrationApi extends Directives {

  def emptyRoute: Route

  @ApiOperation(
    value = "test-sequence-behaviour",
    httpMethod = "POST",
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
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.technical.TestSequenceRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TestSequenceResponse]))
  )
  @Path(value = "/test-sequence")
  def testSequence: Route

  @ApiOperation(
    value = "test-sequence-votes-behaviour",
    httpMethod = "POST",
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
        dataType = "org.make.api.technical.TestSequenceVotesRequest"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TestSequenceVotesResponse]))
  )
  @Path(value = "/test-sequence-votes")
  def testSequenceVotes: Route

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

  def routes: Route =
    emptyRoute ~ testSequence ~ testSequenceVotes ~ replayUserRegistrationEvent ~ replayUserHistoryEvent
}

trait MigrationApiComponent {
  def migrationApi: MigrationApi
}

trait DefaultMigrationApiComponent extends MigrationApiComponent with MakeAuthenticationDirectives with StrictLogging {
  this: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent
    with OperationServiceComponent
    with OperationOfQuestionServiceComponent
    with SequenceServiceComponent
    with SequenceConfigurationComponent
    with ProposalServiceComponent
    with UserServiceComponent
    with SecurityConfigurationComponent
    with UserHistoryCoordinatorServiceComponent
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

    def newContext: RequestContext = RequestContext.empty.copy(
      requestId = idGenerator.nextId(),
      sessionId = SessionId(idGenerator.nextId()),
      visitorId = Some(idGenerator.nextVisitorId())
    )

    def createOperation(userId: UserId, slug: String, sequenceSize: Int): Future[OperationOfQuestion] =
      for {
        operationId <- operationService.create(userId, slug, Language("fr"), Seq.empty)
        opOfQuestion <- operationOfQuestionService.create(
          CreateOperationOfQuestion(
            operationId = operationId,
            startDate = None,
            endDate = None,
            operationTitle = slug,
            slug = s"question-$slug",
            country = Country("FR"),
            language = Language("fr"),
            question = s"question-$slug",
            canPropose = false
          )
        )
        config <- sequenceConfigurationService.getSequenceConfiguration(opOfQuestion.landingSequenceId)
        _ <- sequenceConfigurationService.setSequenceConfiguration(
          config.copy(sequenceSize = sequenceSize, newProposalsRatio = 1.0)
        )
      } yield opOfQuestion

    def createProposals(userId: UserId,
                        slug: String,
                        opOfQuestion: OperationOfQuestion,
                        nbProposals: Int,
                        requestContext: RequestContext): Future[immutable.Seq[ProposalId]] = {
      val question = Question(
        questionId = opOfQuestion.questionId,
        slug = s"question-$slug",
        country = Country("FR"),
        language = Language("fr"),
        question = s"question-$slug",
        operationId = Some(opOfQuestion.operationId),
        themeId = None
      )
      val inclusive: immutable.Seq[Int] = 0.to(nbProposals)
      Future.traverse(inclusive) { it =>
        for {
          user <- userService
            .retrieveOrCreateVirtualUser(
              AuthorRequest(None, s"author-$it", None, None, None),
              question.country,
              question.language
            )
          proposalId <- proposalService.propose(
            user,
            RequestContext.empty,
            DateHelper.now(),
            s"Il faut tester le comportement de la séquence $it",
            question,
            initialProposal = false
          )
          _ <- proposalService.validateProposal(
            proposalId = proposalId,
            moderator = userId,
            requestContext = requestContext,
            question = question,
            newContent = None,
            sendNotificationEmail = false,
            idea = None,
            tags = Seq.empty,
            predictedTags = None,
            predictedTagsModelName = None
          )
        } yield proposalId
      }
    }

    private def generateSequencesAndVotes(nbSequences: Int, sequenceId: SequenceId): Future[Int] = {
      val inclusive: immutable.Seq[Int] = 0.to(nbSequences)
      Future
        .traverse(inclusive) { _ =>
          val context = newContext
          sequenceService
            .startNewSequence(None, sequenceId, Seq.empty, None, context)
            .flatMap {
              case None =>
                logger.warn(s"[NewSequence] None $sequenceId, $context")
                Future.successful(0)
              case Some(sequenceResult) =>
                logger.warn(s"[NewSequence] sequenceResult $sequenceId, $context")
                Future
                  .traverse(sequenceResult.proposals) { proposal =>
                    val hash = SecurityHelper.generateProposalKeyHash(
                      proposal.id,
                      newContext.sessionId,
                      newContext.location,
                      securityConfiguration.secureVoteSalt
                    )
                    proposalService.voteProposal(proposal.id, None, context, VoteKey.Agree, Some(hash)).map {
                      case None =>
                        logger.warn(s"[0] voteProposal ${proposal.id} $context")
                        0
                      case Some(_) =>
                        logger.warn(s"[1] voteProposal ${proposal.id} $context")
                        1
                    }
                  }
                  .map(_.sum)
            }
        }
        .map(_.sum)
    }

    override def testSequence: Route = post {
      path("migrations" / "test-sequence") {
        withRequestTimeout(40.seconds) {
          makeOperation("TestSequence") { requestContext =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                decodeRequest {
                  entity(as[TestSequenceRequest]) { request: TestSequenceRequest =>
                    val userId = userAuth.user.userId
                    val slug = request.operationSlug
                    provideAsync(createOperation(userId, slug, request.sequenceSize.getOrElse(20))) { opOfQuestion =>
                      sequenceConfigurationService.reloadConfigurations()
                      provideAsync(
                        createProposals(userId, slug, opOfQuestion, request.nbProposals.getOrElse(30), requestContext)
                      ) { _ =>
                        complete(TestSequenceResponse(opOfQuestion.landingSequenceId, opOfQuestion.questionId))
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def testSequenceVotes: Route = post {
      path("migrations" / "test-sequence-votes") {
        withRequestTimeout(40.seconds) {
          makeOperation("TestSequenceVotes") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                decodeRequest {
                  entity(as[TestSequenceVotesRequest]) { request: TestSequenceVotesRequest =>
                    val userId = userAuth.user.userId
                    provideAsync(generateSequencesAndVotes(request.nbVotedSequences.getOrElse(200), request.sequenceId)) {
                      totalVotes =>
                        provideAsync(
                          proposalService.search(
                            Some(userId),
                            SearchQuery(
                              filters =
                                Some(SearchFilters(question = Some(QuestionSearchFilter(Seq(request.questionId)))))
                            ),
                            RequestContext.empty
                          )
                        ) { proposalSearchResult =>
                          provideAsync(
                            sequenceService
                              .startNewSequence(None, request.sequenceId, Seq.empty, None, newContext)
                          ) { newSequenceResult =>
                            complete(
                              TestSequenceVotesResponse(
                                totalVotes = totalVotes,
                                votedProposals = proposalSearchResult.results,
                                newSequence = newSequenceResult
                              )
                            )
                          }
                        }
                    }
                  }
                }
              }
            }
          }
        }
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
  }

}

final case class TestSequenceRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "random-operation-slug") operationSlug: String,
  @(ApiModelProperty @field)(dataType = "integer", example = "20") sequenceSize: Option[Int],
  @(ApiModelProperty @field)(dataType = "integer", example = "30") nbProposals: Option[Int]
)
object TestSequenceRequest {
  implicit val decoder: Decoder[TestSequenceRequest] = deriveDecoder[TestSequenceRequest]

}

final case class TestSequenceResponse(sequenceId: SequenceId, questionId: QuestionId)
object TestSequenceResponse {
  implicit val encoder: Encoder[TestSequenceResponse] = deriveEncoder[TestSequenceResponse]
}

final case class TestSequenceVotesRequest(
  @(ApiModelProperty @field)(dataType = "string") questionId: QuestionId,
  @(ApiModelProperty @field)(dataType = "string") sequenceId: SequenceId,
  @(ApiModelProperty @field)(dataType = "integer", example = "200") nbVotedSequences: Option[Int]
)
object TestSequenceVotesRequest {
  implicit val decoder: Decoder[TestSequenceVotesRequest] = deriveDecoder[TestSequenceVotesRequest]

}

final case class TestSequenceVotesResponse(totalVotes: Int,
                                           votedProposals: Seq[IndexedProposal],
                                           newSequence: Option[SequenceResult])
object TestSequenceVotesResponse {
  implicit val encoder: Encoder[TestSequenceVotesResponse] = deriveEncoder[TestSequenceVotesResponse]
}
