package org.make.api

import akka.actor.{Actor, ActorLogging, Props}
import org.make.api.idea.{IdeaConsumerActor, IdeaProducerActor}
import org.make.api.proposal.ProposalSupervisor
import org.make.api.semantic.SemanticProducerActor
import org.make.api.sequence.{SequenceConfigurationActor, SequenceSupervisor}
import org.make.api.sessionhistory.SessionHistoryCoordinator
import org.make.api.technical.{DeadLettersListenerActor, MakeDowningActor}
import org.make.api.technical.crm._
import org.make.api.technical.healthcheck.HealthCheckSupervisor
import org.make.api.technical.tracking.TrackingProducerActor
import org.make.api.user.UserSupervisor
import org.make.api.userhistory.UserHistoryCoordinator

class MakeGuardian(makeApi: MakeApi) extends Actor with ActorLogging {
  override def preStart(): Unit = {
    context.watch(context.actorOf(MakeDowningActor.props, MakeDowningActor.name))

    context.watch(context.actorOf(DeadLettersListenerActor.props, DeadLettersListenerActor.name))

    val userHistoryCoordinator =
      context.watch(context.actorOf(UserHistoryCoordinator.props, UserHistoryCoordinator.name))

    val sessionHistoryCoordinator =
      context.watch(
        context.actorOf(SessionHistoryCoordinator.props(userHistoryCoordinator), SessionHistoryCoordinator.name)
      )

    context.watch(
      context
        .actorOf(
          SequenceConfigurationActor.props(makeApi.persistentSequenceConfigurationService),
          SequenceConfigurationActor.name
        )
    )

    context.watch(
      context.actorOf(SequenceSupervisor.props(userHistoryCoordinator, makeApi.themeService), SequenceSupervisor.name)
    )
    context.watch(
      context.actorOf(
        ProposalSupervisor
          .props(
            makeApi.userService,
            userHistoryCoordinator,
            sessionHistoryCoordinator,
            makeApi.tagService,
            makeApi.sequenceService,
            makeApi.operationService,
            makeApi.semanticService
          ),
        ProposalSupervisor.name
      )
    )
    context.watch(
      context.actorOf(
        UserSupervisor.props(makeApi.userService, userHistoryCoordinator, makeApi.operationService),
        UserSupervisor.name
      )
    )

    context.watch(context.actorOf(MailJetCallbackProducerActor.props, MailJetCallbackProducerActor.name))
    context.watch(context.actorOf(MailJetProducerActor.props, MailJetProducerActor.name))
    context.watch(context.actorOf(CrmContactProducerActor.props, CrmContactProducerActor.name))

    context.watch(context.actorOf(SemanticProducerActor.props, SemanticProducerActor.name))

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(MailJetConsumerActor.props(makeApi.crmService), MailJetConsumerActor.name)
      context.actorOf(props, name)
    }

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(
          MailJetEventConsumerActor.props(makeApi.userService),
          MailJetEventConsumerActor.name
        )
      context.actorOf(props, name)
    }

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(
          CrmContactEventConsumerActor.props(makeApi.userService, makeApi.crmService),
          CrmContactEventConsumerActor.name
        )
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
        MakeBackoffSupervisor.propsAndName(IdeaConsumerActor.props(makeApi.ideaService), IdeaConsumerActor.name)
      context.actorOf(props, name)
    }

    context.watch(context.actorOf(HealthCheckSupervisor.props, HealthCheckSupervisor.name))
  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }
}

object MakeGuardian {
  val name: String = "make-api"
  def props(makeApi: MakeApi): Props =
    Props(new MakeGuardian(makeApi))
}
