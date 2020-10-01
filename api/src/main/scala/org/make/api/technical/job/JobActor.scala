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

package org.make.api.technical.job

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import eu.timepit.refined.auto._
import org.make.api.technical.{ActorCommand, ActorProtocol}
import org.make.api.technical.job.JobActor.Protocol.Command._
import org.make.api.technical.job.JobActor.Protocol.Response._
import org.make.api.technical.job.JobEvent._
import org.make.core.DateHelper
import org.make.core.job.Job
import org.make.core.job.Job.{JobId, JobStatus, Progress}

import scala.concurrent.duration.Duration

object JobActor {

  sealed abstract class Protocol extends ActorProtocol

  object Protocol {
    sealed abstract class Command extends Protocol with ActorCommand[JobId]

    object Command {
      final case class Start(id: JobId, replyTo: ActorRef[JobAcceptance]) extends Command
      final case class Heartbeat(id: JobId, replyTo: ActorRef[Process]) extends Command
      final case class Report(id: JobId, progress: Progress, replyTo: ActorRef[Process]) extends Command
      final case class Finish(id: JobId, failure: Option[Throwable], replyTo: ActorRef[Process]) extends Command
      final case class Get(id: JobId, replyTo: ActorRef[State]) extends Command
      final case class Kill(id: JobId) extends Command
    }

    sealed abstract class Response extends Protocol

    object Response {
      final case class JobAcceptance(isAccepted: Boolean) extends Response
      final case class State(value: Option[Job]) extends Response

      sealed trait Process extends Response
      final case object Ack extends Process
      final case object NotRunning extends Process
    }
  }

  val JournalPluginId: String = "make-api.event-sourcing.jobs.read-journal"
  val SnapshotPluginId: String = "make-api.event-sourcing.jobs.snapshot-store"

  def apply(heartRate: Duration): Behavior[Protocol.Command] = {
    Behaviors.setup { context =>
      val id: JobId = JobId(context.self.path.name)
      val persistenceId: PersistenceId = PersistenceId.ofUniqueId(id.value)
      EventSourcedBehavior[Protocol.Command, JobEvent, State](
        persistenceId,
        emptyState = State(value = None),
        commandHandler(heartRate),
        eventHandler
      ).withJournalPluginId(JournalPluginId)
        .withSnapshotPluginId(SnapshotPluginId)
        .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 10, keepNSnapshots = 50))
    }
  }

  def commandHandler(heartRate: Duration): (State, Protocol.Command) => Effect[JobEvent, State] = {
    case (state, Start(id, replyTo: ActorRef[JobAcceptance])) =>
      val startable = state.value.forall(
        job =>
          job.status match {
            case JobStatus.Running(_) => job.isStuck(heartRate)
            case _                    => true
          }
      )
      if (startable) {
        Effect.persist(Started(id, DateHelper.now())).thenReply(replyTo)(_ => JobAcceptance(true))
      } else {
        Effect.reply(replyTo)(JobAcceptance(false))
      }
    case (state, Heartbeat(id, replyTo)) => persistIfRunning(replyTo, state, HeartbeatReceived(id, DateHelper.now()))
    case (state, Report(id, progress, replyTo)) =>
      persistIfRunning(replyTo, state, Progressed(id, DateHelper.now(), progress))
    case (state, Finish(id, outcome, replyTo)) =>
      persistIfRunning(replyTo, state, Finished(id, DateHelper.now(), outcome.flatMap(e => Option(e.getMessage))))
    case (state, Get(_, replyTo)) => Effect.reply(replyTo)(state)
    case (_, Kill(_))             => Effect.stop().thenStop()
  }

  private def persistIfRunning(replyTo: ActorRef[Process], state: State, event: JobEvent): Effect[JobEvent, State] =
    state match {
      case State(Some(Job(_, JobStatus.Running(_), _, _))) => Effect.persist(event).thenReply(replyTo)(_ => Ack)
      case _                                               => Effect.reply(replyTo)(NotRunning)
    }

  val eventHandler: (State, JobEvent) => State = {
    case (_, Started(id, date)) =>
      State(Some(Job(id, JobStatus.Running(0d), Some(date), Some(date))))
    case (state, HeartbeatReceived(_, date)) =>
      State(value = state.value.map(_.copy(updatedAt = Some(date))))
    case (state, Progressed(_, date, progress)) =>
      State(value = state.value.map(_.copy(status = JobStatus.Running(progress), updatedAt = Some(date))))
    case (state, Finished(_, date, outcome)) =>
      State(value = state.value.map(_.copy(status = JobStatus.Finished(outcome), updatedAt = Some(date))))
    case (state, _) => state
  }

}
