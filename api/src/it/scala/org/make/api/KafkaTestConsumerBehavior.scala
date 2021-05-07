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

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.util.Timeout
import com.sksamuel.avro4s.{Decoder, SchemaFor}
import org.make.api.technical.KafkaConsumerBehavior
import org.make.api.technical.KafkaConsumerBehavior.{CheckState, Protocol, Ready, Waiting}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class KafkaTestConsumerBehavior[T: Decoder: SchemaFor](
  override val topicKey: String,
  override val groupId: String,
  receiver: ActorRef[T]
) extends KafkaConsumerBehavior[T] {

  override def handleMessage(message: T): Future[_] = Future.successful {
    receiver ! message
  }
}

object KafkaTestConsumerBehavior {
  def apply[T: Decoder: SchemaFor](topicKey: String, groupId: String, receiver: ActorRef[T]): Behavior[Protocol] = {
    new KafkaTestConsumerBehavior(topicKey, groupId, receiver)
      .createBehavior(groupId)
  }

  def waitUntilReady(target: ActorRef[Protocol], retries: Int = 5)(implicit scheduler: Scheduler): Future[Unit] = {
    val defaultInterval: Long = 100
    implicit val timeout: Timeout = Timeout(10.seconds)

    (target ? (ref => CheckState(ref))).flatMap {
      case Ready => Future.unit
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
