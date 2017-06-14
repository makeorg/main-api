package org.make.api.user

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import org.make.core.user.UserCommand

object UserCoordinator {
  def props: Props = Props(new UserCoordinator)
  val name: String = "user-coordinator"
}

class UserCoordinator extends Actor {

  ClusterSharding(context.system).start(
    ShardedUser.shardName,
    ShardedUser.props,
    ClusterShardingSettings(context.system),
    ShardedUser.extractEntityId,
    ShardedUser.extractShardId
  )

  def shardedUser: ActorRef = {
    ClusterSharding(context.system).shardRegion(ShardedUser.shardName)
  }

  def receive: Receive = {
    case cmd: UserCommand => shardedUser.forward(cmd)
  }

}
