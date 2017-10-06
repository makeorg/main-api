package org.make.api.sessionhistory

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import org.make.core.session.{SessionHistoryAction, SessionHistoryEvent}

class SessionHistoryCoordinator extends Actor {
  ClusterSharding(context.system).start(
    ShardedSessionHistory.shardName,
    ShardedSessionHistory.props,
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
  val props: Props = Props[SessionHistoryCoordinator]
  val name: String = "session-history-coordinator"
}
