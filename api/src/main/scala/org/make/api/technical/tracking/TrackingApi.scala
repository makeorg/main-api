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
import akka.http.scaladsl.server.{Directives, Route}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeAuthentication
import org.make.api.technical.monitoring.MonitoringServiceComponent
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, MakeDirectives}
import org.make.core.{HttpCodes, RequestContext}

import scala.annotation.meta.field

@Api(value = "Tracking")
@Path(value = "/tracking")
trait TrackingApi extends Directives {

  @ApiOperation(value = "front-events", httpMethod = "POST")
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
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
  def frontTracking: Route

  @ApiOperation(value = "front-performance", httpMethod = "POST")
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.technical.tracking.FrontPerformanceRequest"
      )
    )
  )
  @Path(value = "/performance")
  def trackFrontPerformances: Route

  final lazy val routes: Route = frontTracking ~ trackFrontPerformances
}

trait TrackingApiComponent {
  def trackingApi: TrackingApi
}

trait DefaultTrackingApiComponent extends TrackingApiComponent with MakeDirectives {
  this: EventBusServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with MakeAuthentication
    with MonitoringServiceComponent =>

  override lazy val trackingApi: TrackingApi = new TrackingApi {
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

    def trackFrontPerformances: Route =
      post {
        path("tracking" / "performance") {
          makeOperation("PerformanceTracking") { _ =>
            decodeRequest {
              entity(as[FrontPerformanceRequest]) { request =>
                monitoringService.monitorPerformance(request.applicationName, request.timings)
                complete(StatusCodes.NoContent)
              }
            }
          }
        }
      }

  }
}

final case class FrontTrackingRequest(
  eventType: String,
  eventName: Option[String],
  @(ApiModelProperty @field)(dataType = "map[string]") eventParameters: Option[Map[String, String]]
)

final case class FrontPerformanceRequest(applicationName: String, timings: FrontPerformanceTimings)

object FrontPerformanceRequest {
  implicit val decoder: Decoder[FrontPerformanceRequest] = deriveDecoder[FrontPerformanceRequest]
}

final case class FrontPerformanceTimings(connectStart: Long,
                                         connectEnd: Long,
                                         domComplete: Long,
                                         domContentLoadedEventEnd: Long,
                                         domContentLoadedEventStart: Long,
                                         domInteractive: Long,
                                         domLoading: Long,
                                         domainLookupEnd: Long,
                                         domainLookupStart: Long,
                                         fetchStart: Long,
                                         loadEventEnd: Long,
                                         loadEventStart: Long,
                                         navigationStart: Long,
                                         redirectEnd: Long,
                                         redirectStart: Long,
                                         requestStart: Long,
                                         responseEnd: Long,
                                         responseStart: Long,
                                         secureConnectionStart: Long,
                                         unloadEventEnd: Long,
                                         unloadEventStart: Long)
object FrontPerformanceTimings {
  implicit val decoder: Decoder[FrontPerformanceTimings] = deriveDecoder[FrontPerformanceTimings]
}

object FrontTrackingRequest {
  implicit val decoder: Decoder[FrontTrackingRequest] =
    Decoder.forProduct3("eventType", "eventName", "eventParameters")(FrontTrackingRequest.apply)
}
