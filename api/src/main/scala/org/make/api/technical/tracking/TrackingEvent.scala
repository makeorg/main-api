package org.make.api.technical.tracking

import java.time.ZonedDateTime

import org.make.core.{DateHelper, EventWrapper, MakeSerializable, RequestContext}
import shapeless.{:+:, CNil, Coproduct}

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
                                      event: Coproduct)
    extends EventWrapper

object TrackingEvent {
  type AnyTrackingEvent = TrackingEvent :+: CNil

  def eventfromFront(frontRequest: FrontTrackingRequest, requestContext: RequestContext): TrackingEventWrapper = {
    val trackingEvent = TrackingEvent(
      eventProvider = "front",
      eventType = Some(frontRequest.eventType),
      eventName = frontRequest.eventName,
      eventParameters = frontRequest.eventParameters,
      requestContext = requestContext,
      createdAt = DateHelper.now()
    )

    TrackingEventWrapper(
      version = MakeSerializable.V1,
      id = requestContext.sessionId.value,
      date = trackingEvent.createdAt,
      eventType = "TrackingEvent",
      event = Coproduct[AnyTrackingEvent](trackingEvent)
    )
  }
}
