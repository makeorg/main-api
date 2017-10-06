package org.make.api.proposal

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}

object ProposalCoordinator {
  val props: Props = Props[ProposalCoordinator]
  val name: String = "proposal-coordinator"
}

class ProposalCoordinator extends Actor {
  ClusterSharding(context.system).start(
    ShardedProposal.shardName,
    ShardedProposal.props,
    ClusterShardingSettings(context.system),
    ShardedProposal.extractEntityId,
    ShardedProposal.extractShardId
  )

  def shardedProposal: ActorRef = {
    ClusterSharding(context.system).shardRegion(ShardedProposal.shardName)
  }

  override def receive: Receive = {
    case cmd: ProposalCommand => shardedProposal.forward(cmd)
  }
}
