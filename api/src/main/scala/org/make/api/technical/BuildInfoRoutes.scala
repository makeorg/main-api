package org.make.api.technical

import akka.http.scaladsl.server.{Directives, Route}
import buildinfo.BuildInfo
import de.knutwalker.akka.http.support.CirceHttpSupport
import io.circe.generic.auto._
import kamon.akka.http.KamonTraceDirectives
import org.make.core.CirceFormatters

trait BuildInfoRoutes
    extends Directives
    with CirceHttpSupport
    with CirceFormatters
    with KamonTraceDirectives {

  val buildRoutes: Route = buildInfo

  def buildInfo: Route = get {
    path("version") {
      traceName("version") {
        complete(BuildInformation())
      }
    }
  }

}

case class BuildInformation(
  name: String = BuildInfo.name,
  version: String = BuildInfo.version,
  gitHeadCommit: String = BuildInfo.gitHeadCommit.get,
  buildTime: String = BuildInfo.buildTime
)
