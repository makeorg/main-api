package org.make.api.proposal

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.BasicProducerActor

class DuplicateDetectorProducerActor extends BasicProducerActor[PredictDuplicate, PredictDuplicate] {
  override protected lazy val eventClass: Class[PredictDuplicate] = classOf[PredictDuplicate]
  override protected lazy val format: RecordFormat[PredictDuplicate] = RecordFormat[PredictDuplicate]
  override protected lazy val schema: SchemaFor[PredictDuplicate] = SchemaFor[PredictDuplicate]
  override val kafkaTopic: String = kafkaConfiguration.topics(DuplicateDetectorProducerActor.topicKey)
  override protected def convert(event: PredictDuplicate): PredictDuplicate = event
}

object DuplicateDetectorProducerActor {
  val name: String = "duplicate-detector-producer"
  val props: Props = Props[DuplicateDetectorProducerActor]
  val topicKey: String = "duplicates-predicted"
}
