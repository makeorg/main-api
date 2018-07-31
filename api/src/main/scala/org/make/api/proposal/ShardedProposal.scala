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

import akka.actor.{ActorRef, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess}
import org.make.api.technical.MakePersistentActor.Snapshot

import scala.concurrent.duration.DurationInt

object ShardedProposal {
  def props(sessionHistoryActor: ActorRef): Props =
    Props(new ShardedProposal(sessionHistoryActor = sessionHistoryActor))
  val shardName: String = "proposal"

  case object StopProposal

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: ProposalCommand => (cmd.proposalId.value, cmd)
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case cmd: ProposalCommand        => Math.abs(cmd.proposalId.value.hashCode % 100).toString
    case ShardRegion.StartEntity(id) => Math.abs(id.hashCode                   % 100).toString
  }

  val readJournal: String = "make-api.event-sourcing.proposals.read-journal"
  val snapshotStore: String = "make-api.event-sourcing.proposals.snapshot-store"
  val queryJournal: String = "make-api.event-sourcing.proposals.query-journal"
}

class ShardedProposal(sessionHistoryActor: ActorRef) extends ProposalActor(sessionHistoryActor = sessionHistoryActor) {

  import ShardedProposal._

  context.setReceiveTimeout(30.minutes)

  override def journalPluginId: String = ShardedProposal.readJournal
  override def snapshotPluginId: String = ShardedProposal.snapshotStore

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout =>
      self ! Snapshot
      context.parent ! Passivate(stopMessage = StopProposal)
    case StopProposal                  => context.stop(self)
    case SaveSnapshotSuccess(snapshot) => log.debug(s"Snapshot saved: $snapshot")
    case SaveSnapshotFailure(_, cause) => log.error(cause, "Error while saving snapshot")
  }
}
