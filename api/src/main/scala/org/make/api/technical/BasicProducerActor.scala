package org.make.api.technical

abstract class BasicProducerActor[T, U] extends ProducerActor[T, U] {

  def kafkaTopic: String

  override def receive: Receive = {
    case event if eventClass.isAssignableFrom(event.getClass) =>
      sendRecord(kafkaTopic, convert(eventClass.cast(event)))
    case other => log.warning("Unknown event {}", other)
  }

  protected def convert(event: U): T
}
