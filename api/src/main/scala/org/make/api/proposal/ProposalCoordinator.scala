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

package org.make.api.proposal

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import org.make.api.sessionhistory.SessionHistoryCoordinatorService

import scala.concurrent.duration.FiniteDuration

object ProposalCoordinator {
  def props(sessionHistoryCoordinatorService: SessionHistoryCoordinatorService, lockDuration: FiniteDuration): Props =
    Props(
      new ProposalCoordinator(
        sessionHistoryCoordinatorService = sessionHistoryCoordinatorService,
        lockDuration = lockDuration
      )
    )
  val name: String = "proposal-coordinator"
}

class ProposalCoordinator(sessionHistoryCoordinatorService: SessionHistoryCoordinatorService,
                          lockDuration: FiniteDuration)
    extends Actor {
  ClusterSharding(context.system).start(
    ShardedProposal.shardName,
    ShardedProposal
      .props(sessionHistoryCoordinatorService = sessionHistoryCoordinatorService, lockDuration = lockDuration),
    ClusterShardingSettings(context.system),
    ShardedProposal.extractEntityId,
    ShardedProposal.extractShardId
  )

  def shardedProposal: ActorRef = {
    ClusterSharding(context.system).shardRegion(ShardedProposal.shardName)
  }

  override def receive: Receive = {
    case cmd: ProposalCommand => shardedProposal.forward(cmd)
  }
}
