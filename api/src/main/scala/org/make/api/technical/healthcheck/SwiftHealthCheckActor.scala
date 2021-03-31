/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.{typed, Props}
import org.make.api.ActorSystemTypedComponent
import org.make.api.technical.ShortenedNames
import org.make.api.technical.storage.DefaultSwiftClientComponent
import org.make.swift.SwiftClient

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

class SwiftHealthCheckActor(healthCheckExecutionContext: ExecutionContext)
    extends HealthCheck
    with ShortenedNames
    with DefaultSwiftClientComponent
    with ActorSystemTypedComponent {

  override val techno: String = "swift"

  override val actorSystemTyped: typed.ActorSystem[_] = context.system.toTyped

  lazy val client: SwiftClient = swiftClient

  override def preStart(): Unit = {
    Await.result(client.init(), atMost = 30.seconds)
  }

  override def healthCheck(): Future[String] = {
    implicit val cxt: EC = healthCheckExecutionContext
    client
      .listBuckets()
      .map {
        case Seq() =>
          log.warning("Unexpected result in swift health check: containers list is empty")
          "NOK"
        case _ => "OK"
      }
      .recoverWith {
        case failed =>
          log.error("Error during swift helthcheck: ", failed)
          Future.failed(failed)
      }
  }
}

object SwiftHealthCheckActor extends HealthCheckActorDefinition {
  override val name: String = "swift-health-check"

  override def props(healthCheckExecutionContext: ExecutionContext): Props =
    Props(new SwiftHealthCheckActor(healthCheckExecutionContext))
}
