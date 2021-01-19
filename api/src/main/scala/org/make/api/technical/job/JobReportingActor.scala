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

import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import grizzled.slf4j.Logging
import org.make.api.technical.ActorProtocol
import org.make.api.technical.job.JobReportingActor.Protocol.{Command, Response}
import org.make.core.job.Job.{JobId, Progress}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

object JobReportingActor extends Logging {

  final class JobReportingActorFacade private (actor: ActorRef[Command]) {
    def apply(progress: Progress)(implicit timeout: Timeout, scheduler: Scheduler): Future[Unit] =
      (actor ? (Command.Report(progress, _))).map(_ => ())
  }

  object JobReportingActorFacade {
    def apply(actor: ActorRef[Command]): JobReportingActorFacade = new JobReportingActorFacade(actor)
  }

  sealed abstract class Protocol extends ActorProtocol

  object Protocol {
    sealed abstract class Command extends Protocol

    object Command {
      final case class Report(progress: Progress, replyTo: ActorRef[Response.Ack.type]) extends Command
      final case class Finish(outcome: Option[Throwable]) extends Command
      case object Tick extends Command
      case object HeartbeatSuccess extends Command
      case object HeartbeatFailure extends Command
      final case class ReportResult(replyTo: ActorRef[Response.Ack.type]) extends Command
      case object Stop extends Command
    }

    sealed abstract class Response extends Protocol

    object Response {
      case object Ack extends Response
    }

  }

  def apply(
    jobId: JobId,
    work: JobReportingActorFacade => Future[Unit],
    jobCoordinatorService: JobCoordinatorService,
    heartRate: FiniteDuration
  ): Behavior[Protocol.Command] = {
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(s"${context.self.path.name}-heartbeat", Command.Tick, heartRate)

        context.pipeToSelf(work(JobReportingActorFacade(context.self))) { result =>
          Command.Finish(result.failed.toOption)
        }

        Behaviors.receiveMessage {
          case Command.Tick =>
            val futureResult = jobCoordinatorService.heartbeat(jobId)
            context.pipeToSelf(futureResult) {
              case Success(_) => Command.HeartbeatSuccess
              case Failure(e) =>
                logger.error(s"Could not send heartbeat for job $jobId", e)
                Command.HeartbeatFailure
            }
            Behaviors.same
          case Command.HeartbeatSuccess => Behaviors.same
          case Command.HeartbeatFailure => Behaviors.stopped
          case Command.Report(progress, replyTo) =>
            val futureResult = jobCoordinatorService.report(jobId, progress)
            context.pipeToSelf(futureResult) {
              case Success(_) => Command.ReportResult(replyTo)
              case Failure(e) =>
                logger.error(s"Job $jobId failed to report", e)
                Command.ReportResult(replyTo)
            }
            Behaviors.same
          case Command.ReportResult(replyTo) =>
            replyTo ! Response.Ack
            Behaviors.same
          case Command.Finish(outcome) =>
            val futureResult = jobCoordinatorService.finish(jobId, outcome)
            context.pipeToSelf(futureResult) {
              case Success(_) => Command.Stop
              case Failure(e) =>
                logger.error(s"Job $jobId failed to finish", e)
                Command.Stop
            }
            Behaviors.same
          case Command.Stop => Behaviors.stopped
        }
      }
    }

  }
}
