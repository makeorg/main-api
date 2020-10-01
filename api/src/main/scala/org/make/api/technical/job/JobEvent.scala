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

import org.make.core.MakeSerializable
import org.make.core.SprayJsonFormatters._
import org.make.core.job.Job._
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

sealed abstract class JobEvent extends MakeSerializable {
  def id: JobId
  def date: ZonedDateTime
}

object JobEvent {

  final case class Started(id: JobId, date: ZonedDateTime) extends JobEvent

  object Started {
    implicit val formatter: RootJsonFormat[Started] = DefaultJsonProtocol.jsonFormat2(Started.apply)
  }

  final case class HeartbeatReceived(id: JobId, date: ZonedDateTime) extends JobEvent

  object HeartbeatReceived {
    implicit val formatter: RootJsonFormat[HeartbeatReceived] = DefaultJsonProtocol.jsonFormat2(HeartbeatReceived.apply)
  }

  final case class Progressed(id: JobId, date: ZonedDateTime, progress: Progress) extends JobEvent

  object Progressed {
    implicit val formatter: RootJsonFormat[Progressed] = DefaultJsonProtocol.jsonFormat3(Progressed.apply)
  }

  final case class Finished(id: JobId, date: ZonedDateTime, outcome: Option[String]) extends JobEvent

  object Finished {
    implicit val formatter: RootJsonFormat[Finished] = DefaultJsonProtocol.jsonFormat3(Finished.apply)
  }
}
