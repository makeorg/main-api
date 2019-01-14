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

package org.make.api.technical.healthcheck
import akka.http.scaladsl.server.Route
import io.swagger.annotations.{Api, _}
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.HttpCodes

@Api(value = "Health Check")
@Path(value = "/")
trait HealthCheckApi extends MakeAuthenticationDirectives {
  this: MakeSettingsComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with HealthCheckServiceComponent =>

  @ApiOperation(value = "healthcheck", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Map[String, String]]))
  )
  @Path(value = "/healthcheck")
  def healthCheck: Route = get {
    path("healthcheck") {
      makeOperation("HealthCheck") { _ =>
        provideAsync(healthCheckService.runAllHealthChecks())(result => complete(result))
      }
    }
  }

  val healthCheckRoutes: Route = healthCheck
}
