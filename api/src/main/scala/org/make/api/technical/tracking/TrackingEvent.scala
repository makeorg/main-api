package org.make.api.technical.tracking

import java.time.ZonedDateTime

import org.make.api.technical.tracking.TrackingEvent.AnyTrackingEvent
import org.make.core.{DateHelper, EventWrapper, RequestContext}
import shapeless.{:+:, CNil}

final case class TrackingEvent(eventProvider: String,
                               eventType: Option[String],
                               eventName: Option[String],
                               eventParameters: Option[Map[String, String]],
                               requestContext: RequestContext,
                               createdAt: ZonedDateTime)

final case class TrackingEventWrapper(version: Int,
                                      id: String,
                                      date: ZonedDateTime,
                                      eventType: String,
                                      event: AnyTrackingEvent)
    extends EventWrapper

object TrackingEvent {
  type AnyTrackingEvent = TrackingEvent :+: CNil

  def eventfromFront(frontRequest: FrontTrackingRequest, requestContext: RequestContext): TrackingEvent = {
    TrackingEvent(
      eventProvider = "front",
      eventType = Some(frontRequest.eventType),
      eventName = frontRequest.eventName,
      eventParameters = frontRequest.eventParameters,
      requestContext = requestContext,
      createdAt = DateHelper.now()
    )
  }
}
