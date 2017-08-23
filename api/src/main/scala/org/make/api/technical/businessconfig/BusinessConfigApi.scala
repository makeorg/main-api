package org.make.api.technical.businessconfig

import javax.ws.rs.Path

import akka.http.scaladsl.server.Route
import io.circe.generic.auto._
import io.swagger.annotations._
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, MakeDirectives, ShortenedNames}
import org.make.core.HttpCodes

@Api(value = "Business Config")
@Path(value = "/business_config")
trait BusinessConfigApi extends MakeDirectives with MakeAuthenticationDirectives with ShortenedNames {
  self: MakeDataHandlerComponent with IdGeneratorComponent =>

  @Path(value = "")
  @ApiOperation(value = "business_config", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[BusinessConfig])))
  def businessConfig: Route =
    get {
      path("business_config") {
        makeTrace("BusinessConfig") { _ =>
          complete(BusinessConfig.default())
        }
      }
    }

  val businessConfigRoutes: Route = businessConfig

}
