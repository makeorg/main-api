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

import akka.actor.{Actor, Props}
import akka.pattern.pipe
import com.typesafe.scalalogging.StrictLogging
import org.make.api.technical.crm.BasicCrmResponse.ManageContactsWithCsvResponse
import org.make.api.technical.crm.CrmSynchroCsvMonitor._

import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, Promise}

class CrmSynchroCsvMonitor(crmClient: CrmClient,
                           jobIds: Seq[Long],
                           promise: Promise[Unit],
                           tickInterval: FiniteDuration)(implicit executionContext: ExecutionContext)
    extends Actor
    with StrictLogging {

  private val queue: mutable.Queue[Long] = mutable.Queue[Long]()
  private val pendingCalls: mutable.Set[Long] = mutable.Set[Long]()

  override def preStart(): Unit = {
    jobIds.foreach(queue.enqueue)
    context.system.scheduler.scheduleWithFixedDelay(0.seconds, tickInterval, self, Tick)
  }

  override def receive: Receive = {
    case Tick                              => handleTick()
    case CrmCallSucceeded(jobId, response) => handleSuccessfulResponse(jobId, response)
    case CrmCallFailed(jobId, e)           => handleErrorResponse(jobId, e)
    case _                                 =>
  }

  def blocked: Receive = {
    case Tick => // Do nothing
    case QuotaAvailable =>
      logger.info("Job checker becomes available again")
      context.become(receive)
    case CrmCallSucceeded(jobId, response) => handleSuccessfulResponse(jobId, response)
    case CrmCallFailed(jobId, e)           => handleErrorResponse(jobId, e)
    case _                                 =>
  }

  private def handleTick(): Unit = {

    if (queue.isEmpty) {
      if (pendingCalls.isEmpty) {
        promise.success {}
        context.stop(self)
      }
    } else {
      val current = queue.dequeue()
      pendingCalls += current

      crmClient
        .monitorCsvImport(current)
        .map(CrmCallSucceeded(current, _))
        .recoverWith {
          case e => Future.successful(CrmCallFailed(current, e))
        }
        .pipeTo(self)
    }
  }

  private def handleErrorResponse(jobId: Long, e: Throwable): Unit = {
    pendingCalls -= jobId
    queue.enqueue(jobId)

    e match {
      case QuotaExceeded(_, message) =>
        context.become(blocked)
        context.system.scheduler.scheduleOnce(1.hour, self, QuotaAvailable)
        logger.warn(s"Job checker is becoming blocked for an hour, received message is $message")
      case other => logger.error(s"Error when checking status for job $jobId", other)
    }
  }

  private def handleSuccessfulResponse(jobId: Long, response: ManageContactsWithCsvResponse): Unit = {
    pendingCalls -= jobId
    if (response.data.forall(response => response.status == "Completed" || response.status == "Abort")) {
      logger.info(s"Job $jobId completed successfully: $response")
      if (response.data.forall(response => response.errorCount > 0)) {
        logger.error(
          s"Job $jobId has errors: $response, " +
            s"file should be at https://api.mailjet.com/v3/DATA/Batchjob/$jobId/CSVError/text:csv"
        )
      }
    } else {
      queue.enqueue(jobId)
    }
  }
}

object CrmSynchroCsvMonitor {
  def props(crmClient: CrmClient, jobIds: Seq[Long], promise: Promise[Unit], tickInterval: FiniteDuration)(
    implicit executionContext: ExecutionContext
  ): Props =
    Props(new CrmSynchroCsvMonitor(crmClient, jobIds, promise, tickInterval))

  case object Tick
  case object QuotaAvailable
  case class CrmCallSucceeded(jobId: Long, response: ManageContactsWithCsvResponse)
  case class CrmCallFailed(jobId: Long, error: Throwable)
}
