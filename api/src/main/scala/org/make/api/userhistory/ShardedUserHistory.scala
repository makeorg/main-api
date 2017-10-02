package org.make.api.userhistory

import akka.actor.{ActorLogging, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess}
import org.make.api.userhistory.ShardedUserHistory.StopUserHistory
import org.make.api.userhistory.UserHistoryActor.RequestVoteValues
import org.make.core.user.{UserHistoryAction, UserHistoryEvent}

import scala.concurrent.duration._

class ShardedUserHistory extends UserHistoryActor with ActorLogging {

  context.setReceiveTimeout(2.minutes)

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout                => context.parent ! Passivate(stopMessage = StopUserHistory)
    case StopUserHistory               => context.stop(self)
    case SaveSnapshotSuccess(_)        => log.info("Snapshot saved")
    case SaveSnapshotFailure(_, cause) => log.error(cause, "Error while saving snapshot")
  }
}

object ShardedUserHistory {
  val props: Props = Props[ShardedUserHistory]
  val shardName: String = "user-history"

  case object StopUserHistory

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: UserHistoryEvent[_] => (cmd.userId.value, cmd)
    case cmd: UserHistoryAction   => (cmd.userId.value, cmd)
    case cmd: RequestVoteValues   => (cmd.userId.value, cmd)
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case cmd: UserHistoryEvent[_]    => Math.abs(cmd.userId.value.hashCode % 100).toString
    case cmd: UserHistoryAction      => Math.abs(cmd.userId.value.hashCode % 100).toString
    case cmd: RequestVoteValues      => Math.abs(cmd.userId.value.hashCode % 100).toString
    case ShardRegion.StartEntity(id) => Math.abs(id.hashCode               % 100).toString
  }

}
