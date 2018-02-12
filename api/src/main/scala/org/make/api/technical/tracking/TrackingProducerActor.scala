package org.make.api.technical.tracking

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.BasicProducerActor
import org.make.api.technical.tracking.TrackingEvent.AnyTrackingEvent
import org.make.core.MakeSerializable
import shapeless.Coproduct

class TrackingProducerActor extends BasicProducerActor[TrackingEventWrapper, TrackingEvent] {
  override protected lazy val eventClass: Class[TrackingEvent] = classOf[TrackingEvent]
  override protected lazy val format: RecordFormat[TrackingEventWrapper] = RecordFormat[TrackingEventWrapper]
  override protected lazy val schema: SchemaFor[TrackingEventWrapper] = SchemaFor[TrackingEventWrapper]
  override val kafkaTopic: String = kafkaConfiguration.topics(TrackingProducerActor.topicKey)
  override protected def convert(event: TrackingEvent): TrackingEventWrapper = {
    TrackingEventWrapper(
      version = MakeSerializable.V1,
      id = event.requestContext.sessionId.value,
      date = event.createdAt,
      eventType = "TrackingEvent",
      event = Coproduct[AnyTrackingEvent](event)
    )
  }
}

object TrackingProducerActor {
  val name: String = "tracking-event-producer"
  val props: Props = Props[TrackingProducerActor]
  val topicKey: String = "tracking-events"
}
