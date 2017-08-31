package org.make.api.userhistory

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import org.make.core.user.UserHistoryEvent

class UserHistoryCoordinator extends Actor {
  ClusterSharding(context.system).start(
    ShardedUserHistory.shardName,
    ShardedUserHistory.props,
    ClusterShardingSettings(context.system),
    ShardedUserHistory.extractEntityId,
    ShardedUserHistory.extractShardId
  )

  def shardedUserHistory: ActorRef = {
    ClusterSharding(context.system).shardRegion(ShardedUserHistory.shardName)
  }

  override def receive: Receive = {
    case cmd: UserHistoryEvent[_] => shardedUserHistory.forward(cmd)
  }
}

object UserHistoryCoordinator {
  val props: Props = Props[UserHistoryCoordinator]
  val name: String = "user-history-coordinator"
}
