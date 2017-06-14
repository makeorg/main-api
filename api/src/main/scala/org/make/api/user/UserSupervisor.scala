package org.make.api.user

import akka.actor.{Actor, ActorLogging, Props}
import com.sksamuel.avro4s.RecordFormat
import org.make.api.technical.ConsumerActor.Consume
import org.make.api.technical.{AvroSerializers, ConsumerActor}
import org.make.core.user.UserEvent.UserEventWrapper

class UserSupervisor extends Actor with AvroSerializers with ActorLogging {

  override def preStart(): Unit = {
    context.watch(context.actorOf(UserCoordinator.props, UserCoordinator.name))

    context.watch(context.actorOf(UserProducerActor.props, UserProducerActor.name))

    val userConsumer = context.actorOf(
      ConsumerActor.props(RecordFormat[UserEventWrapper], "users"),
      ConsumerActor.name("users")
    )
    context.watch(userConsumer)
    userConsumer ! Consume

  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }
}

object UserSupervisor {
  val name: String = "user"
  val props: Props = Props[UserSupervisor]
}
