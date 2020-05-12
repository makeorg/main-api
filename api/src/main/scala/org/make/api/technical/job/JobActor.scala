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

import akka.actor.PoisonPill
import eu.timepit.refined.auto._
import org.make.api.technical.MakePersistentActor.Snapshot
import org.make.api.technical.job.JobActor.Protocol.Command._
import org.make.api.technical.job.JobActor.Protocol.Response._
import org.make.api.technical.job.JobEvent._
import org.make.api.technical.{ActorProtocol, MakePersistentActor}
import org.make.core.DateHelper
import org.make.core.job.Job
import org.make.core.job.Job.{JobId, JobStatus, Progress}

import scala.concurrent.duration.Duration

class JobActor(heartRate: Duration) extends MakePersistentActor(classOf[Job], classOf[JobEvent]) {

  private val id: JobId = JobId(self.path.name)

  override def persistenceId: String = id.value

  override def receiveCommand: Receive = {
    case Start(_) =>
      val startable = state.forall(
        job =>
          job.status match {
            case JobStatus.Running(_) => job.isStuck(heartRate)
            case _                    => true
          }
      )
      if (startable) {
        persistAndPublishEvent(Started(id, DateHelper.now()))(_ => sender() ! JobAcceptance(true))
      } else {
        sender() ! JobAcceptance(false)
      }
    case Heartbeat(_)        => acceptIfRunning(HeartbeatReceived(id, DateHelper.now()))
    case Report(_, progress) => acceptIfRunning(Progressed(id, DateHelper.now(), progress))
    case Finish(_, outcome) =>
      acceptIfRunning(Finished(id, DateHelper.now(), outcome.flatMap(e => Option(e.getMessage))))
    case Get(_)   => sender() ! State(state)
    case Snapshot => saveSnapshot()
    case Kill(_)  => self ! PoisonPill
  }

  private def acceptIfRunning(event: JobEvent): Unit = state match {
    case Some(Job(_, JobStatus.Running(_), _, _)) => persistAndPublishEvent(event)(_ => sender() ! Ack)
    case _                                        => sender() ! NotRunning
  }

  override val applyEvent: PartialFunction[JobEvent, Option[Job]] = {
    case Started(_, date) =>
      Some(Job(id, JobStatus.Running(0d), Some(date), Some(date)))
    case HeartbeatReceived(_, date) =>
      state.map(_.copy(updatedAt = Some(date)))
    case Progressed(_, date, progress) =>
      state.map(_.copy(status = JobStatus.Running(progress), updatedAt = Some(date)))
    case Finished(_, date, outcome) =>
      state.map(_.copy(status = JobStatus.Finished(outcome), updatedAt = Some(date)))
  }

}

object JobActor {

  sealed abstract class Protocol extends ActorProtocol

  object Protocol {

    sealed abstract class Command extends Protocol {
      def id: JobId
    }

    object Command {
      case class Start(id: JobId) extends Command
      case class Heartbeat(id: JobId) extends Command
      case class Report(id: JobId, progress: Progress) extends Command
      case class Finish(id: JobId, failure: Option[Throwable]) extends Command
      case class Get(id: JobId) extends Command
      case class Kill(id: JobId) extends Command
    }

    sealed abstract class Response extends Protocol

    object Response {
      case class JobAcceptance(isAccepted: Boolean) extends Response
      case object Ack extends Response
      case object NotRunning extends Response
      case class State(value: Option[Job]) extends Response
    }

  }

}
