package org.make.api.technical

import org.make.api.technical.KafkaConsumerActor.{CheckState, Ready, Waiting}

abstract class BasicProducerActor[T, U] extends ProducerActor[T, U] {

  def kafkaTopic: String

  override def receive: Receive = {
    case CheckState =>
      if (producer.partitionsFor(kafkaConfiguration.topics(kafkaTopic)).size > 0) {
        sender() ! Ready
      } else {
        sender() ! Waiting
      }
    case event if eventClass.isAssignableFrom(event.getClass) =>
      sendRecord(kafkaTopic, convert(eventClass.cast(event)))
    case other => log.warning("Unknown event {}", other)
  }

  protected def convert(event: U): T
}
