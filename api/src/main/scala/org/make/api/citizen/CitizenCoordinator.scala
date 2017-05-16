package org.make.api.citizen

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import org.make.core.citizen.CitizenCommand

object CitizenCoordinator {
  def props: Props = Props(new CitizenCoordinator)
  val name: String = "citizen-coordinator"
}

class CitizenCoordinator extends Actor {

  ClusterSharding(context.system).start(
    ShardedCitizen.shardName,
    ShardedCitizen.props,
    ClusterShardingSettings(context.system),
    ShardedCitizen.extractEntityId,
    ShardedCitizen.extractShardId
  )

  def shardedCitizen: ActorRef = {
    ClusterSharding(context.system).shardRegion(ShardedCitizen.shardName)
  }

  def receive: Receive = {
    case cmd: CitizenCommand => shardedCitizen forward cmd
  }

}
