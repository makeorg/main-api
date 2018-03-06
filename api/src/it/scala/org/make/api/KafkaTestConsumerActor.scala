package org.make.api

import java.util.UUID

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.technical.KafkaConsumerActor
import org.make.api.technical.KafkaConsumerActor.{CheckState, Ready, Waiting}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class KafkaTestConsumerActor[T](override val format: RecordFormat[T],
                                override val kafkaTopic: String,
                                override val groupId: String,
                                receiver: ActorRef)
    extends KafkaConsumerActor[T]
    with ActorLogging {

  override def handleMessage(message: T): Future[Unit] = Future.successful {
    receiver ! message
  }
}

object KafkaTestConsumerActor {

  def propsAndName[T](format: RecordFormat[T], kafkaTopic: String, receiver: ActorRef): (String, Props) = {
    val name = UUID.randomUUID().toString
    (name, Props(new KafkaTestConsumerActor[T](format, kafkaTopic, name, receiver)))
  }

  val defaultRetries = 5

  def waitUntilReady(target: ActorRef, retries: Int = defaultRetries): Future[Unit] = {
    val defaultInterval: Long = 100
    implicit val timeout: Timeout = Timeout(10.seconds)

    (target ? CheckState).flatMap {
      case Ready => Future.successful {}
      case Waiting =>
        Thread.sleep(defaultInterval)
        waitUntilReady(target)
    }.recoverWith {
      case e if retries <= 0 => Future.failed(e)
      case _ =>
        Thread.sleep(defaultInterval)
        waitUntilReady(target, retries - 1)
    }
  }

}
