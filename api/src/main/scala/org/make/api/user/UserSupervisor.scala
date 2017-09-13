package org.make.api.user

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.technical.{AvroSerializers, ShortenedNames}

class UserSupervisor(userService: UserService, userHistoryCoordinator: ActorRef)
    extends Actor
    with ActorLogging
    with AvroSerializers
    with ShortenedNames
    with KafkaConfigurationExtension {

  override def preStart(): Unit = {
    context.watch(
      context
        .actorOf(UserProducerActor.props, UserProducerActor.name)
    )

    context.watch(
      context
        .actorOf(UserEmailConsumerActor.props(userService), UserEmailConsumerActor.name)
    )

    context.watch(
      context
        .actorOf(UserHistoryConsumerActor.props(userHistoryCoordinator), UserHistoryConsumerActor.name)
    )

  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }
}

object UserSupervisor {

  val name: String = "users"
  def props(userService: UserService, userHistoryCoordinator: ActorRef): Props =
    Props(new UserSupervisor(userService, userHistoryCoordinator))
}
