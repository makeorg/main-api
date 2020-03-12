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

import org.make.api.technical.job.JobEvent._
import org.make.core.SprayJsonFormatters
import org.make.core.job.Job
import stamina.V1
import stamina.json.{persister, JsonPersister}

object JobSerializers extends SprayJsonFormatters {

  private val jobSerializer: JsonPersister[Job, V1] = persister[Job]("job")
  private val startedSerializer: JsonPersister[Started, V1] = persister[Started]("job-started")
  private val heartbeatReceivedSerializer: JsonPersister[HeartbeatReceived, V1] =
    persister[HeartbeatReceived]("job-heartbeat-received")
  private val progressedSerializer: JsonPersister[Progressed, V1] = persister[Progressed]("job-progressed")
  private val finishedSerializer: JsonPersister[Finished, V1] = persister[Finished]("job-finished")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(jobSerializer, startedSerializer, heartbeatReceivedSerializer, progressedSerializer, finishedSerializer)

}
