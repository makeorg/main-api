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

import akka.http.scaladsl.server.Route
import buildinfo.BuildInfo
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}

trait BuildInfoRoutes extends MakeDirectives {
  this: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with MakeAuthentication
    with SessionHistoryCoordinatorServiceComponent =>

  val buildRoutes: Route = buildInfo

  def buildInfo: Route = get {
    path("version") {
      makeOperation("version") { _ =>
        complete(BuildInformation())
      }
    }
  }

}

final case class BuildInformation(
  name: String = BuildInfo.name,
  version: String = BuildInfo.version,
  gitHeadCommit: String = BuildInfo.gitHeadCommit.getOrElse("no commit information"),
  gitBranch: String = BuildInfo.gitCurrentBranch,
  buildTime: String = BuildInfo.buildTime
)

object BuildInformation {
  implicit val encoder: Encoder[BuildInformation] = deriveEncoder[BuildInformation]
}
