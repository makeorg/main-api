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
import org.make.api.extensions.MakeSettingsExtension
import org.make.api.sessionhistory.ShardedSessionHistory.StopSessionHistory
import org.make.api.technical.MakePersistentActor.StartShard

import scala.concurrent.duration.FiniteDuration

class ShardedSessionHistory(userHistoryCoordinator: ActorRef, lockDuration: FiniteDuration)
    extends SessionHistoryActor(userHistoryCoordinator, lockDuration)
    with ActorLogging
    with MakeSettingsExtension {

  context.setReceiveTimeout(settings.SessionCookie.lifetime)

  override def journalPluginId: String = ShardedSessionHistory.readJournal
  override def snapshotPluginId: String = ShardedSessionHistory.snapshotStore

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout                => context.parent ! Passivate(stopMessage = StopSessionHistory)
    case StopSessionHistory            => stopSessionHistoryActor()
    case SaveSnapshotSuccess(_)        => log.debug("Snapshot saved")
    case SaveSnapshotFailure(_, cause) => log.error("Error while saving snapshot", cause)
  }
}

object ShardedSessionHistory {
  val readJournal: String = "make-api.event-sourcing.sessions.read-journal"
  val snapshotStore: String = "make-api.event-sourcing.sessions.snapshot-store"
  val queryJournal: String = "make-api.event-sourcing.sessions.query-journal"

  def props(userHistoryCoordinator: ActorRef, lockDuration: FiniteDuration): Props =
    Props(new ShardedSessionHistory(userHistoryCoordinator, lockDuration))
  val shardName: String = "session-history"

  case object StopSessionHistory

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: SessionRelatedEvent => (cmd.sessionId.value, cmd)
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case StartShard(shardId)         => shardId
    case cmd: SessionRelatedEvent    => Math.abs(cmd.sessionId.value.hashCode % 100).toString
    case ShardRegion.StartEntity(id) => Math.abs(id.hashCode % 100).toString
  }

}
