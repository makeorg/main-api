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

import java.time.ZonedDateTime

import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations.{Authorization, _}
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.{MailJetConfigurationComponent, MakeSettingsComponent}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.crm.CrmServiceComponent
import org.make.api.technical.storage.StorageConfigurationComponent
import org.make.api.user.UserServiceComponent
import org.make.api.userhistory.UserUploadAvatarEvent
import org.make.core.tag.{Tag => _}
import org.make.core.{CirceFormatters, DateHelper, HttpCodes, Validation}

import scala.annotation.meta.field
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import akka.stream.scaladsl.Sink
import org.make.api.technical.crm.QuestionResolver
import org.make.api.question.SearchQuestionRequest
import org.make.api.operation.OperationServiceComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.userhistory.UserHistoryEvent
import org.make.core.user.User
import org.make.core.question.Question
import org.make.api.proposal.ProposalServiceComponent
import org.make.api.userhistory.LogUserVoteEvent
import org.make.api.userhistory.LogUserUnvoteEvent
import org.make.api.userhistory.LogUserQualificationEvent
import org.make.api.userhistory.LogUserUnqualificationEvent
import org.make.api.userhistory.LogUserProposalEvent
import akka.stream.scaladsl.Source
import org.make.core.profile.Profile
import org.make.core.RequestContext
import akka.persistence.query.EventEnvelope
import org.make.core.user.UserId
import org.make.api.userhistory.LogRegisterCitizenEvent

@Api(value = "Migrations")
@Path(value = "/migrations")
trait MigrationApi extends Directives {

  def emptyRoute: Route

  @ApiOperation(
    value = "set-proper-signup-operation",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "NoContent")))
  @Path(value = "/set-proper-signup-operation")
  def setProperSignUpOperation: Route

  @ApiOperation(
    value = "upload-all-avatars",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "NoContent")))
  @Path(value = "/upload-all-avatars")
  def uploadAllAvatars: Route

  def routes: Route = setProperSignUpOperation ~ uploadAllAvatars
}

trait MigrationApiComponent {
  def migrationApi: MigrationApi
}

trait DefaultMigrationApiComponent extends MigrationApiComponent with MakeAuthenticationDirectives with StrictLogging {
  this: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with ActorSystemComponent
    with SessionHistoryCoordinatorServiceComponent
    with CrmServiceComponent
    with MailJetConfigurationComponent
    with UserServiceComponent
    with ReadJournalComponent
    with OperationServiceComponent
    with QuestionServiceComponent
    with EventBusServiceComponent
    with StorageConfigurationComponent
    with ProposalServiceComponent =>

  override lazy val migrationApi: MigrationApi = new DefaultMigrationApi

  implicit private lazy val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)

  class DefaultMigrationApi extends MigrationApi {
    override def emptyRoute: Route =
      get {
        path("migrations") {
          complete(StatusCodes.OK)
        }
      }

    private def createQuestionResolver(): Future[QuestionResolver] = {
      val operationsAsMap = operationService
        .findSimple()
        .map(_.map(operation => operation.slug -> operation.operationId).toMap)
      for {
        questions  <- questionService.searchQuestion(SearchQuestionRequest())
        operations <- operationsAsMap
      } yield new QuestionResolver(questions, operations)
    }

    private def retrieveEventQuestion(
      resolver: QuestionResolver,
      event: UserHistoryEvent[_]
    ): Future[Option[Question]] = {
      resolver.extractQuestionWithOperationFromRequestContext(event.requestContext) match {
        case Some(question) => Future.successful(Some(question))
        case None =>
          event match {
            case e: LogUserVoteEvent =>
              proposalService.resolveQuestionFromVoteEvent(resolver, e.requestContext, e.action.arguments.proposalId)
            case e: LogUserUnvoteEvent =>
              proposalService.resolveQuestionFromVoteEvent(resolver, e.requestContext, e.action.arguments.proposalId)
            case e: LogUserQualificationEvent =>
              proposalService.resolveQuestionFromVoteEvent(resolver, e.requestContext, e.action.arguments.proposalId)
            case e: LogUserUnqualificationEvent =>
              proposalService.resolveQuestionFromVoteEvent(resolver, e.requestContext, e.action.arguments.proposalId)
            case e: LogUserProposalEvent =>
              proposalService.resolveQuestionFromUserProposal(resolver, e.requestContext, e.userId, e.action.date)
            case e => Future.successful(resolver.extractQuestionWithOperationFromRequestContext(e.requestContext))
          }
      }

    }

    private def resolveQuestionFromEvents(
      questionResolver: QuestionResolver,
      events: Seq[EventEnvelope]
    ): Future[Option[Question]] = {
      val maybeRegistration: Option[LogRegisterCitizenEvent] = events
        .map(_.event)
        .collectFirst {
          case e: LogRegisterCitizenEvent => e
        }

      maybeRegistration match {
        case None => Future.successful(None)
        case Some(userRegisteredEvent) =>
          questionResolver
            .extractQuestionWithOperationFromRequestContext(userRegisteredEvent.requestContext)
            .map(question => Future.successful(Some(question)))
            .getOrElse {
              val registerSessionEvents = events
                .map(_.event.asInstanceOf[UserHistoryEvent[_]])
                .filter(_.requestContext.sessionId == userRegisteredEvent.requestContext.sessionId)
                .sortBy(_.action.date)

              Source(registerSessionEvents)
                .mapAsync(1)(retrieveEventQuestion(questionResolver, _))
                .collect {
                  case Some(question) => question
                }
                .runWith(Sink.headOption[Question])
            }
      }
    }

    private def listEvents(userId: UserId) = {
      userJournal
        .currentEventsByPersistenceId(userId.value, 0L, Long.MaxValue)
        .runWith(Sink.seq)
    }

    private def updateUserIfNeeded(user: User, maybeQuestion: Option[Question]): Future[Int] = {
      val newProfile =
        user.profile.orElse(Profile.parseProfile()).map(_.copy(registerQuestionId = maybeQuestion.map(_.questionId)))

      if (newProfile == user.profile) {
        Future.successful(0)
      } else {
        userService
          .update(user.copy(profile = newProfile), RequestContext.empty)
          .map(_ => 1)
      }
    }

    override def setProperSignUpOperation: Route = post {
      path("migrations" / "set-proper-signup-operation") {
        withoutRequestTimeout {
          makeOperation("RepairSignupOperation") { _ =>
            makeOAuth2 { userAuth =>
              requireAdminRole(userAuth.user) {
                provideAsync(createQuestionResolver()) { questionResolver =>
                  StreamUtils
                    .asyncPageToPageSource(
                      userService.findUsersForCrmSynchro(None, None, _, mailJetConfiguration.userListBatchSize)
                    )
                    .mapConcat(identity)
                    .mapAsync(1) { user =>
                      val updatedUserCount = for {
                        events        <- listEvents(user.userId)
                        maybeQuestion <- resolveQuestionFromEvents(questionResolver, events)
                        count         <- updateUserIfNeeded(user, maybeQuestion)
                      } yield count

                      updatedUserCount.recover {
                        case e =>
                          logger.error(
                            s"Error when correcting the register question for user ${user.userId.value}, he hasn't been changed.",
                            e
                          )
                          0
                      }
                    }
                    .runFold(0)(_ + _)
                    .onComplete {
                      case Success(results) => logger.info(s"$results user accounts have been updated")
                      case Failure(e)       => logger.error("error when updating register question of all users", e)
                    }
                  complete(StatusCodes.Accepted)
                }
              }
            }
          }
        }
      }
    }

    override def uploadAllAvatars: Route = post {
      path("migrations" / "upload-all-avatars") {
        makeOperation("UploadAllAvatars") { requestContext =>
          makeOAuth2 { userAuth =>
            requireAdminRole(userAuth.user) {
              val batchSize: Int = 1000
              StreamUtils
                .asyncPageToPageSource(userService.findUsersForCrmSynchro(None, None, _, batchSize))
                .mapConcat(identity)
                .filter { user =>
                  user.profile.flatMap(_.avatarUrl).isDefined && user.profile
                    .flatMap(_.avatarUrl)
                    .exists(url => Try(Uri(url)).isSuccess && !url.startsWith(storageConfiguration.baseUrl))
                }
                .map { user =>
                  val avatarUrl = user.profile.flatMap(_.avatarUrl).get
                  val largeAvatarUrl = avatarUrl match {
                    case url if url.startsWith("https://graph.facebook.com/v3.0/") => s"$url?width=512&height=512"
                    case url if url.contains("google")                             => url.replace("s96-c", "s512-c")
                    case _                                                         => avatarUrl
                  }
                  UserUploadAvatarEvent(
                    connectedUserId = Some(userAuth.user.userId),
                    userId = user.userId,
                    country = user.country,
                    language = user.language,
                    requestContext = requestContext,
                    avatarUrl = largeAvatarUrl,
                    eventDate = DateHelper.now()
                  )
                }
                .runForeach(eventBusService.publish)
              complete(StatusCodes.NoContent)
            }
          }
        }

      }
    }
  }

}

final case class DeleteContactsRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "2019-07-11T11:21:40.508Z") maxUpdatedAtBeforeDelete: ZonedDateTime,
  @(ApiModelProperty @field)(dataType = "boolean") deleteEmptyProperties: Boolean
) {
  Validation.validate(
    Validation.validateField(
      "maxUpdatedAtBeforeDelete",
      "invalid_date",
      maxUpdatedAtBeforeDelete.isBefore(DateHelper.now().minusDays(1)),
      "DeleteFor cannot be set to a date more recent than yesterday."
    )
  )
}

object DeleteContactsRequest extends CirceFormatters {
  implicit val decoder: Decoder[DeleteContactsRequest] = deriveDecoder[DeleteContactsRequest]

}
