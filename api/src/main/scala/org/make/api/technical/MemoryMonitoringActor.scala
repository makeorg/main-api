package org.make.api.technical

import akka.actor.{Actor, Props}
import akka.pattern.{Backoff, BackoffSupervisor}
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
    BackoffSupervisor.props(
      Backoff.onStop(
        Props[MemoryMonitoringActor],
        childName = "memory-monitor",
        minBackoff = 3.seconds,
        maxBackoff = 30.seconds,
        randomFactor = 0.2
      )
    )
  }
}
