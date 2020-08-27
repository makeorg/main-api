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
import org.make.api.technical.crm.BasicCrmResponse.ManageManyContactsJobDetailsResponse
import org.make.api.technical.crm.CrmJobChecker.Protocol.{CrmCallFailed, CrmCallSucceeded, QuotaAvailable, Tick}

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future, Promise}

class CrmJobChecker(crmClient: CrmClient, jobs: Seq[String], promise: Promise[Unit])(
  implicit executionContext: ExecutionContext
) extends Actor
    with StrictLogging {

  @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
  private val queue: mutable.Queue[String] = mutable.Queue[String]()
  @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
  private val pendingCalls: mutable.Set[String] = mutable.Set[String]()

  override def preStart(): Unit = {
    jobs.foreach(queue.enqueue)
    context.system.scheduler.scheduleWithFixedDelay(1.second, 1.second, self, Tick)
    ()
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
        .manageContactListJobDetails(current)
        .map(CrmCallSucceeded(current, _))
        .recoverWith {
          case e => Future.successful(CrmCallFailed(current, e))
        }
        .pipeTo(self)
      ()
    }
  }

  private def handleErrorResponse(jobId: String, e: Throwable): Unit = {
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

  private def handleSuccessfulResponse(jobId: String, response: ManageManyContactsJobDetailsResponse): Unit = {
    pendingCalls -= jobId
    if (response.data.forall(response => response.status == "Completed")) {
      logger.debug(s"Job $jobId completed successfully: ${response.toString}")
    } else if (response.data.forall(response => response.status == "Error")) {
      logger.error(
        s"Job $jobId has errors: $response, " +
          s"file should be at https://api.mailjet.com/v3/DATA/Batchjob/$jobId/JSONError/application:json/LAST"
      )
    } else {
      queue.enqueue(jobId)
    }
  }
}

object CrmJobChecker {
  def props(crmClient: CrmClient, jobs: Seq[String], promise: Promise[Unit])(
    implicit executionContext: ExecutionContext
  ): Props =
    Props(new CrmJobChecker(crmClient, jobs, promise))

  sealed abstract class Protocol extends Product with Serializable

  object Protocol {
    final case object Tick extends Protocol
    final case object QuotaAvailable extends Protocol
    final case class CrmCallSucceeded(jobId: String, response: ManageManyContactsJobDetailsResponse) extends Protocol
    final case class CrmCallFailed(jobId: String, error: Throwable) extends Protocol
  }

}
