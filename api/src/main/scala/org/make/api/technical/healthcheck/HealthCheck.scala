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

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import io.circe._
import org.make.api.technical.healthcheck.HealthCheckCommands._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait HealthCheck extends Actor with ActorLogging {

  val techno: String

  def healthCheck(): Future[String]

  override def receive: Receive = {
    case CheckStatus =>
      val originalSender = sender()

      healthCheck().onComplete {
        case Success(status) =>
          originalSender ! HealthCheckSuccess(techno, status)
          self ! PoisonPill
        case Failure(e) =>
          originalSender ! HealthCheckError(techno, e.getMessage)
          self ! PoisonPill
      }
  }
}

object HealthCheckCommands {
  case object CheckStatus
  case object CheckExternalServices
}

sealed trait HealthCheckResponse {
  val service: String
  val message: String
}

object HealthCheckResponse {
  implicit val encoder: Encoder[HealthCheckResponse] = new Encoder[HealthCheckResponse] {
    override def apply(response: HealthCheckResponse): Json =
      Json.obj((response.service, Json.fromString(response.message)))
  }
}

final case class HealthCheckSuccess(override val service: String, override val message: String)
    extends HealthCheckResponse

final case class HealthCheckError(override val service: String, override val message: String)
    extends HealthCheckResponse

trait HealthCheckActorDefinition {
  val name: String
  def props(healthCheckExecutionContext: ExecutionContext): Props
}
