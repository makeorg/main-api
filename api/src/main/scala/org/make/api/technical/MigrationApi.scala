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
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import cats.data.OptionT
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation.OperationServiceComponent
import org.make.api.question.{PersistentQuestionServiceComponent, QuestionServiceComponent}
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.user.{PersistentUserServiceComponent, UserServiceComponent}
import org.make.api.userhistory.LogRegisterCitizenEvent
import org.make.core.auth.UserRights
import org.make.core.profile.Profile
import org.make.core.question.Question
import org.make.core.tag.{Tag => _}
import org.make.core.user.User
import org.make.core.{HttpCodes, RequestContext}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Path("/migrations")
@Api(value = "Migrations")
trait MigrationApi extends Directives {

  @Path(value = "/user/attach-register-question")
  @ApiOperation(
    value = "attach-register-question",
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
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Unit])))
  def attachUserRegisterQuestion: Route

  def routes: Route = attachUserRegisterQuestion
}

trait MigrationApiComponent {
  def migrationApi: MigrationApi
}
trait DefaultMigrationApiComponent extends MigrationApiComponent with MakeAuthenticationDirectives with StrictLogging {
  self: OperationServiceComponent
    with PersistentQuestionServiceComponent
    with PersistentUserServiceComponent
    with ReadJournalComponent
    with MakeDataHandlerComponent
    with ActorSystemComponent
    with IdGeneratorComponent
    with QuestionServiceComponent
    with UserServiceComponent
    with MakeSettingsComponent =>

  override lazy val migrationApi: MigrationApi = new MigrationApi {

    def attachUserRegisterQuestion: Route = {
      post {
        path("migrations" / "user" / "attach-register-question") {
          makeOperation("AttachUserRegisterQuestion") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)

                logger.info("Migration : start")

                val source: Future[Done] = userJournal
                  .currentPersistenceIds()
                  .flatMapMerge(1, userJournal.currentEventsByPersistenceId(_, 0, Long.MaxValue))
                  .map(_.event)
                  .filter(_.isInstanceOf[LogRegisterCitizenEvent])
                  .map(_.asInstanceOf[LogRegisterCitizenEvent])
                  .mapAsync(1)(addQuestionIdToUser)
                  .runWith(Sink.ignore)

                onComplete(source) { _ =>
                  complete(StatusCodes.NoContent)
                }
              }
            }
          }
        }
      }
    }
  }

  private def addQuestionIdToUser(event: LogRegisterCitizenEvent): Future[Option[Unit]] = {
    val requestContext: RequestContext = event.requestContext

    logger.info(s"Migration (${event.userId.value}) : Begin")
    logger.info(
      s"Migration (${event.userId.value}) : operationId in event is ${requestContext.operationId.map(_.value).getOrElse("None")}"
    )
    logger.info(s"Migration (${event.userId.value}) : country in event is ${event.action.arguments.country.value}")
    logger.info(s"Migration (${event.userId.value}) : language in event is ${event.action.arguments.language.value}")

    val futureMaybeQuestion: Future[Option[Question]] = questionService.findQuestionByQuestionIdOrThemeOrOperation(
      None,
      requestContext.currentTheme,
      requestContext.operationId,
      event.action.arguments.country,
      event.action.arguments.language
    )

    futureMaybeQuestion.map {
      case Some(_) => logger.info(s"Migration (${event.userId.value}) : question found")
      case _       => logger.error(s"Migration (${event.userId.value}) : question not found")
    }

    val futureMaybeUser: Future[Option[User]] = (for {
      question <- OptionT(futureMaybeQuestion)
      user     <- OptionT(userService.getUser(event.userId))
    } yield {
      logger.info(s"Migration (${event.userId.value}) : registerQuestionId: '${question.questionId.value}'")
      val newProfile: Profile = user.profile match {
        case None          => Profile.default.copy(registerQuestionId = Some(question.questionId))
        case Some(profile) => profile.copy(registerQuestionId = Some(question.questionId))
      }

      user.copy(profile = Some(newProfile))

    }).value

    (for {
      user          <- OptionT(futureMaybeUser)
      persistedUser <- OptionT(persistentUserService.updateUser(user).map[Option[User]](Some(_)))
    } yield {
      val questionId: String = persistedUser.profile.flatMap(_.registerQuestionId.map(_.value)).getOrElse("")
      logger.info(
        s"Migration (${event.userId.value}) : user ${persistedUser.userId.value} persisted with registerQuestionId ${questionId}"
      )
    }).value
  }
}
