package org.make.api.user

import akka.actor.{ActorLogging, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess}
import org.make.core.user.{UserCommand, UserId}

import scala.concurrent.duration._

object ShardedUser {
  def props: Props = Props(new ShardedUser)

  def name(userId: UserId): String = userId.value

  val shardName: String = "user"

  case object StopUser

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: UserCommand => (cmd.userId.value, cmd)
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case cmd: UserCommand =>
      Math.abs(cmd.userId.value.hashCode % 100).toString
    case ShardRegion.StartEntity(id) =>
      Math.abs(id.hashCode % 100).toString
  }

}

class ShardedUser extends UserActor with ActorLogging {

  import ShardedUser._

  context.setReceiveTimeout(2.minutes)

  override def unhandled(msg: Any): Unit = msg match {
    case ReceiveTimeout =>
      context.parent ! Passivate(stopMessage = StopUser)
    case StopUser            => context.stop(self)
    case SaveSnapshotSuccess(_) => log.info("Snapshot saved")
    case SaveSnapshotFailure(_, cause) =>
      log.error("Error while saving snapshot", cause)
  }

}
