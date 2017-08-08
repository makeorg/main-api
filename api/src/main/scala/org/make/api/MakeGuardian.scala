package org.make.api

import java.util.concurrent.Executors

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.ActorMaterializer
import org.make.api.extensions.MailJetConfigurationExtension
import org.make.api.proposition.PropositionSupervisor
import org.make.api.technical.DeadLettersListenerActor
import org.make.api.technical.cluster.ClusterFormationActor
import org.make.api.technical.mailjet.{MailJet, MailJetCallbackProducerActor}
import org.make.api.user.UserSupervisor
import org.make.api.vote.VoteSupervisor

import scala.concurrent.ExecutionContext

class MakeGuardian extends Actor with ActorLogging with MailJetConfigurationExtension {

  override def preStart(): Unit = {
//    implicit val actorSystem: ActorSystem = context.system
    val materializer: ActorMaterializer = ActorMaterializer()

    context.watch(context.actorOf(PropositionSupervisor.props, PropositionSupervisor.name))
    context.watch(context.actorOf(VoteSupervisor.props, VoteSupervisor.name))
    context.watch(
      context
        .actorOf(DeadLettersListenerActor.props, DeadLettersListenerActor.name)
    )
    context.watch(context.actorOf(ClusterFormationActor.props, ClusterFormationActor.name))
    context.watch(context.actorOf(MailJetCallbackProducerActor.props, MailJetCallbackProducerActor.name))
    context.watch(context.actorOf(UserSupervisor.props, UserSupervisor.name))

    MailJet.run(mailJetConfiguration.url, mailJetConfiguration.apiKey, mailJetConfiguration.secretKey)(
      context.system,
      materializer,
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(3))
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
