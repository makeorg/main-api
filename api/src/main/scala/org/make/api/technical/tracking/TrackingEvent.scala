package org.make.api.technical.tracking

import java.time.ZonedDateTime

import org.make.core.{DateHelper, RequestContext}

final case class TrackingEvent(eventProvider: String,
                               eventType: Option[String],
                               eventName: Option[String],
                               eventParameters: Option[Map[String, String]],
                               requestContext: RequestContext,
                               createdAt: ZonedDateTime)

object TrackingEvent {
  def eventfromFront(frontRequest: FrontTrackingRequest, requestContext: RequestContext): TrackingEvent =
    TrackingEvent(
      eventProvider = "front",
      eventType = Some(frontRequest.eventType),
      eventName = frontRequest.eventName,
      eventParameters = frontRequest.eventParameters,
      requestContext = requestContext,
      createdAt = DateHelper.now()
    )
}
