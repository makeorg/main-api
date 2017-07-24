package org.make.api.user

import akka.actor.{Actor, ActorLogging, Props}
import org.make.api.technical.{AvroSerializers, ShortenedNames}

class UserSupervisor extends Actor with ActorLogging with AvroSerializers with ShortenedNames {

  override def preStart(): Unit = {
    context.watch(
      context
        .actorOf(UserProducerActor.props, UserProducerActor.name)
    )
  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }
}

object UserSupervisor {

  val name: String = "users"
  val props: Props = Props[UserSupervisor]
}
