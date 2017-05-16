package org.make.api

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.StrictLogging
import org.make.api.citizen.CitizenSupervisor
import org.make.api.proposition.PropositionSupervisor
import org.make.api.vote.VoteSupervisor

class MakeGuardian extends Actor with StrictLogging {


  override def preStart(): Unit = {
    context.watch(context.actorOf(CitizenSupervisor.props, CitizenSupervisor.name))
    context.watch(context.actorOf(PropositionSupervisor.props, PropositionSupervisor.name))
    context.watch(context.actorOf(VoteSupervisor.props, VoteSupervisor.name))
    context.watch(context.actorOf(DeadLettersListenerActor.props, DeadLettersListenerActor.name))
  }

  override def receive: Receive = {
    case x => logger.info(s"received $x")
  }
}

object MakeGuardian {
  val name: String = "make-api"
  val props: Props = Props[MakeGuardian]
}