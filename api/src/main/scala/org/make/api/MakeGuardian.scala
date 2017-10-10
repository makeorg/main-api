package org.make.api

import akka.actor.{Actor, ActorLogging, Props}
import org.make.api.proposal.{DuplicateDetectorProducerActor, ProposalSessionHistoryConsumerActor, ProposalSupervisor}
import org.make.api.sequence.SequenceSupervisor
import org.make.api.sessionhistory.SessionHistoryCoordinator
import org.make.api.tag.TagService
import org.make.api.technical.DeadLettersListenerActor
import org.make.api.technical.cluster.ClusterFormationActor
import org.make.api.technical.mailjet.{MailJetCallbackProducerActor, MailJetConsumerActor, MailJetProducerActor}
import org.make.api.user.{UserService, UserSupervisor}
import org.make.api.userhistory.UserHistoryCoordinator

class MakeGuardian(userService: UserService, tagService: TagService) extends Actor with ActorLogging {

  override def preStart(): Unit = {
    context.watch(context.actorOf(DeadLettersListenerActor.props, DeadLettersListenerActor.name))
    context.watch(context.actorOf(ClusterFormationActor.props, ClusterFormationActor.name))

    val userHistoryCoordinator =
      context.watch(context.actorOf(UserHistoryCoordinator.props, UserHistoryCoordinator.name))

    val sessionHistoryCoordinator =
      context.watch(
        context.actorOf(SessionHistoryCoordinator.props(userHistoryCoordinator), SessionHistoryCoordinator.name)
      )

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(
          ProposalSessionHistoryConsumerActor.props(sessionHistoryCoordinator),
          ProposalSessionHistoryConsumerActor.name
        )
      context.actorOf(props, name)
    }

    context.watch(
      context.actorOf(
        ProposalSupervisor.props(userService, userHistoryCoordinator, sessionHistoryCoordinator, tagService),
        ProposalSupervisor.name
      )
    )
    context.watch(context.actorOf(UserSupervisor.props(userService, userHistoryCoordinator), UserSupervisor.name))
    context.watch(
      context.actorOf(SequenceSupervisor.props(userService, userHistoryCoordinator), SequenceSupervisor.name)
    )

    context.watch(context.actorOf(MailJetCallbackProducerActor.props, MailJetCallbackProducerActor.name))
    context.watch(context.actorOf(MailJetProducerActor.props, MailJetProducerActor.name))

    context.watch(context.actorOf(DuplicateDetectorProducerActor.props, DuplicateDetectorProducerActor.name))

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(MailJetConsumerActor.props, MailJetConsumerActor.name)
      context.actorOf(props, name)
    }
  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }
}

object MakeGuardian {
  val name: String = "make-api"
  def props(userService: UserService, tagService: TagService): Props = Props(new MakeGuardian(userService, tagService))
}
