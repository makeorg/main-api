package org.make.api.sequence

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.make.api.MakeBackoffSupervisor
import org.make.api.technical.ShortenedNames
import org.make.api.theme.ThemeService
import org.make.core.DateHelper

class SequenceSupervisor(userHistoryCoordinator: ActorRef, themeService: ThemeService)
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
    context.watch {
      val (props, name) = MakeBackoffSupervisor.propsAndName(
        SequenceConsumerActor
          .props(sequenceCoordinator = sequenceCoordinator, themeService = themeService),
        SequenceConsumerActor.name
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
  def props(userHistoryCoordinator: ActorRef, themeService: ThemeService): Props =
    Props(new SequenceSupervisor(userHistoryCoordinator, themeService))
}
