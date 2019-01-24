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
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.core.tag.{Tag => _}

@Path("/migrations")
@Api(value = "Migrations")
trait MigrationApi extends Directives {

  def emptyRoute: Route

  def routes: Route = emptyRoute
}

trait MigrationApiComponent {
  def migrationApi: MigrationApi
}

trait DefaultMigrationApiComponent extends MigrationApiComponent with StrictLogging {

  override lazy val migrationApi: MigrationApi = new MigrationApi {
    override def emptyRoute: Route =
      get {
        path("migrations") {
          complete(StatusCodes.OK)
        }
      }
  }
}
