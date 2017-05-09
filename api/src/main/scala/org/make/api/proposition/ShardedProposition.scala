package org.make.api.proposition

import akka.actor.{Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess}
import org.make.core.proposition.{PropositionCommand, PropositionId}

import scala.concurrent.duration._

object ShardedProposition {
  def props: Props = Props(new ShardedProposition)

  def name(propositionId: PropositionId): String = propositionId.value

  val shardName: String = "proposition"

  case object StopProposition

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: PropositionCommand => (cmd.propositionId.value, cmd)
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case cmd: PropositionCommand => Math.abs(cmd.propositionId.value.hashCode % 12).toString
  }
}

class ShardedProposition extends PropositionActor {

  import ShardedProposition._

  context.setReceiveTimeout(2.minutes)

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = StopProposition)
    case StopProposition => context.stop(self)
    case SaveSnapshotSuccess(_) => logger.info("Snapshot saved")
    case SaveSnapshotFailure(_, cause) =>
      logger.error("Error while saving snapshot", cause)
  }
}