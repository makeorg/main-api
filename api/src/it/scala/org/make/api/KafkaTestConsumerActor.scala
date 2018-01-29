package org.make.api

import java.util.UUID

import akka.actor.{ActorRef, Props}
import com.sksamuel.avro4s.RecordFormat
import org.make.api.technical.KafkaConsumerActor

import scala.concurrent.Future

class KafkaTestConsumerActor[T](override val format: RecordFormat[T],
                                override val kafkaTopic: String,
                                override val groupId: String,
                                receiver: ActorRef)
    extends KafkaConsumerActor[T] {

  override def handleMessage(message: T): Future[Unit] = Future.successful {
    receiver ! message
  }
}

object KafkaTestConsumerActor {

  def propsAndName[T](format: RecordFormat[T], kafkaTopic: String, receiver: ActorRef): (String, Props) = {
    val name = UUID.randomUUID().toString
    (name, Props(new KafkaTestConsumerActor[T](format, kafkaTopic, name, receiver)))
  }

}
