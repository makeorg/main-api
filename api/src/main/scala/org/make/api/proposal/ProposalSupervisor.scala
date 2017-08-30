package org.make.api.proposal

import akka.actor.{Actor, ActorLogging, Props}
import org.make.api.technical.ShortenedNames

class ProposalSupervisor extends Actor with ActorLogging with ShortenedNames {

  override def preStart(): Unit = {

    context.watch(
      context
        .actorOf(ProposalCoordinator.props, ProposalCoordinator.name)
    )
    context.watch(
      context
        .actorOf(ProposalProducerActor.props, ProposalProducerActor.name)
    )
    context.watch(
      context
        .actorOf(ProposalEventStreamingActor.backoffPros, ProposalEventStreamingActor.backoffName)
    )
  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }

}

object ProposalSupervisor {

  val name: String = "proposal"
  val props: Props = Props[ProposalSupervisor]
}
