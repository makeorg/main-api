package org.make.api

import akka.http.scaladsl.server.{Directives, Route}
import de.knutwalker.akka.http.support.CirceHttpSupport
import org.make.core.CirceFormatters
import buildinfo.BuildInfo

import io.circe.generic.auto._

trait BuildInfoRoutes extends Directives with CirceHttpSupport with CirceFormatters {

  val buildRoutes: Route = buildInfo

  def buildInfo: Route = get {
    path("version") {
      complete(BuildInformation())
    }
  }

}

case class BuildInformation(name: String = BuildInfo.name,
                            version: String = BuildInfo.version,
                            gitHeadCommit: String = BuildInfo.gitHeadCommit.get,
                            buildTime: String = BuildInfo.buildTime)

