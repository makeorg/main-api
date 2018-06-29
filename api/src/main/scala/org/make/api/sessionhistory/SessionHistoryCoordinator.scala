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

package org.make.api.sessionhistory

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}

class SessionHistoryCoordinator(userHistoryCoordinator: ActorRef) extends Actor {
  ClusterSharding(context.system).start(
    ShardedSessionHistory.shardName,
    ShardedSessionHistory.props(userHistoryCoordinator),
    ClusterShardingSettings(context.system),
    ShardedSessionHistory.extractEntityId,
    ShardedSessionHistory.extractShardId
  )

  def shardedSessionHistory: ActorRef = {
    ClusterSharding(context.system).shardRegion(ShardedSessionHistory.shardName)
  }

  override def receive: Receive = {
    case cmd: SessionRelatedEvent => shardedSessionHistory.forward(cmd)
  }
}

object SessionHistoryCoordinator {
  def props(userHistoryCoordinator: ActorRef): Props = Props(new SessionHistoryCoordinator(userHistoryCoordinator))
  val name: String = "session-history-coordinator"
}
