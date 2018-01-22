package org.make.api.extensions

import akka.actor.{Actor, Props}
import akka.pattern.{Backoff, BackoffSupervisor}
import kamon.Kamon
import kamon.metric.GaugeMetric
import org.make.api.extensions.ThreadPoolMonitoringActor.{ExecutorWithGauges, Monitor, MonitorThreadPool}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class ThreadPoolMonitoringActor extends Actor {
  private var monitoredPools: Map[String, ExecutorWithGauges] = Map.empty

  override def preStart(): Unit = {
    context.system.scheduler.schedule(1.second, 1.second, self, Monitor)
  }

  override def receive: Receive = {
    case Monitor =>
      monitoredPools.foreach {
        case (_, executorAndGauges) =>
          val executor = executorAndGauges.executor
          executorAndGauges.activeTasks.set(executor.activeTasks)
          executorAndGauges.currentTasks.set(executor.currentTasks)
          executorAndGauges.maxTasks.set(executor.maxTasks)
          executorAndGauges.waitingTasks.set(executor.waitingTasks)
      }
    case MonitorThreadPool(newExecutor, name) =>
      monitoredPools += name -> ExecutorWithGauges(
        newExecutor,
        Kamon.gauge(s"executors.$name.active"),
        Kamon.gauge(s"executors.$name.core-size"),
        Kamon.gauge(s"executors.$name.max"),
        Kamon.gauge(s"executors.$name.waiting")
      )
  }
}

object ThreadPoolMonitoringActor {
  val innerName: String = "ThreadPoolMonitoringActor"
  val innerProps: Props = Props[ThreadPoolMonitoringActor]

  val name: String = "BackoffThreadPoolMonitoringActor"
  val props: Props = BackoffSupervisor.props(
    Backoff
      .onStop(innerProps, childName = innerName, minBackoff = 3.seconds, maxBackoff = 30.seconds, randomFactor = 0.2)
  )

  case object Monitor
  case class MonitorThreadPool(pool: MonitorableExecutionContext, name: String)

  case class ExecutorWithGauges(executor: MonitorableExecutionContext,
                                activeTasks: GaugeMetric,
                                currentTasks: GaugeMetric,
                                maxTasks: GaugeMetric,
                                waitingTasks: GaugeMetric)
}
