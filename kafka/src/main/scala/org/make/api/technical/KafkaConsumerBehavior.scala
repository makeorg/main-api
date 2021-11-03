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

package org.make.api.technical

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed._
import akka.stream.scaladsl.{Sink, Source}
import com.sksamuel.avro4s.{Decoder, SchemaFor}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer
import org.make.api.extensions.KafkaConfiguration
import org.make.api.kafka.kafkaDispatcher
import org.make.api.technical.KafkaConsumerBehavior._
import org.make.api.technical.tracing.Tracing
import org.make.core.{AvroSerializers, EventWrapper, SlugHelper}

import java.util
import java.util.Properties
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

abstract class KafkaConsumerBehavior[T: SchemaFor: Decoder] extends AvroSerializers {

  protected val groupId: String
  protected val topicKey: String
  protected def handleMessage(message: T): Future[_]

  protected val handleMessagesParallelism: Int = 4
  protected def handleMessagesTimeout: FiniteDuration = 1.minute
  protected def customProperties: Properties = new Properties()

  protected def createConsumer(kafkaConfiguration: KafkaConfiguration, name: String): KafkaConsumer[String, T] = {
    val props = new Properties()
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfiguration.connectionString)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
    props.put(ConsumerConfig.CLIENT_ID_CONFIG, name)
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    props.putAll(customProperties)
    val consumer = new KafkaConsumer[String, T](
      props,
      new StringDeserializer(),
      new MakeKafkaAvroDeserializer(kafkaConfiguration.schemaRegistry)
    )
    consumer.subscribe(util.Arrays.asList(kafkaConfiguration.topic(topicKey)))
    consumer
  }

  final def createBehavior(name: String): Behavior[Protocol] = {
    Behaviors.setup { context =>
      val kafkaConfiguration = KafkaConfiguration(context.system)
      val consumer = createConsumer(kafkaConfiguration, name)

      context.self ! Consume

      implicit val system: ActorSystem[Nothing] = context.system
      val dispatcher = system.dispatchers.lookup(DispatcherSelector.fromConfig(kafkaDispatcher))

      Behaviors
        .receiveMessage[Protocol] {
          case CheckState(replyTo) =>
            if (consumer.assignment().size() > 0) {
              replyTo ! Ready
            } else {
              replyTo ! Waiting
            }
            Behaviors.same
          case Consume =>
            context.self ! Consume
            val records = consumer.poll(kafkaConfiguration.pollTimeout)
            val handleRecords = Source
              .fromIterator(() => records.asScala.iterator)
              .map(_.value())
              .mapAsync(handleMessagesParallelism) { message =>
                val messageClass = (message match {
                  case wrapper: EventWrapper[_] => wrapper.event
                  case _                        => message
                }).getClass.getSimpleName
                Tracing.entrypoint(SlugHelper(s"${getClass.getSimpleName}-$messageClass"))
                handleMessage(message).recover {
                  case e => context.log.error(s"Error while handling message of type [$messageClass]: $message", e)
                }(dispatcher)
              }
              .runWith(Sink.ignore)

            Try(Await.ready(handleRecords, handleMessagesTimeout)) match {
              case Success(_) =>
              case Failure(e) => context.log.error("Timeout occurred while consuming message", e)
            }
            // toDo: manage failures
            consumer.commitSync()
            Behaviors.same
        }
        .receiveSignal {
          case (_, PostStop) =>
            consumer.close()
            Behaviors.same
        }
    }
  }

  final def doNothing(event: Any): Future[Unit] = {
    Future.unit
  }
}

object KafkaConsumerBehavior {
  sealed trait Protocol
  case object Consume extends Protocol
  final case class CheckState(replyTo: ActorRef[ConsumerStatus]) extends Protocol

  sealed trait ConsumerStatus
  case object Ready extends ConsumerStatus
  case object Waiting extends ConsumerStatus
}
