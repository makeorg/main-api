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

package org.make.api.technical

import akka.actor.{Actor, Props}
import akka.pattern.{BackoffOpts, BackoffSupervisor}
import kamon.Kamon
import kamon.metric.Gauge
import org.make.api.technical.MemoryMonitoringActor.Monitor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class MemoryMonitoringActor extends Actor {
  val memoryMax: Gauge = Kamon.gauge("jvm-memory-max").withoutTags()
  val memoryFree: Gauge = Kamon.gauge("jvm-memory-free").withoutTags()
  val memoryTotal: Gauge = Kamon.gauge("jvm-memory-total").withoutTags()

  override def preStart(): Unit = {
    context.system.scheduler.scheduleWithFixedDelay(1.second, 1.second, self, Monitor)
  }

  override def receive: Receive = {
    case Monitor =>
      memoryFree.update(Runtime.getRuntime.freeMemory().toDouble)
      memoryMax.update(Runtime.getRuntime.maxMemory().toDouble)
      memoryTotal.update(Runtime.getRuntime.totalMemory().toDouble)
    case _ =>
  }
}

object MemoryMonitoringActor {
  case object Monitor

  val name = "memory-monitor-backoff"
  val props: Props = {
    val maxNrOfRetries = 50
    BackoffSupervisor.props(
      BackoffOpts
        .onStop(
          Props[MemoryMonitoringActor],
          childName = "memory-monitor",
          minBackoff = 3.seconds,
          maxBackoff = 30.seconds,
          randomFactor = 0.2
        )
        .withMaxNrOfRetries(maxNrOfRetries)
    )
  }
}
