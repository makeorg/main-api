package org.make.api

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.make.api.technical.{IdGeneratorComponent, MakeDirectives}

trait MakeApiTestUtils extends MakeUnitTest with ScalatestRouteTest with MakeDirectives { this: IdGeneratorComponent =>

  def sealRoute(route: Route): Route =
    Route.seal(MakeApi.makeDefaultHeadersAndHandlers() {
      route
    })
}
