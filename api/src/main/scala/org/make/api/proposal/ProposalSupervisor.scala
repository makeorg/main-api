package org.make.api.proposal

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.make.api.MakeBackoffSupervisor
import org.make.api.sequence.SequenceService
import org.make.api.tag.TagService
import org.make.api.technical.ShortenedNames
import org.make.api.user.UserService

class ProposalSupervisor(userService: UserService,
                         userHistoryCoordinator: ActorRef,
                         sessionHistoryCoordinator: ActorRef,
                         tagService: TagService,
                         sequenceService: SequenceService)
    extends Actor
    with ActorLogging
    with ShortenedNames
    with DefaultProposalCoordinatorServiceComponent
    with ProposalCoordinatorComponent {

  override val proposalCoordinator: ActorRef =
    context.watch(
      context.actorOf(
        ProposalCoordinator
          .props(userHistoryActor = userHistoryCoordinator, sessionHistoryActor = sessionHistoryCoordinator),
        ProposalCoordinator.name
      )
    )

  override def preStart(): Unit = {
    context.watch(context.actorOf(ProposalProducerActor.props, ProposalProducerActor.name))

    context.watch {
      val (props, name) = MakeBackoffSupervisor.propsAndName(
        ProposalUserHistoryConsumerActor.props(userHistoryCoordinator),
        ProposalUserHistoryConsumerActor.name
      )
      context.actorOf(props, name)
    }
    context.watch {
      val (props, name) = MakeBackoffSupervisor.propsAndName(
        ProposalSessionHistoryConsumerActor.props(sessionHistoryCoordinator),
        ProposalSessionHistoryConsumerActor.name
      )
      context.actorOf(props, name)
    }
    context.watch {
      val (props, name) = MakeBackoffSupervisor.propsAndName(
        ProposalEmailConsumerActor.props(userService, this.proposalCoordinatorService),
        ProposalEmailConsumerActor.name
      )
      context.actorOf(props, name)
    }
    context.watch {
      val (props, name) = MakeBackoffSupervisor.propsAndName(
        ProposalConsumerActor.props(proposalCoordinator, userService, tagService, sequenceService),
        ProposalConsumerActor.name
      )
      context.actorOf(props, name)
    }
  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }

}

object ProposalSupervisor {

  val name: String = "proposal"
  def props(userService: UserService,
            userHistoryCoordinator: ActorRef,
            sessionHistoryCoordinator: ActorRef,
            tagService: TagService,
            sequenceService: SequenceService): Props =
    Props(
      new ProposalSupervisor(
        userService,
        userHistoryCoordinator,
        sessionHistoryCoordinator,
        tagService,
        sequenceService
      )
    )
}
