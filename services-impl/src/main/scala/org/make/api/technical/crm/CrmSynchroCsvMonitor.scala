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

package org.make.api.technical.crm

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import grizzled.slf4j.Logging
import org.make.api.technical.ActorProtocol
import org.make.api.technical.crm.BasicCrmResponse.ManageContactsWithCsvResponse
import org.make.api.technical.crm.CrmSynchroCsvMonitor.Protocol.Command

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

object CrmSynchroCsvMonitor extends Logging {

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def apply(crmClient: CrmClient, promise: Promise[Unit], tickInterval: FiniteDuration)(
    jobIds: Seq[Long]
  )(implicit executionContext: ExecutionContext): Behavior[Protocol.Command] = {

    def running(jobIds: Seq[Long], pendingCalls: Seq[Long]): Behavior[Protocol.Command] = {
      Behaviors.setup { implicit context =>
        Behaviors.receiveMessage {
          case Command.Tick           => handleTick(jobIds, pendingCalls)
          case Command.QuotaAvailable => Behaviors.same
          case Command.CrmCallSucceeded(jobId, response) =>
            handleSuccessfulResponse(jobId, response)(jobIds, pendingCalls)(running)
          case Command.CrmCallFailed(jobId, e) =>
            handleErrorResponse(jobId, e)(jobIds, pendingCalls)(running)
        }
      }
    }

    def blocked(jobIds: Seq[Long], pendingCalls: Seq[Long]): Behavior[Protocol.Command] = {
      Behaviors.withTimers { timers =>
        timers.startSingleTimer(Command.QuotaAvailable, 1.hour)
        Behaviors.receiveMessage {
          case Command.Tick => Behaviors.same
          case Command.QuotaAvailable =>
            logger.info("Job checker becomes available again")
            running(jobIds, pendingCalls)
          case Command.CrmCallSucceeded(jobId, response) =>
            handleSuccessfulResponse(jobId, response)(jobIds, pendingCalls)(blocked)
          case Command.CrmCallFailed(jobId, e) =>
            handleErrorResponse(jobId, e)(jobIds, pendingCalls)(blocked)
        }
      }
    }

    def handleTick(jobIds: Seq[Long], pendingCalls: Seq[Long])(
      implicit context: ActorContext[Command]
    ): Behavior[Command] = {
      (jobIds, pendingCalls) match {
        case (Seq(), Seq()) =>
          promise.success {}
          Behaviors.stopped
        case (Seq(), _) =>
          Behaviors.same
        case (toMonitor +: tail, _) =>
          val futureCall: Future[ManageContactsWithCsvResponse] = crmClient.monitorCsvImport(toMonitor)
          context.pipeToSelf(futureCall) {
            case Success(response) => Command.CrmCallSucceeded(toMonitor, response)
            case Failure(e)        => Command.CrmCallFailed(toMonitor, e)
          }
          running(tail, pendingCalls :+ toMonitor)
      }

    }

    def handleErrorResponse(jobId: Long, error: Throwable)(jobIds: Seq[Long], pendingCalls: Seq[Long])(
      next: (Seq[Long], Seq[Long]) => Behavior[Command]
    ): Behavior[Command] =
      error match {
        case QuotaExceeded(_, message) =>
          logger.warn(s"Job checker is becoming blocked for an hour, received message is $message")
          blocked(jobIds :+ jobId, pendingCalls.filterNot(_ == jobId))
        case other =>
          logger.error(s"Error when checking status for job $jobId", other)
          next(jobIds :+ jobId, pendingCalls.filterNot(_ == jobId))
      }

    def handleSuccessfulResponse(jobId: Long, response: BasicCrmResponse.ManageContactsWithCsvResponse)(
      jobIds: Seq[Long],
      pendingCalls: Seq[Long]
    )(next: (Seq[Long], Seq[Long]) => Behavior[Command]): Behavior[Command] =
      if (response.data.forall(response => response.status == "Completed" || response.status == "Abort")) {
        logger.info(s"Job $jobId completed successfully: $response")
        if (response.data.forall(response => response.errorCount > 0)) {
          logger.error(
            s"Job $jobId has errors: $response, " +
              s"file should be at https://api.mailjet.com/v3/DATA/Batchjob/$jobId/CSVError/text:csv"
          )
        }
        next(jobIds, pendingCalls.filterNot(_ == jobId))
      } else {
        next(jobIds :+ jobId, pendingCalls.filterNot(_ == jobId))
      }

    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(Command.Tick, tickInterval)
      running(jobIds, Seq.empty)
    }
  }

  sealed abstract class Protocol extends ActorProtocol

  object Protocol {

    sealed abstract class Command extends Protocol

    object Command {
      case object Tick extends Command
      case object QuotaAvailable extends Command
      final case class CrmCallSucceeded(jobId: Long, response: ManageContactsWithCsvResponse) extends Command
      final case class CrmCallFailed(jobId: Long, error: Throwable) extends Command
    }
  }

}
