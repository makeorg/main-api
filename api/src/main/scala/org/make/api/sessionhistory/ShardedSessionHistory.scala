package org.make.api.sessionhistory

import akka.actor.{ActorLogging, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess}
import org.make.api.sessionhistory.ShardedSessionHistory.StopSessionHistory
import org.make.core.session.{SessionHistoryAction, SessionHistoryEvent}

import scala.concurrent.duration._

class ShardedSessionHistory extends SessionHistoryActor with ActorLogging {

  context.setReceiveTimeout(2.minutes)

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout                => context.parent ! Passivate(stopMessage = StopSessionHistory)
    case StopSessionHistory            => context.stop(self)
    case SaveSnapshotSuccess(_)        => log.info("Snapshot saved")
    case SaveSnapshotFailure(_, cause) => log.error("Error while saving snapshot", cause)
  }
}

object ShardedSessionHistory {
  val props: Props = Props[ShardedSessionHistory]
  val shardName: String = "session-history"

  case object StopSessionHistory

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: SessionHistoryEvent[_] => (cmd.sessionId.value, cmd)
    case cmd: SessionHistoryAction   => (cmd.sessionId.value, cmd)
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case cmd: SessionHistoryEvent[_] => Math.abs(cmd.sessionId.hashCode % 100).toString
    case cmd: SessionHistoryAction   => Math.abs(cmd.sessionId.hashCode % 100).toString
    case ShardRegion.StartEntity(id) => Math.abs(id.hashCode            % 100).toString
  }

}
