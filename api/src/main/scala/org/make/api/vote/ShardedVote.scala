package org.make.api.vote

import akka.actor.{Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess}
import org.make.core.vote.{VoteCommand, VoteId}

import scala.concurrent.duration._

object ShardedVote {
  def props: Props = Props(new ShardedVote)

  def name(voteId: VoteId): String = voteId.value

  val shardName: String = "vote"

  case object StopVote

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: VoteCommand => (cmd.propositionId.value, cmd)
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case cmd: VoteCommand => Math.abs(cmd.propositionId.value.hashCode % 12).toString
  }
}

class ShardedVote extends VoteActor {

  import ShardedVote._

  context.setReceiveTimeout(2.minutes)

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = StopVote)
    case StopVote => context.stop(self)
    case SaveSnapshotSuccess(_) => log.info("Snapshot saved")
    case SaveSnapshotFailure(_, cause) =>
      log.error("Error while saving snapshot", cause)
  }
}