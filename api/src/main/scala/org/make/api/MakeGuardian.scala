/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api

import akka.actor.{Actor, ActorLogging, Props}
import org.make.api.MakeGuardian.{Ping, Pong}
import org.make.api.idea.{IdeaConsumerActor, IdeaProducerActor}
import org.make.api.proposal.ProposalSupervisor
import org.make.api.semantic.{SemanticPredictionsProducerActor, SemanticProducerActor}
import org.make.api.sequence.SequenceConfigurationActor
import org.make.api.sessionhistory.SessionHistoryCoordinator
import org.make.api.technical.crm._
import org.make.api.technical.healthcheck.HealthCheckSupervisor
import org.make.api.technical.tracking.TrackingProducerActor
import org.make.api.technical.{DeadLettersListenerActor, MakeDowningActor}
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
      context.actorOf(
        ProposalSupervisor
          .props(userHistoryCoordinator, sessionHistoryCoordinator, makeApi),
        ProposalSupervisor.name
      )
    )
    context.watch(context.actorOf(UserSupervisor.props(userHistoryCoordinator, makeApi), UserSupervisor.name))

    context.watch(context.actorOf(MailJetCallbackProducerActor.props, MailJetCallbackProducerActor.name))
    context.watch(context.actorOf(MailJetProducerActor.props, MailJetProducerActor.name))

    context.watch(context.actorOf(SemanticProducerActor.props, SemanticProducerActor.name))
    context.watch(context.actorOf(SemanticPredictionsProducerActor.props, SemanticPredictionsProducerActor.name))

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(MailJetConsumerActor.props(makeApi.crmService), MailJetConsumerActor.name)
      context.actorOf(props, name)
    }

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(
          MailJetEventConsumerActor.props(makeApi.userService).withDispatcher(kafkaDispatcher),
          MailJetEventConsumerActor.name
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
        MakeBackoffSupervisor.propsAndName(
          IdeaConsumerActor.props(makeApi.ideaService, makeApi.elasticsearchConfiguration, makeApi.elasticsearchClient),
          IdeaConsumerActor.name
        )
      context.actorOf(props, name)
    }

    context.watch(context.actorOf(HealthCheckSupervisor.props, HealthCheckSupervisor.name))
  }

  override def receive: Receive = {
    case Ping => sender() ! Pong
    case x    => log.info(s"received $x")
  }
}

object MakeGuardian {
  val name: String = "make-api"
  def props(makeApi: MakeApi): Props =
    Props(new MakeGuardian(makeApi))

  case object Ping
  case object Pong
}
