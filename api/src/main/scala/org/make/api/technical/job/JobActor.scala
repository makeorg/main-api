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
import org.make.api.technical.job.JobActor.Protocol.Command._
import org.make.api.technical.job.JobActor.Protocol.Response._
import org.make.api.technical.job.JobEvent._
import org.make.api.technical.{ActorCommand, ActorProtocol}
import org.make.core.job.Job
import org.make.core.job.Job.{JobId, JobStatus, Progress}
import org.make.core.{DateHelper, MakeSerializable}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, RootJsonFormat}

import scala.concurrent.duration.Duration

object JobActor {

  sealed trait JobState extends MakeSerializable {
    def toOption: Option[Job]
    def isStopped: Boolean
    def isStuck(heartRate: Duration): Boolean

    def persistIfRunning(replyTo: ActorRef[Process], event: JobEvent): Effect[JobEvent, JobState] = {
      if (isStopped) {
        Effect.reply(replyTo)(NotRunning)
      } else {
        Effect.persist(event).thenReply(replyTo)(_ => Ack)
      }
    }
  }

  case object EmptyJob extends JobState {
    override def toOption: Option[Job] = None
    override def isStopped: Boolean = true
    override def isStuck(heartRate: Duration): Boolean = false

    implicit val emptyJobJsonFormat: RootJsonFormat[EmptyJob.type] = new RootJsonFormat[EmptyJob.type] {
      private val Key = "empty"

      @SuppressWarnings(Array("org.wartremover.warts.Throw"))
      override def read(json: JsValue): EmptyJob.type = json match {
        case JsString(Key) => EmptyJob
        case other         => throw new IllegalStateException(s"$other is not an EmptyJob.")
      }

      override def write(obj: EmptyJob.type): JsValue = JsString(Key)
    }
  }

  final case class DefinedJob(job: Job) extends JobState {
    override def toOption: Option[Job] = Some(job)
    override def isStopped: Boolean = {
      job.status match {
        case JobStatus.Running(_) => false
        case _                    => true
      }
    }

    override def isStuck(heartRate: Duration): Boolean = job.isStuck(heartRate)
  }

  object DefinedJob {
    implicit val jsonFormat: RootJsonFormat[DefinedJob] = DefaultJsonProtocol.jsonFormat1(DefinedJob.apply)
  }

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
      EventSourcedBehavior[Protocol.Command, JobEvent, JobState](
        persistenceId,
        emptyState = EmptyJob,
        commandHandler(heartRate),
        eventHandler
      ).withJournalPluginId(JournalPluginId)
        .withSnapshotPluginId(SnapshotPluginId)
        .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 10, keepNSnapshots = 50))
    }
  }

  def commandHandler(heartRate: Duration): (JobState, Protocol.Command) => Effect[JobEvent, JobState] = {
    case (state, Start(id, replyTo: ActorRef[JobAcceptance])) =>
      if (state.isStopped || state.isStuck(heartRate)) {
        Effect.persist(Started(id, DateHelper.now())).thenReply(replyTo)(_ => JobAcceptance(true))
      } else {
        Effect.reply(replyTo)(JobAcceptance(false))
      }
    case (state, Heartbeat(id, replyTo)) => state.persistIfRunning(replyTo, HeartbeatReceived(id, DateHelper.now()))
    case (state, Report(id, progress, replyTo)) =>
      state.persistIfRunning(replyTo, Progressed(id, DateHelper.now(), progress))
    case (state, Finish(id, outcome, replyTo)) =>
      state.persistIfRunning(replyTo, Finished(id, DateHelper.now(), outcome.flatMap(e => Option(e.getMessage))))
    case (state, Get(_, replyTo)) => Effect.reply(replyTo)(State(state.toOption))
    case (_, Kill(_))             => Effect.stop().thenStop()
  }

  val eventHandler: (JobState, JobEvent) => JobState = {
    case (_, Started(id, date)) => DefinedJob(Job(id, JobStatus.Running(0d), Some(date), Some(date)))
    case (EmptyJob, _)          => EmptyJob
    case (DefinedJob(job), HeartbeatReceived(_, date)) =>
      DefinedJob(job.copy(updatedAt = Some(date)))
    case (DefinedJob(job), Progressed(_, date, progress)) =>
      DefinedJob(job.copy(status = JobStatus.Running(progress), updatedAt = Some(date)))
    case (DefinedJob(job), Finished(_, date, outcome)) =>
      DefinedJob(job.copy(status = JobStatus.Finished(outcome), updatedAt = Some(date)))
  }

}
