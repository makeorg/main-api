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

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Props, SpawnProtocol}
import org.make.api.idea.{IdeaConsumerBehavior, IdeaProducerBehavior}
import org.make.api.proposal.ProposalSupervisor
import org.make.api.semantic.{SemanticPredictionsProducerBehavior, SemanticProducerBehavior}
import org.make.api.sequence.SequenceConfigurationActor
import org.make.api.sequence.SequenceConfigurationActor.SequenceConfigurationActorProtocol
import org.make.api.sessionhistory.SessionHistoryCoordinator
import org.make.api.technical.crm._
import org.make.api.technical.job.JobCoordinator
import org.make.api.technical.tracking.{
  ConcertationProducerBehavior,
  DemographicsProducerBehavior,
  TrackingProducerBehavior
}
import org.make.api.technical.{ActorProtocol, ActorSystemHelper, DeadLettersListenerActor, MakeDowningActor}
import org.make.api.user.UserSupervisor
import org.make.api.userhistory.{UserHistoryCommand, UserHistoryCoordinator}
import org.make.core.job.Job

object MakeGuardian {
  val name: String = "make-api"

  sealed trait GuardianProtocol extends ActorProtocol

  sealed trait GuardianCommand extends GuardianProtocol
  final case class Initialize(replyTo: ActorRef[Initialized.type]) extends GuardianCommand

  sealed trait GuardianResponse extends GuardianProtocol
  case object Initialized extends GuardianResponse

  val SpawnActorKey: ServiceKey[SpawnProtocol.Command] = ServiceKey("spawn-actor")

  def createBehavior(dependencies: MakeApi): Behavior[GuardianCommand] = {
    Behaviors.setup { implicit context =>
      Behaviors.receiveMessagePartial {
        case Initialize(sender) =>
          createTechnicalActors(dependencies)
          createFunctionalActors(dependencies)
          createProducersAndConsumers(dependencies)
          sender ! Initialized
          initialized()
      }
    }
  }

  private def initialized(): Behavior[GuardianCommand] = {
    Behaviors.receiveMessagePartial {
      case Initialize(sender) =>
        sender ! Initialized
        Behaviors.same
    }
  }

  private def createTechnicalActors(dependencies: MakeApi)(implicit context: ActorContext[_]): Unit = {
    context.watch(context.spawn(MakeDowningActor(), MakeDowningActor.name))
    context.watch(context.spawn(DeadLettersListenerActor(), DeadLettersListenerActor.name))
    val spawnActorRef = context.spawn(SpawnProtocol(), MakeGuardian.SpawnActorKey.id)
    context.watch(spawnActorRef)
    context.system.receptionist ! Receptionist.Register(MakeGuardian.SpawnActorKey, spawnActorRef)

    val sequenceCacheActor: ActorRef[SequenceConfigurationActorProtocol] = context.spawn(
      ActorSystemHelper.superviseWithBackoff(
        SequenceConfigurationActor(dependencies.persistentSequenceConfigurationService)
      ),
      SequenceConfigurationActor.name
    )
    context.watch(sequenceCacheActor)
    context.system.receptionist ! Receptionist.Register(
      SequenceConfigurationActor.SequenceCacheActorKey,
      sequenceCacheActor
    )
  }

  private def createFunctionalActors(dependencies: MakeApi)(implicit context: ActorContext[_]): Unit = {
    val userHistoryCoordinator: ActorRef[UserHistoryCommand] = UserHistoryCoordinator(system = context.system)
    context.watch(userHistoryCoordinator)
    context.system.receptionist ! Receptionist.Register(UserHistoryCoordinator.Key, userHistoryCoordinator)

    context.watch(
      context.actorOf(
        SessionHistoryCoordinator.props(userHistoryCoordinator, dependencies.idGenerator),
        SessionHistoryCoordinator.name
      )
    )

    context.watch(
      context.spawn[Nothing](
        ProposalSupervisor(dependencies, dependencies.makeSettings.lockDuration),
        ProposalSupervisor.name
      )
    )

    context.watch(context.spawn[Nothing](UserSupervisor(dependencies), UserSupervisor.name))

    val jobCoordinatorRef = JobCoordinator(context.system, Job.defaultHeartRate)
    context.watch(jobCoordinatorRef)
    context.system.receptionist ! Receptionist.Register(JobCoordinator.Key, jobCoordinatorRef)
  }

  private def createProducersAndConsumers(dependencies: MakeApi)(implicit context: ActorContext[_]): Unit = {
    spawnKafkaActorWithBackoff(ConcertationProducerBehavior(), ConcertationProducerBehavior.name)
    spawnKafkaActorWithBackoff(DemographicsProducerBehavior(), DemographicsProducerBehavior.name)
    spawnKafkaActorWithBackoff(IdeaProducerBehavior(), IdeaProducerBehavior.name)
    spawnKafkaActorWithBackoff(
      IdeaConsumerBehavior(dependencies.ideaService, dependencies.elasticsearchIdeaAPI),
      IdeaConsumerBehavior.name
    )
    spawnKafkaActorWithBackoff(MailJetProducerBehavior(), MailJetProducerBehavior.name)
    spawnKafkaActorWithBackoff(MailJetConsumerBehavior(dependencies.crmService), MailJetConsumerBehavior.name)
    spawnKafkaActorWithBackoff(MailJetEventProducerBehavior(), MailJetEventProducerBehavior.name)
    spawnKafkaActorWithBackoff(
      MailJetEventConsumerBehavior(dependencies.userService),
      MailJetEventConsumerBehavior.name
    )
    spawnKafkaActorWithBackoff(SemanticProducerBehavior(), SemanticProducerBehavior.name)
    spawnKafkaActorWithBackoff(SemanticPredictionsProducerBehavior(), SemanticPredictionsProducerBehavior.name)
    spawnKafkaActorWithBackoff(TrackingProducerBehavior(), TrackingProducerBehavior.name)
  }

  private def spawnKafkaActorWithBackoff(behavior: Behavior[_], name: String)(
    implicit context: ActorContext[_]
  ): Unit = {
    context.watch(
      context.spawn(
        ActorSystemHelper.superviseWithBackoff(behavior),
        name,
        Props.empty.withDispatcherFromConfig(kafkaDispatcher)
      )
    )
  }
}
