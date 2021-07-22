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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Route, RouteConcatenation}
import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import grizzled.slf4j.Logging
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations._
import org.make.api.question.QuestionServiceComponent
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.monitoring.MonitoringServiceComponent
import org.make.api.technical.{EndpointType, EventBusServiceComponent, MakeAuthenticationDirectives, MakeDirectives}
import org.make.core.auth.UserRights
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.{HttpCodes, RequestContext, ValidationError, Validator}
import org.slf4j.event.Level
import scalaoauth2.provider.AuthInfo

import javax.ws.rs.Path
import scala.annotation.meta.field
import scala.util.Try

@Api(value = "Tracking")
@Path(value = "/tracking")
trait TrackingApi extends RouteConcatenation {

  @ApiOperation(value = "front-events", httpMethod = "POST", code = HttpCodes.NoContent)
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

  @ApiOperation(value = "front-performance", httpMethod = "POST", code = HttpCodes.NoContent)
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

  @ApiOperation(value = "backoffice-logs", httpMethod = "POST", code = HttpCodes.NoContent)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.technical.tracking.BackofficeLogs"
      )
    )
  )
  @Path(value = "/backoffice/logs")
  def backofficeLogs: Route

  @ApiOperation(value = "track-demographics", httpMethod = "POST", code = HttpCodes.NoContent)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.technical.tracking.DemographicsTrackingRequest"
      )
    )
  )
  @Path(value = "/demographics")
  def trackDemographics: Route

  final def routes: Route = backofficeLogs ~ frontTracking ~ trackFrontPerformances ~ trackDemographics
}

trait TrackingApiComponent {
  def trackingApi: TrackingApi
}

trait DefaultTrackingApiComponent extends TrackingApiComponent with MakeDirectives {
  this: MakeDirectivesDependencies
    with EventBusServiceComponent
    with MakeAuthenticationDirectives
    with MonitoringServiceComponent
    with QuestionServiceComponent
    with Logging =>

  override lazy val trackingApi: TrackingApi = new DefaultTrackingApi

  class DefaultTrackingApi extends TrackingApi {

    def backofficeLogs: Route = {
      post {
        path("tracking" / "backoffice" / "logs") {
          makeOperation("BackofficeLogs") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireModerationRole(auth.user) {
                decodeRequest {
                  entity(as[BackofficeLogs]) { request: BackofficeLogs =>
                    (request.level match {
                      case Level.DEBUG => logger.debug(_: String)
                      case Level.ERROR => logger.error(_: String)
                      case Level.INFO  => logger.info(_: String)
                      case Level.TRACE => logger.trace(_: String)
                      case Level.WARN  => logger.warn(_: String)
                    })(request.message)
                    complete(StatusCodes.NoContent)
                  }
                }
              }
            }
          }

        }
      }
    }

    def frontTracking: Route =
      post {
        path("tracking" / "front") {
          makeOperation("TrackingFront", EndpointType.Public) { requestContext: RequestContext =>
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
          makeOperation("PerformanceTracking", EndpointType.Public) { _ =>
            decodeRequest {
              entity(as[FrontPerformanceRequest]) { request =>
                monitoringService.monitorPerformance(request.applicationName, request.timings)
                complete(StatusCodes.NoContent)
              }
            }
          }
        }
      }

    def trackDemographics: Route = {
      post {
        path("tracking" / "demographics") {
          makeOperation("DemographicsTracking", EndpointType.Public) { requestContext: RequestContext =>
            decodeRequest {
              entity(as[DemographicsTrackingRequest]) { request =>
                provideAsyncOrBadRequest(
                  questionService.getQuestion(request.questionId),
                  ValidationError(
                    "questionId",
                    "not_found",
                    Some(s"Question ${request.questionId.value} doesn't exist")
                  )
                ) { _ =>
                  validate(request, DemographicsTrackingRequest.validator) {
                    eventBusService.publish(
                      DemographicEvent.fromDemographicRequest(request, requestContext.applicationName)
                    )
                    complete(StatusCodes.NoContent)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

final case class BackofficeLogs(level: Level, message: String)

object BackofficeLogs {
  implicit val levelDecoder: Decoder[Level] = Decoder[String].emapTry(name => Try(Level.valueOf(name.toUpperCase)))
  implicit val decoder: Decoder[BackofficeLogs] = deriveDecoder
}

final case class FrontTrackingRequest(
  eventType: String,
  eventName: Option[String],
  @(ApiModelProperty @field)(dataType = "map[string]") eventParameters: Option[Map[String, String]]
)

object FrontTrackingRequest {
  implicit val decoder: Decoder[FrontTrackingRequest] =
    Decoder.forProduct3("eventType", "eventName", "eventParameters")(FrontTrackingRequest.apply)
}

final case class FrontPerformanceRequest(applicationName: String, timings: FrontPerformanceTimings)

object FrontPerformanceRequest {
  implicit val decoder: Decoder[FrontPerformanceRequest] = deriveDecoder[FrontPerformanceRequest]
}

final case class FrontPerformanceTimings(
  connectStart: Long,
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
  unloadEventStart: Long
)
object FrontPerformanceTimings {
  implicit val decoder: Decoder[FrontPerformanceTimings] = deriveDecoder[FrontPerformanceTimings]
}

final case class DemographicsTrackingRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "age")
  demographic: String,
  @(ApiModelProperty @field)(dataType = "string", example = "18-24")
  value: String,
  @(ApiModelProperty @field)(dataType = "string", example = "4ee15013-b5a6-415c-8270-da8438766644")
  questionId: QuestionId,
  @(ApiModelProperty @field)(dataType = "string", example = "core")
  source: String,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Country,
  @(ApiModelProperty @field)(dataType = "map[string]")
  parameters: Map[String, String],
  @(ApiModelProperty @field)(dataType = "boolean", example = "false")
  autoSubmit: Boolean
)

object DemographicsTrackingRequest {
  implicit val decoder: Decoder[DemographicsTrackingRequest] = deriveDecoder
  val skipped: String = "SKIPPED"
  val validValues: Map[String, Set[String]] = Map(
    "age" -> Set("8-15", "16-24", "25-34", "35-44", "45-54", "55-64", "65+"),
    "region" -> Set( // see iso 3166-2
      "FR-ARA",
      "FR-BFC",
      "FR-BRE",
      "FR-CVL",
      "FR-COR",
      "FR-GES",
      "FR-HDF",
      "FR-IDF",
      "FR-NOR",
      "FR-NAQ",
      "FR-OCC",
      "FR-PDL",
      "FR-PAC",
      "FR-GUA",
      "FR-GUF",
      "FR-MTQ",
      "FR-LRE",
      "FR-MAY"
    ),
    "gender" -> Set("M", "F", "O")
  )

  val validator: Validator[DemographicsTrackingRequest] = (e: DemographicsTrackingRequest) => {
    validValues.get(e.demographic).map(_.contains(e.value) || e.value == skipped) match {
      case Some(true) => Valid(e)
      case Some(false) =>
        Invalid(
          NonEmptyList.one(
            ValidationError(
              "value",
              "unknown",
              Some(s"${e.value} is not valid for ${e.demographic}, expected one of ${validValues(e.demographic)
                .mkString("'", "', '", "'")}")
            )
          )
        )
      case None =>
        Invalid(
          NonEmptyList.one(
            ValidationError(
              "demographic",
              "unknown",
              Some(s"${e.demographic} is not a known demographic, expected one of ${validValues.keys
                .mkString("'", "', '", "'")}")
            )
          )
        )
    }
  }
}
