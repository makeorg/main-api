package org.make.api.proposal

import akka.actor.{Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess}

import scala.concurrent.duration._

object ShardedProposal {
  val props: Props = Props(new ShardedProposal)
  val shardName: String = "proposal"

  case object StopProposal

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: ProposalCommand => (cmd.proposalId.value, cmd)
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case cmd: ProposalCommand        => Math.abs(cmd.proposalId.value.hashCode % 100).toString
    case ShardRegion.StartEntity(id) => Math.abs(id.hashCode                   % 100).toString
  }
}

class ShardedProposal extends ProposalActor {

  import ShardedProposal._

  context.setReceiveTimeout(2.minutes)

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout                => context.parent ! Passivate(stopMessage = StopProposal)
    case StopProposal                  => context.stop(self)
    case SaveSnapshotSuccess(snapshot) => log.info(s"Snapshot saved: $snapshot")
    case SaveSnapshotFailure(_, cause) => log.error(cause, "Error while saving snapshot")
  }
}
