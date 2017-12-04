package org.make.api.technical

import akka.http.scaladsl.server.Route
import buildinfo.BuildInfo
import io.circe.ObjectEncoder
import io.circe.generic.semiauto.deriveEncoder
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent

trait BuildInfoRoutes extends MakeDirectives {
  this: MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  val buildRoutes: Route = buildInfo

  def buildInfo: Route = get {
    path("version") {
      makeTrace("version") { _ =>
        complete(BuildInformation())
      }
    }
  }

}

case class BuildInformation(name: String = BuildInfo.name,
                            version: String = BuildInfo.version,
                            gitHeadCommit: String = BuildInfo.gitHeadCommit.get,
                            gitBranch: String = BuildInfo.gitCurrentBranch,
                            buildTime: String = BuildInfo.buildTime)

object BuildInformation {
  implicit val encoder: ObjectEncoder[BuildInformation] = deriveEncoder[BuildInformation]
}
