package org.make.api.citizen

import akka.actor.{ActorLogging, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess}
import org.make.core.citizen.{CitizenCommand, CitizenId}

import scala.concurrent.duration._

object ShardedCitizen {
  def props: Props = Props(new ShardedCitizen)

  def name(citizenId: CitizenId): String = citizenId.value

  val shardName: String = "citizen"

  case object StopCitizen

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: CitizenCommand => (cmd.citizenId.value, cmd)
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case cmd: CitizenCommand =>
      Math.abs(cmd.citizenId.value.hashCode % 12).toString
  }

}

class ShardedCitizen extends CitizenActor with ActorLogging {

  import ShardedCitizen._

  context.setReceiveTimeout(2.minutes)

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout =>
      context.parent ! Passivate(stopMessage = StopCitizen)
    case StopCitizen => context.stop(self)
    case SaveSnapshotSuccess(_) => log.info("Snapshot saved")
    case SaveSnapshotFailure(_, cause) =>
      log.error("Error while saving snapshot", cause)
  }

}
