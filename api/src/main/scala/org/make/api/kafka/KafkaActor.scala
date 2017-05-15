package org.make.api.kafka

import akka.actor.{Actor, Props, SupervisorStrategy}
import com.sksamuel.avro4s.RecordFormat
import org.make.api.kafka.ConsumerActor.Consume
import org.make.core.citizen.CitizenEvent.CitizenEventWrapper
import org.make.core.proposition.PropositionEvent.PropositionEventWrapper
import org.make.core.vote.VoteEvent.VoteEventWrapper

class KafkaActor extends Actor with AvroSerializers {


  override def preStart(): Unit = {
    context.watch(context.actorOf(CitizenProducerActor.props, CitizenProducerActor.name))
    context.watch(context.actorOf(PropositionProducerActor.props, PropositionProducerActor.name))
    context.watch(context.actorOf(VoteProducerActor.props, VoteProducerActor.name))

    val citizenConsumer = context.actorOf(ConsumerActor.props(RecordFormat[CitizenEventWrapper], "citizens"), "citizens-" + ConsumerActor.name)
    context.watch(citizenConsumer)
    citizenConsumer ! Consume

    val propositionConsumer = context.actorOf(ConsumerActor.props(RecordFormat[PropositionEventWrapper], "propositions"), "propositions-" + ConsumerActor.name)
    context.watch(propositionConsumer)
    propositionConsumer ! Consume

    val voteConsumer = context.actorOf(ConsumerActor.props(RecordFormat[VoteEventWrapper], "votes"), "votes-" + ConsumerActor.name)
    context.watch(voteConsumer)
    voteConsumer ! Consume
  }

  // Restart actors when they fail
  override def supervisorStrategy: SupervisorStrategy = SupervisorStrategy.defaultStrategy

  override def receive: Receive = {
    case x => println(s"received $x")
  }

}

object KafkaActor {
  val props: Props = Props[KafkaActor]
  val name: String = "kafka-guardian"
}
