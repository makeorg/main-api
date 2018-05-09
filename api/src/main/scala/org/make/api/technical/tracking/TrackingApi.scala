package org.make.api.technical.tracking

import javax.ws.rs.Path
import io.swagger.annotations._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import io.circe.Decoder
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeAuthentication
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, MakeDirectives}
import org.make.core.{HttpCodes, RequestContext}

@Api(value = "Tracking")
@Path(value = "/tracking")
trait TrackingApi extends MakeDirectives {
  this: EventBusServiceComponent with IdGeneratorComponent with MakeSettingsComponent with MakeAuthentication =>

  @ApiOperation(value = "front-events", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "Ok")))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.technical.tracking.FrontTrackingRequest"
      )
    )
  )
  @Path(value = "/front")
  def frontTracking: Route =
    post {
      path("tracking" / "front") {
        makeOperation("TrackingFront") { requestContext: RequestContext =>
          decodeRequest {
            entity(as[FrontTrackingRequest]) { request: FrontTrackingRequest =>
              eventBusService.publish(
                TrackingEvent.eventfromFront(frontRequest = request, requestContext = requestContext)
              )
              complete(StatusCodes.NoContent)
            }
          }
        }
      }
    }

  val trackingRoutes: Route = frontTracking
}

final case class FrontTrackingRequest(eventType: String,
                                      eventName: Option[String],
                                      eventParameters: Option[Map[String, String]])

object FrontTrackingRequest {
  implicit val decoder: Decoder[FrontTrackingRequest] =
    Decoder.forProduct3("eventType", "eventName", "eventParameters")(FrontTrackingRequest.apply)
}
