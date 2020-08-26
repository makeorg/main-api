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

import akka.actor.{Actor, ActorRef, PoisonPill, Props, Timers}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import org.make.api.technical.job.JobReportingActor.JobReportingActorFacade
import org.make.api.technical.job.JobReportingActor.Protocol._
import org.make.api.technical.{ActorProtocol, TimeSettings}
import org.make.core.job.Job.{JobId, Progress}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

class JobReportingActor(
  id: JobId,
  work: JobReportingActorFacade => Future[Unit],
  override val jobCoordinatorService: JobCoordinatorService,
  heartRate: FiniteDuration
) extends Actor
    with JobCoordinatorServiceComponent
    with StrictLogging
    with Timers {

  private implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def preStart(): Unit = {
    timers.startTimerWithFixedDelay(s"${self.path.name}-heartbeat", Tick, heartRate)
    work(JobReportingActorFacade(self)).onComplete { result =>
      (self ? Finish(result.fold(Some.apply, _ => None))).mapTo[Ack.type].onComplete(_ => self ! PoisonPill)
    }
  }

  override def receive: Receive = {
    case Tick =>
      jobCoordinatorService.heartbeat(id).onComplete {
        case Failure(e) =>
          logger.error(s"Could not send heartbeat for job $id", e)
          self ! PoisonPill
        case Success(_) =>
      }
    case Report(progress) =>
      val originalSender = sender()
      jobCoordinatorService.report(id, progress).map(_ => originalSender ! Ack)
      ()
    case Finish(outcome) =>
      val originalSender = sender()
      jobCoordinatorService.finish(id, outcome).map(_ => originalSender ! Ack)
      ()
  }

}

object JobReportingActor {

  def props(
    id: JobId,
    work: JobReportingActorFacade => Future[Unit],
    jobCoordinatorService: JobCoordinatorService,
    heartRate: FiniteDuration
  ): Props =
    Props(new JobReportingActor(id, work, jobCoordinatorService, heartRate))

  final class JobReportingActorFacade private (actor: ActorRef) {
    def apply(progress: Progress)(implicit timeout: Timeout): Future[Unit] =
      (actor ? Report(progress)).mapTo[Ack.type].map(_ => ())
  }

  object JobReportingActorFacade {
    def apply(actor: ActorRef): JobReportingActorFacade = new JobReportingActorFacade(actor)
  }

  sealed abstract class Protocol extends ActorProtocol

  object Protocol {
    case class Report(progress: Progress) extends Protocol
    case class Finish(outcome: Option[Throwable]) extends Protocol
    case object Tick extends Protocol
    case object Ack extends Protocol
  }

}
