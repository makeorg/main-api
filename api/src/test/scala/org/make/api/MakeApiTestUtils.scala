package org.make.api

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.ExecutionDirectives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.make.api.technical.{IdGeneratorComponent, MakeDirectives}

trait MakeApiTestUtils extends MakeUnitTest with ScalatestRouteTest with MakeDirectives { this: IdGeneratorComponent =>

  def sealRoute(route: Route): Route =
    ExecutionDirectives.handleExceptions(MakeApi.exceptionHandler)(
      handleRejections(MakeApi.rejectionHandler)(Route.seal(route))
    )
}
