package org.make.api.technical

import akka.http.scaladsl.server.Route
import buildinfo.BuildInfo
import io.circe.ObjectEncoder
import io.circe.generic.semiauto.deriveEncoder
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}

trait BuildInfoRoutes extends MakeDirectives {
  this: MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent with MakeAuthentication =>

  val buildRoutes: Route = buildInfo

  def buildInfo: Route = get {
    path("version") {
      makeOperation("version") { _ =>
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
