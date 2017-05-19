package org.make.api.citizen

import akka.actor.{Actor, ActorLogging, Props}
import com.sksamuel.avro4s.RecordFormat
import org.make.api.kafka.ConsumerActor.Consume
import org.make.api.kafka.{AvroSerializers, CitizenProducerActor, ConsumerActor}
import org.make.core.citizen.CitizenEvent.CitizenEventWrapper

class CitizenSupervisor extends Actor with AvroSerializers with ActorLogging {


  override def preStart(): Unit = {
    context.watch(context.actorOf(CitizenCoordinator.props, CitizenCoordinator.name))

    context.watch(context.actorOf(CitizenProducerActor.props, CitizenProducerActor.name))

    val citizenConsumer = context.actorOf(
      ConsumerActor.props(RecordFormat[CitizenEventWrapper], "citizens"),
      ConsumerActor.name("citizens")
    )
    context.watch(citizenConsumer)
    citizenConsumer ! Consume

  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }
}

object CitizenSupervisor {
  val name: String = "citizen"
  val props: Props = Props[CitizenSupervisor]
}
