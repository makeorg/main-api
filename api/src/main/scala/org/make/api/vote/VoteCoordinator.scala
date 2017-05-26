package org.make.api.vote

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import org.make.core.vote.VoteCommand

object VoteCoordinator {
  def props: Props = Props(new VoteActor)
  def name: String = "vote-coordinator"
}

class VoteCoordinator extends Actor {

  ClusterSharding(context.system).start(
    ShardedVote.shardName,
    ShardedVote.props,
    ClusterShardingSettings(context.system),
    ShardedVote.extractEntityId,
    ShardedVote.extractShardId
  )

  def shardedVote: ActorRef = {
    ClusterSharding(context.system).shardRegion(ShardedVote.shardName)
  }

  override def receive: Receive = {
    case cmd: VoteCommand => shardedVote forward cmd
  }
}
