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

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ShardRegion.{ClusterShardingStats, GetClusterShardingStats}
import akka.pattern.{ask, AskTimeoutException, Backoff, BackoffSupervisor}
import akka.util.Timeout
import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.GaugeMetric
import org.make.api.technical.ClusterShardingMonitor.{Monitor, ShardingGauges}

import scala.collection.JavaConverters._
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

class ClusterShardingMonitor extends Actor with ActorLogging {

  // TODO: Add fallback to reference.conf
  val config: Config = context.system.settings.config
  val refreshInterval: FiniteDuration =
    Duration(
      config.getDuration("kamon.cluster-sharding.refresh-interval").toMillis,
      scala.concurrent.duration.MILLISECONDS
    )

  val statsTimeout: FiniteDuration =
    Duration(
      config.getDuration("kamon.cluster-sharding.stats-timeout").toMillis,
      scala.concurrent.duration.MILLISECONDS
    )

  val shardedRegions: Seq[String] = config.getStringList("kamon.cluster-sharding.regions").asScala

  val gauges: Map[String, ShardingGauges] = shardedRegions.map { region =>
    region -> new ShardingGauges(region)
  }.toMap

  context.system.scheduler.schedule(refreshInterval, refreshInterval, self, Monitor)

  implicit val timeout: Timeout = Timeout(statsTimeout * 2)

  override def receive: Receive = {
    case Monitor =>
      val shardingInfo = ClusterSharding(context.system)
      gauges.foreach {
        case (region, regionGauges) =>
          val statsFuture = (shardingInfo.shardRegion(region) ? GetClusterShardingStats(statsTimeout))
            .mapTo[ClusterShardingStats]

          statsFuture.onComplete {
            case Success(stats) =>
              val allProposals = stats.regions.values.map(_.stats.values.sum).sum
              regionGauges.totalActorsCount.set(allProposals)

              val maybeRegion = stats.regions.get(Cluster(context.system).selfAddress)
              maybeRegion.foreach { shardStats =>
                regionGauges.nodeActorsCount.set(shardStats.stats.values.sum)
                regionGauges.nodeShardsCount.set(shardStats.stats.size)
              }
            case Failure(e: AskTimeoutException) if e.getMessage.contains("terminated") =>
              log.warning("Unable to retrieve stats due to terminated actor: {}", e.getMessage)
            case Failure(e) => log.error(e, "")
          }
      }

    case other =>
      log.info("Unknown message: {}", other)
  }
}

object ClusterShardingMonitor {

  case object Monitor

  val name = "actor-sharding-monitor-backoff"
  val props: Props = {
    BackoffSupervisor.props(
      Backoff.onStop(
        Props[ClusterShardingMonitor],
        childName = "actor-sharding-monitor",
        minBackoff = 3.seconds,
        maxBackoff = 30.seconds,
        randomFactor = 0.2
      )
    )
  }

  class ShardingGauges(name: String) {
    val totalActorsCount: GaugeMetric = Kamon.gauge(s"$name-actors")
    val nodeActorsCount: GaugeMetric = Kamon.gauge(s"node-$name-actors")
    val nodeShardsCount: GaugeMetric = Kamon.gauge(s"node-$name-shards")
  }
}
