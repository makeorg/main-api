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

package org.make.api.technical.job

import akka.actor.{Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess}
import org.make.api.technical.MakePersistentActor.{Snapshot, StartShard}
import org.make.api.technical.job.JobActor.Protocol.Command
import org.make.api.technical.job.ShardedJob.StopJob

import scala.concurrent.duration.{Duration, DurationInt}

class ShardedJob(heartRate: Duration) extends JobActor(heartRate) {

  context.setReceiveTimeout(30.minutes)

  override def journalPluginId: String = ShardedJob.readJournal
  override def snapshotPluginId: String = ShardedJob.snapshotStore

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout =>
      self ! Snapshot
      context.parent ! Passivate(stopMessage = StopJob)
    case StopJob                       => context.stop(self)
    case SaveSnapshotSuccess(snapshot) => log.debug(s"Snapshot saved: $snapshot")
    case SaveSnapshotFailure(_, cause) => log.error(cause, "Error while saving snapshot")
  }
}

object ShardedJob {

  val shardName: String = "job"
  val readJournal: String = "make-api.event-sourcing.jobs.read-journal"
  val snapshotStore: String = "make-api.event-sourcing.jobs.snapshot-store"
  val queryJournal: String = "make-api.event-sourcing.jobs.query-journal"

  def props(heartRate: Duration): Props = Props(new ShardedJob(heartRate))

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: Command => (cmd.id.value, cmd)
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case StartShard(shardId)         => shardId
    case cmd: Command                => Math.abs(cmd.id.value.hashCode % 100).toString
    case ShardRegion.StartEntity(id) => Math.abs(id.hashCode % 100).toString
  }

  case object StopJob

}
