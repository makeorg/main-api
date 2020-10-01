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

import java.time.ZonedDateTime

import org.make.api.technical.job.JobEvent._
import org.make.core.job.Job.{JobId, JobStatus}
import org.make.core.job.Job
import org.scalatest.wordspec.AnyWordSpec
import stamina.Persisters
import stamina.testkit.StaminaTestKit
import eu.timepit.refined.auto._
import org.make.api.technical.job.JobActor.{DefinedJob, EmptyJob}

class JobSerializersTest extends AnyWordSpec with StaminaTestKit {

  val persisters: Persisters = Persisters(JobSerializers.serializers.toList)
  val eventDate: ZonedDateTime = ZonedDateTime.parse("2021-10-02T11:26:42.611Z")
  val jobId: JobId = JobId("job-id")

  "job persister" should {

    val job: DefinedJob = DefinedJob(Job(jobId, JobStatus.Running(42d), Some(eventDate), None))
    val emptyJob: EmptyJob.type = EmptyJob
    val started: Started = Started(jobId, eventDate)
    val heartbeatReceived: HeartbeatReceived = HeartbeatReceived(jobId, eventDate)
    val progressed: Progressed = Progressed(jobId, eventDate, 42d)
    val finished: Finished = Finished(jobId, eventDate, Some("outcome"))

    persisters.generateTestsFor(
      sample(job),
      sample(emptyJob),
      sample(started),
      sample(heartbeatReceived),
      sample(progressed),
      sample(finished)
    )

  }
}
