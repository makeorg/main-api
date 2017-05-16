package org.make.api.vote

import akka.actor.{Actor, Props}
import com.sksamuel.avro4s.RecordFormat
import com.typesafe.scalalogging.StrictLogging
import org.make.api.kafka.ConsumerActor.Consume
import org.make.api.kafka.{AvroSerializers, ConsumerActor, VoteProducerActor}
import org.make.core.vote.VoteEvent.VoteEventWrapper

class VoteSupervisor extends Actor with AvroSerializers with StrictLogging {


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
    case x => logger.info(s"received $x")
  }

}

object VoteSupervisor {
  val name: String = "vote"
  val props: Props = Props[VoteSupervisor]
}