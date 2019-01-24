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

package org.make.api.technical.businessconfig

import akka.http.scaladsl.server.{Directives, Route}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, MakeDirectives, ShortenedNames}
import org.make.api.theme.ThemeServiceComponent
import org.make.core.HttpCodes

@Api(value = "Configurations")
@Path(value = "/configurations")
trait ConfigurationsApi extends Directives {

  @Path(value = "front")
  @ApiOperation(value = "front-configuration", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[FrontConfiguration]))
  )
  def businessConfigurationFront: Route

  final lazy val routes: Route = businessConfigurationFront

}

trait ConfigurationsApiComponent {
  def configurationsApi: ConfigurationsApi
}

trait DefaultConfigurationsApiComponent
    extends ConfigurationsApiComponent
    with MakeDirectives
    with MakeAuthenticationDirectives
    with ShortenedNames {
  self: MakeDataHandlerComponent with IdGeneratorComponent with ThemeServiceComponent with MakeSettingsComponent =>

  override lazy val configurationsApi: ConfigurationsApi = new ConfigurationsApi {
    def businessConfigurationFront: Route =
      get {
        path("configurations" / "front") {
          makeOperation("FrontConfiguration") { _ =>
            complete(FrontConfiguration.default(themes = Seq.empty))
          }
        }
      }
  }

}
