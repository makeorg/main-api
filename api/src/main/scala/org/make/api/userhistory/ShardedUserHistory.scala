package org.make.api.userhistory

import akka.actor.{ActorLogging, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess}
import org.make.api.userhistory.ShardedUserHistory.StopUserHistory

import scala.concurrent.duration._

class ShardedUserHistory extends UserHistoryActor with ActorLogging {

  context.setReceiveTimeout(2.minutes)

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout                => context.parent ! Passivate(stopMessage = StopUserHistory)
    case StopUserHistory               => context.stop(self)
    case SaveSnapshotSuccess(_)        => log.debug("Snapshot saved")
    case SaveSnapshotFailure(_, cause) => log.error(cause, "Error while saving snapshot")
  }
}

object ShardedUserHistory {
  val props: Props = Props[ShardedUserHistory]
  val shardName: String = "user-history"

  case object StopUserHistory

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: UserRelatedEvent => (cmd.userId.value, cmd)
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case cmd: UserRelatedEvent       => Math.abs(cmd.userId.value.hashCode % 100).toString
    case ShardRegion.StartEntity(id) => Math.abs(id.hashCode               % 100).toString
  }

}
