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

package org.make.api.technical.job

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import org.make.api.technical.job.JobActor.Protocol.Command

import scala.concurrent.duration.Duration

class JobCoordinator(heartRate: Duration) extends Actor {
  ClusterSharding(context.system).start(
    ShardedJob.shardName,
    ShardedJob.props(heartRate),
    ClusterShardingSettings(context.system),
    ShardedJob.extractEntityId,
    ShardedJob.extractShardId
  )

  override def receive: Receive = {
    case cmd: Command => shardedJob.forward(cmd)
  }

  def shardedJob: ActorRef = {
    ClusterSharding(context.system).shardRegion(ShardedJob.shardName)
  }
}

object JobCoordinator {
  val name = "job-coordinator"
  def props(heartRate: Duration): Props = Props(new JobCoordinator(heartRate))
}
