package org.make.api.proposal

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.make.api.technical.ShortenedNames
import org.make.api.user.UserService

class ProposalSupervisor(userService: UserService, userHistoryCoordinator: ActorRef)
    extends Actor
    with ActorLogging
    with ShortenedNames
    with DefaultProposalCoordinatorServiceComponent
    with ProposalCoordinatorComponent {

  override val proposalCoordinator: ActorRef =
    context.watch(context.actorOf(ProposalCoordinator.props, ProposalCoordinator.name))

  override def preStart(): Unit = {
    context.watch(context.actorOf(ProposalProducerActor.props, ProposalProducerActor.name))
    context.watch(
      context
        .actorOf(ProposalUserHistoryConsumerActor.props(userHistoryCoordinator), ProposalUserHistoryConsumerActor.name)
    )
    context.watch(
      context
        .actorOf(
          ProposalEmailConsumerActor.props(userService, this.proposalCoordinatorService),
          ProposalEmailConsumerActor.name
        )
    )
    context.watch(context.actorOf(ProposalEventStreamingActor.backoffPros, ProposalEventStreamingActor.backoffName))
  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }

}

object ProposalSupervisor {

  val name: String = "proposal"
  def props(userService: UserService, userHistoryCoordinator: ActorRef): Props =
    Props(new ProposalSupervisor(userService, userHistoryCoordinator))
}
