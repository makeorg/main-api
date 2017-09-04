package org.make.api.vote

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.{ProducerActor, ProducerActorCompanion}
import org.make.core.DateHelper
import org.make.core.vote.VoteEvent
import org.make.core.vote.VoteEvent.VoteEventWrapper

class VoteProducerActor extends ProducerActor {
  override protected lazy val eventClass: Class[VoteEvent] = classOf[VoteEvent]
  override protected lazy val format: RecordFormat[VoteEventWrapper] = RecordFormat[VoteEventWrapper]
  override protected lazy val schema: SchemaFor[VoteEventWrapper] = SchemaFor[VoteEventWrapper]

  val kafkaTopic: String =
    kafkaConfiguration.topics(VoteProducerActor.topicKey)

  override def receive: Receive = {

    case event: VoteEvent =>
      log.debug(s"Received event $event")

      val record = format.to(
        VoteEventWrapper(
          version = 1,
          id = event.id.value,
          date = DateHelper.now(),
          eventType = event.getClass.getSimpleName,
          event = VoteEventWrapper.wrapEvent(event)
        )
      )
      sendRecord(kafkaTopic, event.id.value, record)

    case other => log.info(s"Unknown event $other")
  }
}

object VoteProducerActor extends ProducerActorCompanion {
  val props: Props = Props(new VoteProducerActor)
  val name: String = "kafka-votes-event-writer"
  val topicKey = "votes"
}
