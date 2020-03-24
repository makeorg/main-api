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

package org.make.core
package job

import java.time.ZonedDateTime

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Interval
import eu.timepit.refined.{refineV, W}
import org.make.core.SprayJsonFormatters._
import org.make.core.job.Job.JobStatus.Running
import org.make.core.job.Job.{missableHeartbeats, JobId, JobStatus}
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}

final case class Job(id: JobId, status: JobStatus, createdAt: Option[ZonedDateTime], updatedAt: Option[ZonedDateTime])
    extends MakeSerializable
    with Timestamped {

  /** Check whether the job is stuck, given the expected heart rate.
    *
    * A job is stuck if it has missed three heartbeats (and did not update otherwise), to allow some leniency if it does
    * not report itself often and last heartbeats have not yet been received or handled (because e.g. of some network
    * delay, high load…).
    */
  def isStuck(heartRate: Duration): Boolean = {
    status match {
      case Running(_) =>
        updatedAt.forall(
          date =>
            java.time.Duration.between(date, DateHelper.now()).toMillis > (heartRate.toMillis * missableHeartbeats)
        )
      case _ => false
    }
  }

}

object Job {

  val defaultHeartRate: FiniteDuration = 10.seconds
  val missableHeartbeats: Int = 3

  type ProgressRefinement = Interval.Closed[W.`0D`.T, W.`100D`.T]
  type Progress = Double Refined ProgressRefinement

  implicit val progressJsonFormat: JsonFormat[Progress] = new JsonFormat[Progress] {
    override def write(obj: Progress): JsValue = JsNumber(obj.value)

    override def read(json: JsValue): Progress = json match {
      case n @ JsNumber(value) =>
        refineV[ProgressRefinement](value.toDouble) match {
          case Right(progress) => progress
          case Left(error)     => throw new IllegalArgumentException(s"Unable to convert $n: $error")
        }
      case other => throw new IllegalArgumentException(s"Unable to convert $other")
    }
  }

  implicit val jobJsonFormat: RootJsonFormat[Job] = DefaultJsonProtocol.jsonFormat4(Job.apply)

  final case class JobId(value: String) extends StringValue

  object JobId {

    val Reindex: JobId = JobId("Reindex")
    val SyncCrmData: JobId = JobId("SyncCrmData")

    implicit val jobIdFormatter: JsonFormat[JobId] = new JsonFormat[JobId] {
      override def read(json: JsValue): JobId = json match {
        case JsString(s) => JobId(s)
        case other       => throw new IllegalArgumentException(s"Unable to convert $other")
      }

      override def write(obj: JobId): JsValue = {
        JsString(obj.value)
      }
    }
  }

  sealed abstract class JobStatus extends Product with Serializable

  object JobStatus {

    case class Running(progress: Progress) extends JobStatus

    case class Finished(outcome: Option[String]) extends JobStatus

    implicit val statusJsonFormat: JsonFormat[JobStatus] = new JsonFormat[JobStatus] {

      import SprayJsonFormatters.syntax._

      private implicit val runningFormat: JsonFormat[Running] = DefaultJsonProtocol.jsonFormat1(Running.apply)
      private implicit val finishedFormat: JsonFormat[Finished] = DefaultJsonProtocol.jsonFormat1(Finished.apply)

      override def read(json: JsValue): JobStatus = {
        json.asJsObject.getFields("kind") match {
          case Seq(JsString("running"))  => json.as[Running]
          case Seq(JsString("finished")) => json.as[Finished]
        }
      }

      override def write(obj: JobStatus): JsValue = obj match {
        case Running(progress) => JsObject("kind" := "running", "progress" := progress)
        case Finished(outcome) => JsObject("kind" := "finished", "outcome" := outcome)
      }

    }

  }

}
