package org.make.api.sequence

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.make.api.MakeBackoffSupervisor
import org.make.api.technical.ShortenedNames
import org.make.api.user.UserService
import org.make.core.DateHelper

class SequenceSupervisor(userService: UserService, userHistoryCoordinator: ActorRef)
    extends Actor
    with ActorLogging
    with ShortenedNames
    with DefaultSequenceCoordinatorServiceComponent
    with SequenceCoordinatorComponent {

  override val sequenceCoordinator: ActorRef =
    context.watch(context.actorOf(SequenceCoordinator.props(DateHelper), SequenceCoordinator.name))

  override def preStart(): Unit = {
    context.watch(context.actorOf(SequenceProducerActor.props, SequenceProducerActor.name))

    context.watch {
      val (props, name) = MakeBackoffSupervisor.propsAndName(
        SequenceUserHistoryConsumerActor.props(userHistoryCoordinator),
        SequenceUserHistoryConsumerActor.name
      )
      context.actorOf(props, name)
    }
  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }

}

object SequenceSupervisor {

  val name: String = "sequence"
  def props(userService: UserService, userHistoryCoordinator: ActorRef): Props =
    Props(new SequenceSupervisor(userService, userHistoryCoordinator))
}
