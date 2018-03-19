package org.make.api.technical

import javax.ws.rs.Path

import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation.OperationServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.HttpCodes
import org.make.core.operation.SimpleOperation

@Path("/migrations")
@Api(value = "Migrations")
trait MigrationApi extends MakeAuthenticationDirectives with StrictLogging {
  self: OperationServiceComponent
    with ActorSystemComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent =>

  @ApiOperation(value = "get-simple-operations", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[SimpleOperation]]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "slug", paramType = "query", required = false, dataType = "string"))
  )
  @Path(value = "/operations")
  def migrationGetSimpleOperationsBySlug: Route = {
    get {
      path("migrations" / "operations") {
        parameters('slug.?) { (slug) =>
          makeOperation("GetSimpleOperations") { _ =>
            provideAsync(operationService.findSimpleOperation(slug = slug)) { result =>
              complete(result)
            }
          }
        }
      }
    }
  }

  val migrationRoutes: Route = migrationGetSimpleOperationsBySlug
}
