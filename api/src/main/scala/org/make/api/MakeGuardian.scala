package org.make.api

import akka.actor.{Actor, ActorLogging, Props}
import org.make.api.idea.{IdeaConsumerActor, IdeaProducerActor, IdeaService}
import org.make.api.operation.OperationService
import org.make.api.proposal.{DuplicateDetectorProducerActor, ProposalSupervisor}
import org.make.api.sequence.{
  PersistentSequenceConfigurationService,
  SequenceConfigurationActor,
  SequenceService,
  SequenceSupervisor
}
import org.make.api.sessionhistory.SessionHistoryCoordinator
import org.make.api.tag.TagService
import org.make.api.technical.DeadLettersListenerActor
import org.make.api.technical.mailjet.{MailJetCallbackProducerActor, MailJetConsumerActor, MailJetProducerActor}
import org.make.api.technical.tracking.TrackingProducerActor
import org.make.api.theme.ThemeService
import org.make.api.user.{UserService, UserSupervisor}
import org.make.api.userhistory.UserHistoryCoordinator

class MakeGuardian(persistentSequenceConfigurationService: PersistentSequenceConfigurationService,
                   userService: UserService,
                   tagService: TagService,
                   themeService: ThemeService,
                   sequenceService: SequenceService,
                   operationService: OperationService,
                   ideaService: IdeaService)
    extends Actor
    with ActorLogging {

  override def preStart(): Unit = {
    context.watch(context.actorOf(DeadLettersListenerActor.props, DeadLettersListenerActor.name))
    // context.watch(context.actorOf(ClusterFormationActor.props, ClusterFormationActor.name))

    val userHistoryCoordinator =
      context.watch(context.actorOf(UserHistoryCoordinator.props, UserHistoryCoordinator.name))

    val sessionHistoryCoordinator =
      context.watch(
        context.actorOf(SessionHistoryCoordinator.props(userHistoryCoordinator), SessionHistoryCoordinator.name)
      )

    context.watch(
      context
        .actorOf(
          SequenceConfigurationActor.props(persistentSequenceConfigurationService),
          SequenceConfigurationActor.name
        )
    )

    val sequenceCoordinator = context.watch(
      context.actorOf(
        SequenceSupervisor.props(userService, userHistoryCoordinator, tagService, themeService),
        SequenceSupervisor.name
      )
    )
    context.watch(
      context.actorOf(
        ProposalSupervisor
          .props(
            userService,
            userHistoryCoordinator,
            sessionHistoryCoordinator,
            tagService,
            sequenceService,
            operationService
          ),
        ProposalSupervisor.name
      )
    )
    context.watch(
      context.actorOf(UserSupervisor.props(userService, userHistoryCoordinator, operationService), UserSupervisor.name)
    )

    context.watch(context.actorOf(MailJetCallbackProducerActor.props, MailJetCallbackProducerActor.name))
    context.watch(context.actorOf(MailJetProducerActor.props, MailJetProducerActor.name))

    context.watch(context.actorOf(DuplicateDetectorProducerActor.props, DuplicateDetectorProducerActor.name))

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(MailJetConsumerActor.props, MailJetConsumerActor.name)
      context.actorOf(props, name)
    }

    context.watch {
      val (props, name) = MakeBackoffSupervisor.propsAndName(TrackingProducerActor.props, TrackingProducerActor.name)
      context.actorOf(props, name)
    }

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(IdeaProducerActor.props, IdeaProducerActor.name)

      context.actorOf(props, name)
    }

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(IdeaConsumerActor.props(ideaService), IdeaConsumerActor.name)
      context.actorOf(props, name)
    }
  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }
}

object MakeGuardian {
  val name: String = "make-api"
  def props(persistentSequenceConfigurationService: PersistentSequenceConfigurationService,
            userService: UserService,
            tagService: TagService,
            themeService: ThemeService,
            sequenceService: SequenceService,
            operationService: OperationService,
            ideaService: IdeaService): Props =
    Props(
      new MakeGuardian(
        persistentSequenceConfigurationService,
        userService,
        tagService,
        themeService,
        sequenceService,
        operationService,
        ideaService
      )
    )
}
