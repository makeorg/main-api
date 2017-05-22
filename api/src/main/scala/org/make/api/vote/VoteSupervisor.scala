package org.make.api.vote

import akka.actor.{Actor, ActorLogging, Props}
import com.sksamuel.avro4s.RecordFormat
import org.make.api.technical.ConsumerActor.Consume
import org.make.api.technical.{AvroSerializers, ConsumerActor}
import org.make.core.vote.VoteEvent.VoteEventWrapper

class VoteSupervisor extends Actor with AvroSerializers with ActorLogging {


  override def preStart(): Unit = {
    context.watch(context.actorOf(VoteCoordinator.props, VoteCoordinator.name))

    context.watch(context.actorOf(VoteProducerActor.props, VoteProducerActor.name))

    val voteConsumer = context.actorOf(
      ConsumerActor.props(RecordFormat[VoteEventWrapper], "votes"),
      ConsumerActor.name("votes")
    )
    context.watch(voteConsumer)
    voteConsumer ! Consume
  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }

}

object VoteSupervisor {
  val name: String = "vote"
  val props: Props = Props[VoteSupervisor]
}