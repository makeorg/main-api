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

package org.make.api.sequence

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import org.make.core.DateHelper

class SequenceCoordinator(dateHelper: DateHelper) extends Actor {
  ClusterSharding(context.system).start(
    ShardedSequence.shardName,
    ShardedSequence.props(dateHelper = dateHelper),
    ClusterShardingSettings(context.system),
    ShardedSequence.extractEntityId,
    ShardedSequence.extractShardId
  )

  def shardedSequence: ActorRef = {
    ClusterSharding(context.system).shardRegion(ShardedSequence.shardName)
  }

  override def receive: Receive = {
    case cmd: SequenceCommand => shardedSequence.forward(cmd)
  }
}

object SequenceCoordinator {
  def props(dateHelper: DateHelper): Props = Props(new SequenceCoordinator(dateHelper = dateHelper))
  val name: String = "sequence-coordinator"
}
