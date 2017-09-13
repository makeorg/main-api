package org.make.api

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.ActorMaterializer
import org.make.api.proposal.ProposalSupervisor
import org.make.api.technical.DeadLettersListenerActor
import org.make.api.technical.cluster.ClusterFormationActor
import org.make.api.technical.mailjet.{MailJetCallbackProducerActor, MailJetConsumerActor, MailJetProducerActor}
import org.make.api.user.{UserService, UserSupervisor}
import org.make.api.userhistory.UserHistoryCoordinator

class MakeGuardian(userService: UserService) extends Actor with ActorLogging {

  override def preStart(): Unit = {
    val materializer: ActorMaterializer = ActorMaterializer()
    context.watch(context.actorOf(DeadLettersListenerActor.props, DeadLettersListenerActor.name))
    context.watch(context.actorOf(ClusterFormationActor.props, ClusterFormationActor.name))

    val historyCoordinator = context.watch(context.actorOf(UserHistoryCoordinator.props, UserHistoryCoordinator.name))

    context.watch(context.actorOf(ProposalSupervisor.props(userService, historyCoordinator), ProposalSupervisor.name))
    context.watch(context.actorOf(UserSupervisor.props(userService, historyCoordinator), UserSupervisor.name))

    context.watch(context.actorOf(MailJetCallbackProducerActor.props, MailJetCallbackProducerActor.name))
    context.watch(context.actorOf(MailJetProducerActor.props, MailJetProducerActor.name))
    context.watch(context.actorOf(MailJetConsumerActor.props, MailJetConsumerActor.name))
  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }
}

object MakeGuardian {
  val name: String = "make-api"
  def props(userService: UserService): Props = Props(new MakeGuardian(userService))
}
