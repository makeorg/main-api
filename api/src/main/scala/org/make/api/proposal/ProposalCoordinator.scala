package org.make.api.proposal

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import org.make.api.operation.OperationService

object ProposalCoordinator {
  def props(userHistoryActor: ActorRef,
            sessionHistoryActor: ActorRef,
            sequenceActor: ActorRef,
            operationService: OperationService): Props =
    Props(
      new ProposalCoordinator(
        userHistoryActor = userHistoryActor,
        sessionHistoryActor = sessionHistoryActor,
        sequenceActor = sequenceActor,
        operationService = operationService
      )
    )
  val name: String = "proposal-coordinator"
}

class ProposalCoordinator(userHistoryActor: ActorRef,
                          sessionHistoryActor: ActorRef,
                          sequenceActor: ActorRef,
                          operationService: OperationService)
    extends Actor {
  ClusterSharding(context.system).start(
    ShardedProposal.shardName,
    ShardedProposal.props(
      userHistoryActor = userHistoryActor,
      sessionHistoryActor = sessionHistoryActor,
      sequenceActor = sequenceActor,
      operationService = operationService
    ),
    ClusterShardingSettings(context.system),
    ShardedProposal.extractEntityId,
    ShardedProposal.extractShardId
  )

  def shardedProposal: ActorRef = {
    ClusterSharding(context.system).shardRegion(ShardedProposal.shardName)
  }

  override def receive: Receive = {
    case cmd: ProposalCommand => shardedProposal.forward(cmd)
  }
}
