package org.make.api

import akka.actor.{Actor, ActorLogging, Props}
import org.make.api.citizen.CitizenSupervisor
import org.make.api.proposition.PropositionSupervisor
import org.make.api.technical.DeadLettersListenerActor
import org.make.api.technical.cluster.ClusterFormationActor
import org.make.api.vote.VoteSupervisor

class MakeGuardian extends Actor with ActorLogging {

  override def preStart(): Unit = {
    context.watch(
      context.actorOf(CitizenSupervisor.props, CitizenSupervisor.name)
    )
    context.watch(
      context.actorOf(PropositionSupervisor.props, PropositionSupervisor.name)
    )
    context.watch(context.actorOf(VoteSupervisor.props, VoteSupervisor.name))
    context.watch(
      context
        .actorOf(DeadLettersListenerActor.props, DeadLettersListenerActor.name)
    )
    context.watch(
      context.actorOf(ClusterFormationActor.props, ClusterFormationActor.name)
    )
  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }
}

object MakeGuardian {
  val name: String = "make-api"
  val props: Props = Props[MakeGuardian]
}
