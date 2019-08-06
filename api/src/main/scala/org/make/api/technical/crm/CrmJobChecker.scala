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

import akka.actor.{Actor, PoisonPill, Props}
import akka.pattern.pipe
import com.typesafe.scalalogging.StrictLogging
import org.make.api.technical.crm.BasicCrmResponse.ManageManyContactsJobDetailsResponse
import org.make.api.technical.crm.CrmJobChecker.{CrmCallFailed, CrmCallSucceeded, Tick}

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future, Promise}

class CrmJobChecker(crmClient: CrmClient, jobs: Seq[String], promise: Promise[Unit])(
  implicit executionContext: ExecutionContext
) extends Actor
    with StrictLogging {

  private val queue: mutable.Queue[String] = mutable.Queue[String]()
  private val pendingCalls: mutable.Set[String] = mutable.Set[String]()

  override def preStart(): Unit = {
    queue.enqueue(jobs: _*)
    context.system.scheduler.schedule(1.second, 1.second, self, Tick)
  }

  override def receive: Receive = {
    case Tick =>
      if (queue.isEmpty) {
        if (pendingCalls.isEmpty) {
          self ! PoisonPill
          promise.success {}
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
      }
    case CrmCallSucceeded(jobId, response) =>
      pendingCalls -= jobId
      if (response.data.forall(response => response.status == "Completed")) {
        logger.debug(s"Job $jobId completed successfully: $response")
      } else if (response.data.forall(response => response.status == "Error")) {
        logger.error(
          s"Job $jobId has errors: $response, " +
            s"file should be at https://api.mailjet.com/v3/DATA/Batchjob/$jobId/JSONError/application:json/LAST"
        )
      } else {
        queue.enqueue(jobId)
      }
    case CrmCallFailed(jobId, e) =>
      pendingCalls -= jobId
      logger.error(s"Error when checking status for job $jobId", e)
      queue.enqueue(jobId)
    case _ =>
  }

}

object CrmJobChecker {
  def props(crmClient: CrmClient, jobs: Seq[String], promise: Promise[Unit])(
    implicit executionContext: ExecutionContext
  ): Props =
    Props(new CrmJobChecker(crmClient, jobs, promise))

  case object Tick
  case class CrmCallSucceeded(jobId: String, response: ManageManyContactsJobDetailsResponse)
  case class CrmCallFailed(jobId: String, error: Throwable)
}
