package org.make.api.proposal

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.ProducerActor

class DuplicateDetectorProducerActor extends ProducerActor {

  override protected val eventClass: Class[PredictDuplicate] = classOf[PredictDuplicate]
  override protected val format: RecordFormat[PredictDuplicate] = RecordFormat[PredictDuplicate]
  override protected val schema: SchemaFor[PredictDuplicate] = SchemaFor[PredictDuplicate]

  val kafkaTopic: String = kafkaConfiguration.topics(DuplicateDetectorProducerActor.topicKey)

  override def receive: Receive = {
    case event: PredictDuplicate => onPredictedDuplicate(event)
    case other                   => log.warning(s"Unknown event $other")
  }

  private def onPredictedDuplicate(event: PredictDuplicate): Unit = {
    log.debug(s"Received event $event")
    val record = format.to(event)
    sendRecord(kafkaTopic, record.get("proposalId").asInstanceOf[String], record)
  }
}

object DuplicateDetectorProducerActor {
  val name: String = "duplicate-detector-producer"
  val props: Props = Props[DuplicateDetectorProducerActor]
  val topicKey: String = "duplicates-predicted"
}
