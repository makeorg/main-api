/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

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

class KafkaTestConsumerActor[T](
  override val format: RecordFormat[T],
  override val kafkaTopic: String,
  override val groupId: String,
  receiver: ActorRef
) extends KafkaConsumerActor[T]
    with ActorLogging {

  override def handleMessage(message: T): Future[_] = Future.successful {
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
        waitUntilReady(target, retries)
    }.recoverWith {
      case e if retries <= 0 => Future.failed(e)
      case _ =>
        Thread.sleep(defaultInterval)
        waitUntilReady(target, retries - 1)
    }
  }

}
