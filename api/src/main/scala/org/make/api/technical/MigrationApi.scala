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
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.{MailJetConfigurationComponent, MakeSettingsComponent}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.crm.CrmServiceComponent
import org.make.api.user.{
  PersistentUserServiceComponent,
  PersistentUserToAnonymizeServiceComponent,
  UserServiceComponent
}
import org.make.api.userhistory.UserEvent.UserRegisteredEvent
import org.make.core.tag.{Tag => _}
import org.make.core.user.UserId
import org.make.core.{HttpCodes, RequestContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

@Api(value = "Migrations")
@Path(value = "/migrations")
trait MigrationApi extends Directives {

  def emptyRoute: Route

  @ApiOperation(
    value = "replay-social-register-question-id",
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
  @Path(value = "/replay-social-register-question-id")
  def replaySocialRegisterQuestionId: Route

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
    emptyRoute ~ extractAnonUser ~ replaySocialRegisterQuestionId
}

trait MigrationApiComponent {
  def migrationApi: MigrationApi
}

trait DefaultMigrationApiComponent extends MigrationApiComponent with MakeAuthenticationDirectives with StrictLogging {
  this: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent
    with ActorSystemComponent
    with CrmServiceComponent
    with PersistentUserServiceComponent
    with PersistentUserToAnonymizeServiceComponent
    with MailJetConfigurationComponent
    with ReadJournalComponent
    with UserServiceComponent =>

  override lazy val migrationApi: MigrationApi = new MigrationApi {
    override def emptyRoute: Route =
      get {
        path("migrations") {
          complete(StatusCodes.OK)
        }
      }

    override def replaySocialRegisterQuestionId: Route = post {
      path("migrations" / "replay-social-register-question-id") {
        makeOperation("ReplaySocialRegisterQuestionId") { _ =>
          makeOAuth2 { userAuth =>
            requireAdminRole(userAuth.user) {
              implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
              val streamUsers: Future[Done] =
                userJournal
                  .currentPersistenceIds()
                  .flatMapMerge(1, {
                    id =>
                      userJournal
                        .currentEventsByPersistenceId(id, 0, Long.MaxValue)
                        .filter {
                          case EventEnvelope(_, _, _, _: UserRegisteredEvent) => true
                          case _                                              => false
                        }
                        .mapAsync(1) { eventEnvelope =>
                          val event = eventEnvelope.event.asInstanceOf[UserRegisteredEvent]
                          userService
                            .getUser(UserId(id))
                            .map(_.map {
                              case user if user.profile.flatMap(_.registerQuestionId).isDefined =>
                                Future.successful(user)
                              case user =>
                                userService.update(user = user.copy(profile = user.profile.map { p =>
                                  p.copy(
                                    registerQuestionId =
                                      event.registerQuestionId.orElse(event.requestContext.questionId)
                                  )
                                }), requestContext = RequestContext.empty)
                            })
                        }
                  })
                  .runWith(Sink.ignore)
              provideAsync(streamUsers) { _ =>
                complete(StatusCodes.NoContent)
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
