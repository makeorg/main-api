package org.make.api.user

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.make.api.MakeBackoffSupervisor
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.operation.OperationService
import org.make.api.technical.{AvroSerializers, ShortenedNames}

class UserSupervisor(userService: UserService, userHistoryCoordinator: ActorRef, operationService: OperationService)
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
        .actorOf(UserUpdateProducerActor.props, UserUpdateProducerActor.name)
    )

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(
          UserEmailConsumerActor.props(userService, operationService),
          UserEmailConsumerActor.name
        )
      context.actorOf(props, name)
    }

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(UserCrmConsumerActor.props(userService), UserCrmConsumerActor.name)
      context.actorOf(props, name)
    }

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(
          UserHistoryConsumerActor.props(userHistoryCoordinator),
          UserHistoryConsumerActor.name
        )
      context.actorOf(props, name)
    }

  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }
}

object UserSupervisor {

  val name: String = "users"
  def props(userService: UserService, userHistoryCoordinator: ActorRef, operationService: OperationService): Props =
    Props(new UserSupervisor(userService, userHistoryCoordinator, operationService))
}
