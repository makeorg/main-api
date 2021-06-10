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

import akka.actor.typed.{ActorRef, SpawnProtocol}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{typed, Actor, ActorLogging, Props}
import org.make.api.MakeGuardian.{Ping, Pong}
import org.make.api.idea.{IdeaConsumerBehavior, IdeaProducerBehavior}
import org.make.api.proposal.ProposalSupervisor
import org.make.api.semantic.{SemanticPredictionsProducerBehavior, SemanticProducerBehavior}
import org.make.api.sequence.SequenceConfigurationActor
import org.make.api.sequence.SequenceConfigurationActor.SequenceConfigurationActorProtocol
import org.make.api.sessionhistory.SessionHistoryCoordinator
import org.make.api.technical.crm._
import org.make.api.technical.healthcheck.HealthCheckSupervisor
import org.make.api.technical.job.JobCoordinator
import org.make.api.technical.tracking.TrackingProducerBehavior
import org.make.api.technical.{ActorSystemHelper, DeadLettersListenerActor, MakeDowningActor}
import org.make.api.user.UserSupervisor
import org.make.api.userhistory.{UserHistoryCommand, UserHistoryCoordinator}
import org.make.core.job.Job

class MakeGuardian(makeApi: MakeApi) extends Actor with ActorLogging {
  override def preStart(): Unit = {

    context.watch(context.spawn(MakeDowningActor(), MakeDowningActor.name))

    context.watch(context.spawn(DeadLettersListenerActor(), DeadLettersListenerActor.name))

    val userHistoryCoordinator: ActorRef[UserHistoryCommand] = UserHistoryCoordinator(system = context.system.toTyped)
    context.watch(userHistoryCoordinator)
    context.system.toTyped.receptionist ! Receptionist.Register(UserHistoryCoordinator.Key, userHistoryCoordinator)

    context.watch(
      context.actorOf(
        SessionHistoryCoordinator.props(userHistoryCoordinator, makeApi.idGenerator),
        SessionHistoryCoordinator.name
      )
    )

    val sequenceCacheActor: ActorRef[SequenceConfigurationActorProtocol] = context.spawn(
      ActorSystemHelper.superviseWithBackoff(
        SequenceConfigurationActor(makeApi.persistentSequenceConfigurationService)
      ),
      SequenceConfigurationActor.name
    )
    context.watch(sequenceCacheActor)
    context.system.toTyped.receptionist ! Receptionist.Register(
      SequenceConfigurationActor.SequenceCacheActorKey,
      sequenceCacheActor
    )

    context.watch(
      context.spawn[Nothing](ProposalSupervisor(makeApi, makeApi.makeSettings.lockDuration), ProposalSupervisor.name)
    )

    context.watch(context.spawn[Nothing](UserSupervisor(makeApi), UserSupervisor.name))

    context.watch(
      context.spawn(
        MailJetEventProducerBehavior(),
        MailJetEventProducerBehavior.name,
        typed.Props.empty.withDispatcherFromConfig(kafkaDispatcher)
      )
    )
    context.watch(
      context.spawn(
        MailJetProducerBehavior(),
        MailJetProducerBehavior.name,
        typed.Props.empty.withDispatcherFromConfig(kafkaDispatcher)
      )
    )

    context.watch(
      context.spawn(
        SemanticProducerBehavior(),
        SemanticProducerBehavior.name,
        typed.Props.empty.withDispatcherFromConfig(kafkaDispatcher)
      )
    )
    context.watch(
      context.spawn(
        SemanticPredictionsProducerBehavior(),
        SemanticPredictionsProducerBehavior.name,
        typed.Props.empty.withDispatcherFromConfig(kafkaDispatcher)
      )
    )

    context.watch(
      context.spawn(
        ActorSystemHelper.superviseWithBackoff(MailJetConsumerBehavior(makeApi.crmService)),
        MailJetConsumerBehavior.name,
        typed.Props.empty.withDispatcherFromConfig(kafkaDispatcher)
      )
    )

    context.watch(
      context.spawn(
        ActorSystemHelper.superviseWithBackoff(MailJetEventConsumerBehavior(makeApi.userService)),
        MailJetEventConsumerBehavior.name,
        typed.Props.empty.withDispatcherFromConfig(kafkaDispatcher)
      )
    )

    context.watch(
      context.spawn(
        ActorSystemHelper.superviseWithBackoff(TrackingProducerBehavior()),
        TrackingProducerBehavior.name,
        typed.Props.empty.withDispatcherFromConfig(kafkaDispatcher)
      )
    )

    context.watch(
      context.spawn(
        ActorSystemHelper.superviseWithBackoff(IdeaProducerBehavior()),
        IdeaProducerBehavior.name,
        typed.Props.empty.withDispatcherFromConfig(kafkaDispatcher)
      )
    )

    context.watch(
      context.spawn(
        ActorSystemHelper.superviseWithBackoff(IdeaConsumerBehavior(makeApi.ideaService, makeApi.elasticsearchIdeaAPI)),
        IdeaConsumerBehavior.name,
        akka.actor.typed.Props.empty.withDispatcherFromConfig(kafkaDispatcher)
      )
    )

    context.watch(context.actorOf(HealthCheckSupervisor.props, HealthCheckSupervisor.name))

    val jobCoordinatorRef = JobCoordinator(makeApi.actorSystemTyped, Job.defaultHeartRate)
    context.watch(jobCoordinatorRef)
    context.system.toTyped.receptionist ! Receptionist.Register(JobCoordinator.Key, jobCoordinatorRef)

    val spawnActorRef = context.spawn(SpawnProtocol(), MakeGuardian.SpawnActorKey.id)
    context.watch(spawnActorRef)
    context.system.toTyped.receptionist ! Receptionist.Register(MakeGuardian.SpawnActorKey, spawnActorRef)

    ()
  }

  override def receive: Receive = {
    case Ping => sender() ! Pong
    case x    => log.info(s"received $x")
  }
}

object MakeGuardian {
  val name: String = "make-api"
  def props(makeApi: MakeApi): Props = Props(new MakeGuardian(makeApi))

  case object Ping
  case object Pong

  val SpawnActorKey: ServiceKey[SpawnProtocol.Command] = ServiceKey("spawn-actor")
}
