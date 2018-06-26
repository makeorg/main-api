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
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.tag.DefaultTagMigrationServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.HttpCodes
import org.make.core.auth.UserRights
import org.make.core.tag.{Tag => _}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Path("/migrations")
@Api(value = "Migrations")
trait MigrationApi extends MakeAuthenticationDirectives with StrictLogging {
  self: DefaultTagMigrationServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent =>

  @ApiOperation(value = "migrate-tags", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @Path(value = "/migrate-tags")
  @Deprecated
  def migrationTagsNewModel: Route = {
    post {
      path("migrations" / "migrate-tags") {
        makeOperation("MigrateTags") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireModerationRole(userAuth.user) {
              println("MIGRATION BEGINS")
              val result: Future[Done] =
                tagMigrationService.createTagsByTheme.flatMap { tagsByTheme =>
                  tagMigrationService.createTagsByOperation.flatMap { tagsByOperation =>
                    tagMigrationService.migrateTags(tagsByTheme, tagsByOperation, userAuth.user.userId, requestContext)
                  }
                }
              provideAsync(result) { _ =>
                complete(StatusCodes.NoContent)
              }
            }
          }
        }
      }
    }
  }

  val migrationRoutes: Route = migrationTagsNewModel
}
