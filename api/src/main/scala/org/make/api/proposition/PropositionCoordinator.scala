package org.make.api.proposition

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import org.make.core.proposition.PropositionCommand

object PropositionCoordinator {
  def props: Props = Props[PropositionCoordinator]
  def name: String = "proposition-coordinator"
}

class PropositionCoordinator extends Actor {
  ClusterSharding(context.system).start(
    ShardedProposition.shardName,
    ShardedProposition.props,
    ClusterShardingSettings(context.system),
    ShardedProposition.extractEntityId,
    ShardedProposition.extractShardId
  )

  def shardedProposition: ActorRef = {
    ClusterSharding(context.system).shardRegion(ShardedProposition.shardName)
  }

  override def receive: Receive = {
    case cmd: PropositionCommand => shardedProposition forward cmd
  }
}