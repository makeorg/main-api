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

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import org.make.api.ActorSystemTypedComponent
import org.make.api.technical.job.JobActor.Protocol.Command._
import org.make.api.technical.job.JobActor.Protocol.Response._
import org.make.api.technical.job.JobReportingActor.JobReportingActorFacade
import org.make.api.technical.{IdGeneratorComponent, SpawnActorServiceComponent, TimeSettings}
import org.make.core.job.Job
import org.make.core.job.Job.{JobId, Progress}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait JobCoordinatorComponent {
  def jobCoordinator: ActorRef[JobActor.Protocol.Command]
}

trait JobCoordinatorService {
  def start(id: JobId, heartRate: FiniteDuration = Job.defaultHeartRate)(
    work: JobReportingActorFacade => Future[Unit]
  ): Future[JobAcceptance]
  def heartbeat(id: JobId): Future[Unit]
  def report(id: JobId, value: Progress): Future[Unit]
  def finish(id: JobId, outcome: Option[Throwable]): Future[Unit]
  def get(id: JobId): Future[Option[Job]]
}

trait JobCoordinatorServiceComponent {
  def jobCoordinatorService: JobCoordinatorService
}

trait DefaultJobCoordinatorServiceComponent extends JobCoordinatorServiceComponent {
  self: ActorSystemTypedComponent
    with IdGeneratorComponent
    with JobCoordinatorComponent
    with SpawnActorServiceComponent =>

  override lazy val jobCoordinatorService: JobCoordinatorService = new DefaultJobCoordinatorService

  class DefaultJobCoordinatorService extends JobCoordinatorService {

    implicit val timeout: Timeout = TimeSettings.defaultTimeout

    override def start(id: JobId, heartRate: FiniteDuration)(
      work: JobReportingActorFacade => Future[Unit]
    ): Future[JobAcceptance] = {
      (jobCoordinator ? (Start(id, _))).flatMap {
        case acceptance @ JobAcceptance(true) =>
          spawnActorService
            .spawn(
              behavior = JobReportingActor(id, work, jobCoordinatorService, heartRate),
              name = s"JobReportingActor-${id.value}"
            )
            .map(_ => acceptance)
        case acceptance => Future.successful(acceptance)
      }
    }

    override def heartbeat(id: JobId): Future[Unit] = {
      (jobCoordinator ? (Heartbeat(id, _))).map(_ => ())
    }

    override def report(id: JobId, progress: Progress): Future[Unit] = {
      (jobCoordinator ? (Report(id, progress, _))).map(_ => ())
    }

    override def finish(id: JobId, outcome: Option[Throwable]): Future[Unit] = {
      (jobCoordinator ? (Finish(id, outcome, _))).map(_ => ())
    }

    override def get(id: JobId): Future[Option[Job]] = {
      (jobCoordinator ? (Get(id, _))).map(_.value)
    }

  }

}
