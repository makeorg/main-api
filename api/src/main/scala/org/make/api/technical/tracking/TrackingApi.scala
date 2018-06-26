/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

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
