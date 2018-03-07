package org.make.api.semantic

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.semantic.PredictDuplicateEvent.AnyPredictDuplicateEventEvent
import org.make.api.technical.BasicProducerActor
import org.make.core.{DateHelper, MakeSerializable}
import shapeless.Coproduct

class SemanticProducerActor extends BasicProducerActor[PredictDuplicateEventWrapper, PredictDuplicateEvent] {
  override protected lazy val eventClass: Class[PredictDuplicateEvent] = classOf[PredictDuplicateEvent]
  override protected lazy val format: RecordFormat[PredictDuplicateEventWrapper] =
    RecordFormat[PredictDuplicateEventWrapper]
  override protected lazy val schema: SchemaFor[PredictDuplicateEventWrapper] = SchemaFor[PredictDuplicateEventWrapper]
  override val kafkaTopic: String = kafkaConfiguration.topics(SemanticProducerActor.topicKey)
  override protected def convert(trackingEvent: PredictDuplicateEvent): PredictDuplicateEventWrapper =
    PredictDuplicateEventWrapper(
      version = MakeSerializable.V1,
      id = trackingEvent.proposalId.value,
      date = DateHelper.now(),
      eventType = trackingEvent.getClass.getSimpleName,
      event = Coproduct[AnyPredictDuplicateEventEvent](trackingEvent)
    )
}

object SemanticProducerActor {
  val name: String = "duplicate-detector-producer"
  val props: Props = Props[SemanticProducerActor]
  val topicKey: String = "duplicates-predicted"
}
