package org.make.api.technical.tracking

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.BasicProducerActor

class TrackingProducerActor extends BasicProducerActor[TrackingEvent, TrackingEvent] {
  override protected lazy val eventClass: Class[TrackingEvent] = classOf[TrackingEvent]
  override protected lazy val format: RecordFormat[TrackingEvent] = RecordFormat[TrackingEvent]
  override protected lazy val schema: SchemaFor[TrackingEvent] = SchemaFor[TrackingEvent]
  override val kafkaTopic: String = kafkaConfiguration.topics(TrackingProducerActor.topicKey)
  override protected def convert(event: TrackingEvent): TrackingEvent = event
}

object TrackingProducerActor {
  val name: String = "tracking-event-producer"
  val props: Props = Props[TrackingProducerActor]
  val topicKey: String = "tracking-events"
}
