package org.make.api.sequence

import akka.actor.{Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess}
import org.make.core.DateHelper

import scala.concurrent.duration.DurationInt

object ShardedSequence {
  def props(dateHelper: DateHelper): Props = Props(new ShardedSequence(dateHelper = dateHelper))
  val shardName: String = "sequence"

  case object StopSequence

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: SequenceCommand => (cmd.sequenceId.value, cmd)
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case cmd: SequenceCommand        => Math.abs(cmd.sequenceId.value.hashCode % 100).toString
    case ShardRegion.StartEntity(id) => Math.abs(id.hashCode                   % 100).toString
  }
}

class ShardedSequence(dateHelper: DateHelper) extends SequenceActor(dateHelper) {

  import ShardedSequence._

  context.setReceiveTimeout(2.minutes)

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout                => context.parent ! Passivate(stopMessage = StopSequence)
    case StopSequence                  => context.stop(self)
    case SaveSnapshotSuccess(_)        => log.info("Snapshot saved")
    case SaveSnapshotFailure(_, cause) => log.error("Error while saving snapshot", cause)
  }
}
