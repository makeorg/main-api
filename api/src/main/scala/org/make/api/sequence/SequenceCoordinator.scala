package org.make.api.sequence

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import org.make.core.DateHelper

class SequenceCoordinator(dateHelper: DateHelper) extends Actor {
  ClusterSharding(context.system).start(
    ShardedSequence.shardName,
    ShardedSequence.props(dateHelper = dateHelper),
    ClusterShardingSettings(context.system),
    ShardedSequence.extractEntityId,
    ShardedSequence.extractShardId
  )

  def shardedSequence: ActorRef = {
    ClusterSharding(context.system).shardRegion(ShardedSequence.shardName)
  }

  override def receive: Receive = {
    case cmd: SequenceCommand => shardedSequence.forward(cmd)
  }
}

object SequenceCoordinator {
  def props(dateHelper: DateHelper): Props = Props(new SequenceCoordinator(dateHelper = dateHelper))
  val name: String = "sequence-coordinator"
}
