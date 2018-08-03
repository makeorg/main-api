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

import akka.actor.{Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess}
import org.make.api.technical.MakePersistentActor.StartShard
import org.make.core.DateHelper

import scala.concurrent.duration.DurationInt

object ShardedSequence {
  def props(dateHelper: DateHelper): Props = Props(new ShardedSequence(dateHelper = dateHelper))
  val shardName: String = "sequence"

  case object StopSequence

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: SequenceCommand => (cmd.sequenceId.value, cmd)
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case StartShard(shardId)         => shardId
    case cmd: SequenceCommand        => Math.abs(cmd.sequenceId.value.hashCode % 100).toString
    case ShardRegion.StartEntity(id) => Math.abs(id.hashCode % 100).toString
  }

  val readJournal: String = "make-api.event-sourcing.sequences.read-journal"
  val snapshotStore: String = "make-api.event-sourcing.sequences.snapshot-store"
  val queryJournal: String = "make-api.event-sourcing.sequences.query-journal"

}

class ShardedSequence(dateHelper: DateHelper) extends SequenceActor(dateHelper) {

  import ShardedSequence._

  context.setReceiveTimeout(20.minutes)

  override def journalPluginId: String = ShardedSequence.readJournal
  override def snapshotPluginId: String = ShardedSequence.snapshotStore

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout                => context.parent ! Passivate(stopMessage = StopSequence)
    case StopSequence                  => context.stop(self)
    case SaveSnapshotSuccess(_)        => log.debug("Snapshot saved")
    case SaveSnapshotFailure(_, cause) => log.error("Error while saving snapshot", cause)
  }
}
