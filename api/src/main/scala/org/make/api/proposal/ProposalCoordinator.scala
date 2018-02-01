package org.make.api.proposal

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}

object ProposalCoordinator {
  def props(userHistoryActor: ActorRef, sessionHistoryActor: ActorRef): Props =
    Props(new ProposalCoordinator(userHistoryActor = userHistoryActor, sessionHistoryActor = sessionHistoryActor))
  val name: String = "proposal-coordinator"
}

class ProposalCoordinator(userHistoryActor: ActorRef, sessionHistoryActor: ActorRef) extends Actor {
  ClusterSharding(context.system).start(
    ShardedProposal.shardName,
    ShardedProposal.props(userHistoryActor = userHistoryActor, sessionHistoryActor = sessionHistoryActor),
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
