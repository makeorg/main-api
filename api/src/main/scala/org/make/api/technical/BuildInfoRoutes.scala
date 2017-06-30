package org.make.api.technical

import akka.http.scaladsl.server.Route
import buildinfo.BuildInfo
import io.circe.generic.auto._
import org.make.api.technical.auth.MakeDataHandlerComponent

trait BuildInfoRoutes extends MakeDirectives { this: MakeDataHandlerComponent with IdGeneratorComponent =>

  val buildRoutes: Route = buildInfo

  def buildInfo: Route = get {
    path("version") {
      makeTrace("version") {
        complete(BuildInformation())
      }
    }
  }

}

case class BuildInformation(name: String = BuildInfo.name,
                            version: String = BuildInfo.version,
                            gitHeadCommit: String = BuildInfo.gitHeadCommit.get,
                            buildTime: String = BuildInfo.buildTime)
