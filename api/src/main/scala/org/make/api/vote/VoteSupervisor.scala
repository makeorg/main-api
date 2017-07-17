package org.make.api.vote

import akka.actor.{Actor, ActorLogging, Props}
import org.make.api.technical.AvroSerializers

class VoteSupervisor extends Actor with AvroSerializers with ActorLogging {

  override def preStart(): Unit = {
    context.watch(context.actorOf(VoteCoordinator.props, VoteCoordinator.name))
    context.watch(context.actorOf(VoteProducerActor.props, VoteProducerActor.name))
  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }
}

object VoteSupervisor {
  val name: String = "vote"
  val props: Props = Props[VoteSupervisor]
}
