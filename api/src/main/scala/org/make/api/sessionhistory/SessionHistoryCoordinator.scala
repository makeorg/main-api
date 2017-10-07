package org.make.api.sessionhistory

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}

class SessionHistoryCoordinator(userHistoryCoordinator: ActorRef) extends Actor {
  ClusterSharding(context.system).start(
    ShardedSessionHistory.shardName,
    ShardedSessionHistory.props(userHistoryCoordinator),
    ClusterShardingSettings(context.system),
    ShardedSessionHistory.extractEntityId,
    ShardedSessionHistory.extractShardId
  )

  def shardedSessionHistory: ActorRef = {
    ClusterSharding(context.system).shardRegion(ShardedSessionHistory.shardName)
  }

  override def receive: Receive = {
    case cmd: SessionHistoryEvent[_] => shardedSessionHistory.forward(cmd)
    case cmd: SessionHistoryAction   => shardedSessionHistory.forward(cmd)
  }
}

object SessionHistoryCoordinator {
  def props(userHistoryCoordinator: ActorRef): Props = Props(new SessionHistoryCoordinator(userHistoryCoordinator))
  val name: String = "session-history-coordinator"
}
