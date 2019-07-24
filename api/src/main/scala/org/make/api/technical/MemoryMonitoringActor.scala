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
import kamon.metric.GaugeMetric
import org.make.api.technical.MemoryMonitoringActor.Monitor

import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global

class MemoryMonitoringActor extends Actor {
  val memoryMax: GaugeMetric = Kamon.gauge("jvm.memory.max")
  val memoryFree: GaugeMetric = Kamon.gauge("jvm.memory.free")
  val memoryTotal: GaugeMetric = Kamon.gauge("jvm.memory.total")

  override def preStart(): Unit = {
    context.system.scheduler.schedule(1.second, 1.second, self, Monitor)
  }

  override def receive: Receive = {
    case Monitor =>
      memoryFree.set(Runtime.getRuntime.freeMemory())
      memoryMax.set(Runtime.getRuntime.maxMemory())
      memoryTotal.set(Runtime.getRuntime.totalMemory())
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
