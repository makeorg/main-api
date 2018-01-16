package org.make.api.technical.tracking

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.ProducerActor

import scala.util.Try

class TrackingProducerActor extends ProducerActor {
  override protected lazy val eventClass: Class[TrackingEvent] = classOf[TrackingEvent]
  override protected lazy val format: RecordFormat[TrackingEvent] = RecordFormat[TrackingEvent]
  override protected lazy val schema: SchemaFor[TrackingEvent] = SchemaFor[TrackingEvent]

  val kafkaTopic: String = kafkaConfiguration.topics(TrackingProducerActor.topicKey)

  override def receive: Receive = {
    case event: TrackingEvent => onEvent(event)
    case other                => log.warning(s"Unknown event $other")
  }

  private def onEvent(event: TrackingEvent) = {
    log.debug(s"Received event $event")
    val record = format.to(event)
    sendRecord(kafkaTopic, record)
  }

  override def postStop(): Unit = {
    Try(producer.close())
  }
}

object TrackingProducerActor {
  val name: String = "tracking-event-producer"
  val props: Props = Props[TrackingProducerActor]

  val topicKey: String = "tracking-events"
}
