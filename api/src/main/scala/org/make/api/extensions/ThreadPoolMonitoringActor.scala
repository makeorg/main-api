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

import akka.actor.{Actor, Props}
import akka.pattern.{BackoffOpts, BackoffSupervisor}
import kamon.Kamon
import kamon.metric.Gauge
import org.make.api.extensions.ThreadPoolMonitoringActor.{ExecutorWithGauges, Monitor, MonitorThreadPool}
import org.make.api.technical.MonitorableExecutionContext

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class ThreadPoolMonitoringActor extends Actor {
  private var monitoredPools: Map[String, ExecutorWithGauges] = Map.empty

  override def preStart(): Unit = {
    context.system.scheduler.scheduleWithFixedDelay(1.second, 1.second, self, Monitor)
  }

  override def receive: Receive = {
    case Monitor =>
      monitoredPools.foreach {
        case (_, executorAndGauges) =>
          val executor = executorAndGauges.executor
          executorAndGauges.activeTasks.update(executor.activeTasks)
          executorAndGauges.currentTasks.update(executor.currentTasks)
          executorAndGauges.maxTasks.update(executor.maxTasks)
          executorAndGauges.waitingTasks.update(executor.waitingTasks)
      }
    case MonitorThreadPool(newExecutor, name) =>
      monitoredPools += name -> ExecutorWithGauges(
        newExecutor,
        Kamon.gauge("executors-active-threads").withTag("name", name),
        Kamon.gauge("executors-core-size").withTag("name", name),
        Kamon.gauge("executors-max-threads").withTag("name", name),
        Kamon.gauge("executors-waiting").withTag("name", name)
      )
  }
}

object ThreadPoolMonitoringActor {
  val innerName: String = "ThreadPoolMonitoringActor"
  val innerProps: Props = Props[ThreadPoolMonitoringActor]

  val name: String = "BackoffThreadPoolMonitoringActor"
  private val maxNrOfRetries = 50
  val props: Props = BackoffSupervisor.props(
    BackoffOpts
      .onStop(innerProps, childName = innerName, minBackoff = 3.seconds, maxBackoff = 30.seconds, randomFactor = 0.2)
      .withMaxNrOfRetries(maxNrOfRetries)
  )

  case object Monitor
  case class MonitorThreadPool(pool: MonitorableExecutionContext, name: String)

  case class ExecutorWithGauges(executor: MonitorableExecutionContext,
                                activeTasks: Gauge,
                                currentTasks: Gauge,
                                maxTasks: Gauge,
                                waitingTasks: Gauge)
}
