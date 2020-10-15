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

import java.util.concurrent.Executors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import org.make.api.technical.ExecutorServiceHelper._
import org.make.api.technical.TimeSettings
import org.make.api.technical.healthcheck.HealthCheckCommands._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class HealthCheckSupervisor extends Actor with ActorLogging {

  val healthCheckActorDefinitions: Seq[HealthCheckActorDefinition] =
    Seq(
      ZookeeperHealthCheckActor,
      CockroachHealthCheckActor,
      ElasticsearchHealthCheckActor,
      CassandraHealthCheckActor,
      KafkaHealthCheckActor,
      SwiftHealthCheckActor,
      AvroHealthCheckActor
    )

  private implicit val timeout: Timeout = TimeSettings.defaultTimeout

  val healthCheckExecutionContext: ExecutionContext =
    Executors.newFixedThreadPool(10).instrument("healthchecks").toExecutionContext

  override def receive: Receive = {
    case CheckExternalServices =>
      val caller: ActorRef = sender()
      val children = healthCheckActorDefinitions.map { actor =>
        context.actorOf(actor.props(healthCheckExecutionContext), actor.name)
      }
      Future
        .traverse(children) { healthCheck =>
          (healthCheck ? CheckStatus).mapTo[HealthCheckResponse]
        }
        .pipeTo(caller)
      ()
    case other => log.info(s"received $other")
  }
}

object HealthCheckSupervisor {
  val name: String = "health-checks"

  def props: Props = Props[HealthCheckSupervisor]()
}
