package org.make.api.sessionhistory

import akka.actor.{ActorLogging, ActorRef, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess}
import org.make.api.sessionhistory.ShardedSessionHistory.StopSessionHistory

import scala.concurrent.duration._

class ShardedSessionHistory(userHistoryCoordinator: ActorRef)
    extends SessionHistoryActor(userHistoryCoordinator)
    with ActorLogging {

  context.setReceiveTimeout(2.minutes)

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout                => context.parent ! Passivate(stopMessage = StopSessionHistory)
    case StopSessionHistory            => context.stop(self)
    case SaveSnapshotSuccess(_)        => log.debug("Snapshot saved")
    case SaveSnapshotFailure(_, cause) => log.error("Error while saving snapshot", cause)
  }
}

object ShardedSessionHistory {
  def props(userHistoryCoordinator: ActorRef): Props = Props(new ShardedSessionHistory(userHistoryCoordinator))
  val shardName: String = "session-history"

  case object StopSessionHistory

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: SessionRelatedEvent => (cmd.sessionId.value, cmd)
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case cmd: SessionRelatedEvent    => Math.abs(cmd.sessionId.value.hashCode % 100).toString
    case ShardRegion.StartEntity(id) => Math.abs(id.hashCode                  % 100).toString
  }

}
