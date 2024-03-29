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

package org.make.api.technical.healthcheck

import io.circe.{Encoder, Json}
import org.make.api.technical.healthcheck.HealthCheck.Status

import scala.concurrent.{ExecutionContext, Future}

trait HealthCheck {

  val techno: String

  def healthCheck()(implicit ctx: ExecutionContext): Future[Status]
}

object HealthCheck {
  sealed trait Status extends Product with Serializable {
    def message: String
  }

  object Status {
    case object OK extends Status {
      override val message: String = "OK"
    }
    final case class NOK(reason: Option[String] = None) extends Status {
      override val message: String = reason.map(r => s"NOK: $r").getOrElse("NOK")
    }
  }

  final case class HealthCheckResponse(service: String, message: String)

  object HealthCheckResponse {
    implicit val encoderSuccess: Encoder[HealthCheckResponse] = new Encoder[HealthCheckResponse] {
      override def apply(response: HealthCheckResponse): Json =
        Json.obj((response.service, Json.fromString(response.message)))
    }
  }
}
