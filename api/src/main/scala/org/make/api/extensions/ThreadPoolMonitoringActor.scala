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

package org.make.api.extensions

import akka.actor.typed.{Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import kamon.Kamon
import kamon.metric.Gauge
import org.make.api.technical.MonitorableExecutionContext

import scala.concurrent.duration.DurationInt

object ThreadPoolMonitoringActor {
  def apply(): Behavior[Protocol] = {
    Behaviors.supervise(monitorThreadPools()).onFailure(SupervisorStrategy.resume)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def monitorThreadPools(executors: Map[String, ExecutorWithGauges] = Map.empty): Behavior[Protocol] = {
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(Monitor, 1.second)
      Behaviors.receiveMessage {
        case Monitor =>
          executors.foreach {
            case (_, executorAndGauges) =>
              val executor = executorAndGauges.executor
              executorAndGauges.activeTasks.update(executor.activeTasks)
              executorAndGauges.currentTasks.update(executor.currentTasks)
              executorAndGauges.maxTasks.update(executor.maxTasks)
              executorAndGauges.waitingTasks.update(executor.waitingTasks)
          }
          Behaviors.same
        case MonitorThreadPool(newExecutor, name) =>
          val executorWithGauges = ExecutorWithGauges(
            newExecutor,
            Kamon.gauge("executors-active-threads").withTag("name", name),
            Kamon.gauge("executors-core-size").withTag("name", name),
            Kamon.gauge("executors-max-threads").withTag("name", name),
            Kamon.gauge("executors-waiting").withTag("name", name)
          )
          monitorThreadPools(executors + (name -> executorWithGauges))
      }
    }
  }
  sealed trait Protocol
  case object Monitor extends Protocol
  final case class MonitorThreadPool(pool: MonitorableExecutionContext, name: String) extends Protocol

  final case class ExecutorWithGauges(
    executor: MonitorableExecutionContext,
    activeTasks: Gauge,
    currentTasks: Gauge,
    maxTasks: Gauge,
    waitingTasks: Gauge
  )

  val name: String = "ThreadPoolMonitoringActor"

}
