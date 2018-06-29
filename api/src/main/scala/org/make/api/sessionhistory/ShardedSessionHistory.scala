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

import akka.actor.{ActorLogging, ActorRef, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess}
import org.make.api.sessionhistory.ShardedSessionHistory.StopSessionHistory

import scala.concurrent.duration.DurationInt

class ShardedSessionHistory(userHistoryCoordinator: ActorRef)
    extends SessionHistoryActor(userHistoryCoordinator)
    with ActorLogging {

  context.setReceiveTimeout(20.minutes)

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout                => context.parent ! Passivate(stopMessage = StopSessionHistory)
    case StopSessionHistory            => context.stop(self)
    case SaveSnapshotSuccess(_)        => log.debug("Snapshot saved")
    case SaveSnapshotFailure(_, cause) => log.error("Error while saving snapshot", cause)
  }
}

object ShardedSessionHistory {
  def props(userHistoryCoordinator: ActorRef): Props = Props(new ShardedSessionHistory(userHistoryCoordinator))
  val shardName: String = "session-history"

  case object StopSessionHistory

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: SessionRelatedEvent => (cmd.sessionId.value, cmd)
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case cmd: SessionRelatedEvent    => Math.abs(cmd.sessionId.value.hashCode % 100).toString
    case ShardRegion.StartEntity(id) => Math.abs(id.hashCode                  % 100).toString
  }

}
