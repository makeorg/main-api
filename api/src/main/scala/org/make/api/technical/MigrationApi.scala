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
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.migrations
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.user.PersistentUserServiceComponent
import org.make.core.HttpCodes
import org.make.core.auth.UserRights
import org.make.core.tag.{Tag => _}
import org.make.core.user.User
import scalaoauth2.provider.AuthInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Path("/migrations")
@Api(value = "Migrations")
trait MigrationApi extends MakeAuthenticationDirectives with StrictLogging {
  self: PersistentUserServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent =>

  @ApiOperation(value = "update-fb-user-avatar_url", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @Path(value = "/update-fb-avatar-url")
  def updateFbAvatarUrl: Route = {
    post {
      path("migrations" / "update-fb-avatar-url") {
        makeOperation("MigrateTags") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              def updateAllAvatarUrl(fbUsers: Seq[User]): Future[Unit] = {
                migrations
                  .sequentially(fbUsers) { user =>
                    val avatarUrl: String = user.profile
                      .map(profile => s"https://graph.facebook.com/v3.0/${profile.facebookId}/picture")
                      .getOrElse("")
                    persistentUserService.updateAvatarUrl(user.userId, avatarUrl).map(_ => {})
                  }
              }
              val result: Future[Unit] = for {
                fbUsers    <- persistentUserService.findAllUsersWithFbIdNotNull()
                allUpdated <- updateAllAvatarUrl(fbUsers)
              } yield allUpdated
              provideAsync(result) { _ =>
                complete(StatusCodes.OK)
              }
            }
          }
        }
      }
    }
  }

  val migrationRoutes: Route = updateFbAvatarUrl
}
