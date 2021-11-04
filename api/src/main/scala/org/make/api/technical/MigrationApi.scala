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
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.user.UserServiceComponent
import org.make.api.userhistory.UserHistoryCoordinatorServiceComponent
import org.make.core._
import org.make.core.technical.Pagination.{End, Start}

import javax.ws.rs.Path
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Api(value = "Migrations")
@Path(value = "/migrations")
trait MigrationApi extends Directives {

  def emptyRoute: Route

  @ApiOperation(
    value = "modify-avatars-swift-path",
    httpMethod = "POST",
    code = HttpCodes.NoContent,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "dry", paramType = "query", dataType = "boolean", required = true))
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
  @Path(value = "/modify-avatars-swift-path")
  def modifyAvatarsSwiftPath: Route

  def routes: Route = modifyAvatarsSwiftPath
}

trait MigrationApiComponent {
  def migrationApi: MigrationApi
}

trait DefaultMigrationApiComponent extends MigrationApiComponent with MakeAuthenticationDirectives with Logging {
  this: MakeDirectivesDependencies
    with ActorSystemComponent
    with ReadJournalComponent
    with UserServiceComponent
    with UserHistoryCoordinatorServiceComponent =>

  override lazy val migrationApi: MigrationApi = new DefaultMigrationApi

  class DefaultMigrationApi extends MigrationApi {
    override def emptyRoute: Route =
      get {
        path("migrations") {
          complete(StatusCodes.OK)
        }
      }

    override def modifyAvatarsSwiftPath: Route = post {
      path("migrations" / "modify-avatars-swift-path") {
        makeOperation("ModifyAvatarsSwiftPath") { _ =>
          makeOAuth2 { userAuth =>
            requireAdminRole(userAuth.user) {
              parameter("dry".as[Boolean]) { dry =>
                val batchSize: Int = 1000
                val startTime = System.currentTimeMillis()
                val eventDate = DateHelper.now()
                StreamUtils
                  .asyncPageToPageSource(
                    offset =>
                      userService.adminFindUsers(
                        Start(offset),
                        Some(End(offset + batchSize)),
                        None,
                        None,
                        None,
                        None,
                        None,
                        None,
                        None,
                        None
                      )
                  )
                  .map { users =>
                    users.map(user => user.userId -> user.profile.flatMap(_.avatarUrl))
                  }
                  .mapConcat(identity)
                  .collect { case (id, Some(avatarUrl)) => id -> avatarUrl }
                  .mapAsync(5) {
                    case (id, avatarUrl) =>
                      if (dry) {
                        Future.successful(1)
                      } else {
                        userService
                          .changeAvatarForUser(id, avatarUrl, RequestContext.empty, eventDate)
                          .map(_ => 1)
                      }
                  }
                  .runFold(0)(_ + _)
                  .onComplete {
                    case Success(count) =>
                      val time = System.currentTimeMillis() - startTime
                      logger.info(s"$count users have had their avatars updated in $time ms")
                    case Failure(exception) =>
                      logger.error("Error while updating avatars path:", exception)
                  }

                complete(StatusCodes.NoContent)
              }
            }
          }
        }
      }
    }

  }
}
