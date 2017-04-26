package org.make.api.citizen

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import org.make.core.citizen.CitizenCommand

object CitizenActors {
  def props: Props = Props(new CitizenActors)
  val name: String = "citizen-manager"
}

class CitizenActors extends Actor {

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
