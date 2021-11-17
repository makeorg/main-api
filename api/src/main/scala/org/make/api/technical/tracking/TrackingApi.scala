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
import grizzled.slf4j.Logging
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser._
import io.swagger.annotations._
import org.make.api.demographics.{ActiveDemographicsCardServiceComponent, DemographicsCardServiceComponent}
import org.make.api.question.QuestionServiceComponent
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.monitoring.{MonitoringServiceComponent, MonitoringUtils}
import org.make.api.technical.{EndpointType, EventBusServiceComponent, MakeAuthenticationDirectives, MakeDirectives}
import org.make.core._
import org.make.core.auth.UserRights
import org.make.core.demographics.{DemographicsCard, DemographicsCardId, LabelValue}
import org.make.core.question.QuestionId
import org.make.core.reference.Country
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

  @ApiOperation(value = "track-demographics-v2", httpMethod = "POST", code = HttpCodes.NoContent)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.technical.tracking.DemographicsV2TrackingRequest"
      )
    )
  )
  @Path(value = "/demographics-v2")
  def trackDemographicsV2: Route

  @ApiOperation(value = "track-concertation", httpMethod = "POST", code = HttpCodes.NoContent)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.technical.tracking.ConcertationTrackingRequest"
      )
    )
  )
  @Path(value = "/concertation")
  def trackConcertation: Route

  final def routes: Route =
    backofficeLogs ~ frontTracking ~ trackFrontPerformances ~ trackDemographicsV2 ~ trackConcertation
}

trait TrackingApiComponent {
  def trackingApi: TrackingApi
}

trait DefaultTrackingApiComponent extends TrackingApiComponent with MakeDirectives {
  this: MakeDirectivesDependencies
    with ActiveDemographicsCardServiceComponent
    with DemographicsCardServiceComponent
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
                eventBusService.publish(request.toEvent(requestContext = requestContext))
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

    def trackDemographicsV2: Route = {
      post {
        path("tracking" / "demographics-v2") {
          makeOperation("DemographicsTrackingV2", EndpointType.Public) { requestContext: RequestContext =>
            decodeRequest {
              entity(as[DemographicsV2TrackingRequest]) { request =>
                val futureQuestion = questionService.getQuestion(request.questionId)
                val futureCard = demographicsCardService.get(request.demographicsCardId)
                val futureActiveCards = activeDemographicsCardService.list(
                  questionId = Some(request.questionId),
                  cardId = Some(request.demographicsCardId)
                )
                val questionNotFoundError = ValidationError(
                  "questionId",
                  "not_found",
                  Some(s"Question ${request.questionId.value} doesn't exist")
                )
                val cardNotFoundError = ValidationError(
                  "demographicsCardId",
                  "not_found",
                  Some(s"DemographicsCard ${request.demographicsCardId.value} doesn't exist")
                )
                provideAsyncOrBadRequest(futureQuestion, questionNotFoundError) { question =>
                  provideAsyncOrBadRequest(futureCard, cardNotFoundError) { card =>
                    provideAsync(futureActiveCards) { activeCards =>
                      Validation.validate(
                        Seq(
                          Validation.validateField(
                            "token",
                            "invalid_value",
                            demographicsCardService
                              .isTokenValid(request.token, request.demographicsCardId, request.questionId),
                            s"Invalid token. Token might be expired or contain invalid value"
                          ),
                          Validation.validateField(
                            "demographicsCardId",
                            "invalid_value",
                            activeCards.nonEmpty,
                            s"Demographics card ${request.demographicsCardId} is not active for question ${request.questionId}"
                          ),
                          Validation.validateField(
                            "country",
                            "invalid_value",
                            question.countries.exists(_ == request.country),
                            s"Country ${request.country} is not defined in question ${request.questionId}"
                          )
                        ) ++
                          request.value.map { value =>
                            Validation.validateField(
                              "value",
                              "invalid_value",
                              parse(card.parameters).exists(_.as[Seq[LabelValue]] match {
                                case Right(values) => values.exists(_.value == value)
                                case Left(_)       => false
                              }),
                              s"Provided value is not defined in demographics card ${request.demographicsCardId}"
                            )
                          }: _*
                      )
                      eventBusService.publish(request.toEvent(requestContext.applicationName, card.dataType))
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

    def trackConcertation: Route = {
      post {
        path("tracking" / "concertation") {
          val trackingName = "ConcertationTracking"
          makeOperationForConcertation(trackingName) { origin =>
            decodeRequest {
              entity(as[ConcertationTrackingRequest]) { request =>
                val context =
                  RequestContext.empty.copy(
                    applicationName = Some(ApplicationName.Concertation),
                    location = Some(request.context.location)
                  )
                MonitoringUtils.logRequest(trackingName, context, origin)
                eventBusService.publish(request.toEvent(context.applicationName))
                complete(StatusCodes.NoContent)
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
) {
  def toEvent(requestContext: RequestContext): TrackingEvent = {
    TrackingEvent(
      eventProvider = "front",
      eventType = Some(eventType),
      eventName = eventName,
      eventParameters = eventParameters,
      requestContext = requestContext,
      createdAt = DateHelper.now()
    )
  }

}

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

final case class ConcertationTrackingRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "homepage-display")
  eventName: String,
  context: ConcertationContext,
  @(ApiModelProperty @field)(dataType = "map[string]")
  parameters: Map[String, String]
) {
  def toEvent(applicationName: Option[ApplicationName]): ConcertationEvent =
    ConcertationEvent(eventName = eventName, context = context, parameters = parameters)
}

object ConcertationTrackingRequest {
  implicit val decoder: Decoder[ConcertationTrackingRequest] = deriveDecoder[ConcertationTrackingRequest]
}

final case class DemographicsV2TrackingRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "eeda4303-8c57-4b56-aabe-d52185dca857")
  demographicsCardId: DemographicsCardId,
  @(ApiModelProperty @field)(dataType = "string", example = "18-24")
  value: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "2b228613-10b9-40ec-94b2-50da258a3b4d")
  questionId: QuestionId,
  @(ApiModelProperty @field)(dataType = "string", example = "core")
  source: String,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Country,
  @(ApiModelProperty @field)(dataType = "map[string]")
  parameters: Map[String, String],
  @(ApiModelProperty @field)(dataType = "string", example = "yFUMk1+KSVeFh0NQnTXrdA==")
  token: String
) {
  def toEvent(applicationName: Option[ApplicationName], cardDataType: String): DemographicEvent =
    DemographicEvent(
      demographic = cardDataType,
      value = value.getOrElse(DemographicsCard.SKIPPED),
      questionId = questionId,
      source = source,
      country = country,
      applicationName = applicationName,
      parameters = parameters,
      autoSubmit = false
    )

}

object DemographicsV2TrackingRequest {
  implicit val decoder: Decoder[DemographicsV2TrackingRequest] = deriveDecoder[DemographicsV2TrackingRequest]
}
