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

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, Scheduler, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.cluster.sharding.typed.{scaladsl, GetClusterShardingStats}
import akka.cluster.typed.Cluster
import akka.pattern.AskTimeoutException
import akka.util.Timeout
import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.Gauge
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

object ClusterShardingMonitor {
  trait ClusterShardingMonitorConfiguration {
    def refreshInterval: FiniteDuration
    def statsTimeout: FiniteDuration
    def shardedRegions: Seq[String]
  }

  object ClusterShardingMonitorConfiguration {
    def apply(config: Config): ClusterShardingMonitorConfiguration = {
      new ClusterShardingMonitorConfiguration {
        override val refreshInterval: FiniteDuration = {
          Duration(
            config.getDuration("kamon.cluster-sharding.refresh-interval").toMillis,
            scala.concurrent.duration.MILLISECONDS
          )
        }

        override val statsTimeout: FiniteDuration = {
          Duration(
            config.getDuration("kamon.cluster-sharding.stats-timeout").toMillis,
            scala.concurrent.duration.MILLISECONDS
          )
        }

        override val shardedRegions: Seq[String] = {
          config.getStringList("kamon.cluster-sharding.regions").asScala.toSeq
        }
      }
    }
  }

  def apply(): Behavior[Monitor.type] = {
    Behaviors
      .supervise(monitorClusterSharding())
      .onFailure(
        SupervisorStrategy.restartWithBackoff(minBackoff = 3.seconds, maxBackoff = 30.seconds, randomFactor = 0.2)
      )
  }

  private def monitorClusterSharding(): Behavior[Monitor.type] = {
    Behaviors.setup { context =>
      val configuration = ClusterShardingMonitorConfiguration(context.system.settings.config)
      val gauges: Map[String, ShardingGauges] = configuration.shardedRegions.map { region =>
        region -> new ShardingGauges(region)
      }.toMap

      implicit val timeout: Timeout = Timeout(configuration.statsTimeout * 2)
      implicit val scheduler: Scheduler = context.system.scheduler

      Behaviors.withTimers { timers =>
        timers.startTimerAtFixedRate(Monitor, configuration.refreshInterval)

        Behaviors.receiveMessage {
          case Monitor =>
            val shardingInfo = scaladsl.ClusterSharding(context.system)
            gauges.foreach {
              case (region, regionGauges) =>
                val statsFuture =
                  shardingInfo.shardState
                    .ask(GetClusterShardingStats(EntityTypeKey(region), configuration.statsTimeout, _))

                statsFuture.onComplete {
                  case Success(stats) =>
                    val allProposals = stats.regions.values.map(_.stats.values.sum).sum
                    regionGauges.totalActorsCount.update(allProposals)

                    val maybeRegion = stats.regions.get(Cluster(context.system).selfMember.address)
                    maybeRegion.foreach { shardStats =>
                      regionGauges.nodeActorsCount.update(shardStats.stats.values.sum)
                      regionGauges.nodeShardsCount.update(shardStats.stats.size)
                    }
                  case Failure(e: AskTimeoutException) if e.getMessage.contains("terminated") =>
                    context.log.warn(s"Unable to retrieve stats for $name due to terminated actor: ${e.getMessage}")
                  case Failure(e) => context.log.error(s"Unable to retrieve stats for $name:", e)
                }(context.executionContext)
            }
            Behaviors.same
        }
      }
    }
  }

  case object Monitor

  val name = "actor-sharding-monitor"

  class ShardingGauges(name: String) {
    val totalActorsCount: Gauge = Kamon.gauge("sharding-total-actors").withTag("region", name)
    val nodeActorsCount: Gauge = Kamon.gauge("sharding-node-actors").withTag("region", name)
    val nodeShardsCount: Gauge = Kamon.gauge("sharding-node-shards").withTag("region", name)
  }
}
