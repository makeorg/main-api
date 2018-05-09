package org.make.api.proposal

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}

object ProposalCoordinator {
  def props(sessionHistoryActor: ActorRef): Props =
    Props(new ProposalCoordinator(sessionHistoryActor = sessionHistoryActor))
  val name: String = "proposal-coordinator"
}

class ProposalCoordinator(sessionHistoryActor: ActorRef) extends Actor {
  ClusterSharding(context.system).start(
    ShardedProposal.shardName,
    ShardedProposal.props(sessionHistoryActor = sessionHistoryActor),
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
