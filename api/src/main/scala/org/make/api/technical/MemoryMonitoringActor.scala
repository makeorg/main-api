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

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import kamon.Kamon
import kamon.metric.Gauge

import scala.concurrent.duration.DurationInt

object MemoryMonitoringActor {
  def apply(): Behavior[Monitor.type] = {
    ActorSystemHelper.superviseWithBackoff(monitorMemory())
  }

  private def monitorMemory(): Behavior[Monitor.type] = {
    val memoryMax: Gauge = Kamon.gauge("jvm-memory-max").withoutTags()
    val memoryFree: Gauge = Kamon.gauge("jvm-memory-free").withoutTags()
    val memoryTotal: Gauge = Kamon.gauge("jvm-memory-total").withoutTags()

    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(Monitor, 1.second)
      Behaviors.receiveMessage {
        case Monitor =>
          memoryFree.update(Runtime.getRuntime.freeMemory().toDouble)
          memoryMax.update(Runtime.getRuntime.maxMemory().toDouble)
          memoryTotal.update(Runtime.getRuntime.totalMemory().toDouble)
          Behaviors.same
      }
    }
  }

  case object Monitor

  val name: String = "memory-monitor-backoff"
}
