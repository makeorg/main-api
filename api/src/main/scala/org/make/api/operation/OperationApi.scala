package org.make.api.operation

import javax.ws.rs.Path

import akka.http.scaladsl.server._
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.HttpCodes
import org.make.core.operation._

@Api(value = "Operation")
@Path(value = "/operations")
trait OperationApi extends MakeAuthenticationDirectives with StrictLogging {
  this: OperationServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with OperationServiceComponent =>

  @ApiOperation(value = "get-operations", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[OperationResponse]]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "slug", paramType = "query", required = false, dataType = "string"))
  )
  @Path(value = "/")
  def getOperations: Route = {
    get {
      path("operations") {
        parameters('slug.?) { (slug) =>
          makeTrace("GetOperations") { requestContext =>
            provideAsync(operationService.find(slug = slug)) { result =>
              complete(result.map(operation => OperationResponse(operation, requestContext.country)))
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "get-operation", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OperationResponse]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "operationId", paramType = "path", dataType = "string")))
  @Path(value = "/{operationId}")
  def getOperation: Route = {
    get {
      path("operations" / operationId) { operationId =>
        makeTrace("GetOperation") { requestContext =>
          provideAsyncOrNotFound(operationService.findOne(operationId)) { operation =>
            complete(OperationResponse(operation, requestContext.country))
          }
        }
      }
    }
  }

  val operationRoutes: Route =
    getOperations ~ getOperation

  val operationId: PathMatcher1[OperationId] = Segment.map(id => OperationId(id))
}
