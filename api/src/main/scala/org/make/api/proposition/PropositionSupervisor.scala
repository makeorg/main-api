package org.make.api.proposition

import akka.actor.{Actor, Props}
import akka.stream.ActorMaterializer
import com.sksamuel.avro4s.RecordFormat
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ShortenedNames
import org.make.api.elasticsearch.ElasticsearchConfigurationExtension
import org.make.api.kafka.ConsumerActor.Consume
import org.make.api.kafka.{AvroSerializers, ConsumerActor, PropositionProducerActor}
import org.make.core.proposition.PropositionEvent.PropositionEventWrapper

import scala.util.{Failure, Success}

class PropositionSupervisor extends Actor
  with StrictLogging with ElasticsearchConfigurationExtension
  with AvroSerializers with ShortenedNames {


  override def preStart(): Unit = {

    context.watch(context.actorOf(PropositionCoordinator.props, PropositionCoordinator.name))

    context.watch(context.actorOf(PropositionProducerActor.props, PropositionProducerActor.name))

    val propositionConsumer = context.actorOf(
      ConsumerActor.props(RecordFormat[PropositionEventWrapper], "propositions"),
      ConsumerActor.name("propositions")
    )
    context.watch(propositionConsumer)
    propositionConsumer ! Consume

    implicit val materializer = ActorMaterializer()(context.system)

    val propositionGraph = PropositionStreamToElasticsearch.stream(context.system, materializer)
      .run(elasticsearchConfiguration.esApi)

    propositionGraph.onComplete {
      case Success(result) => logger.debug("Stream processed: {}", result)
      case Failure(e) => logger.warn("Failure in stream", e)
    }(ECGlobal)

  }

  override def receive: Receive = {
    case x => logger.info(s"received $x")
  }
}

object PropositionSupervisor {

  val name: String = "proposition"
  val props: Props = Props[PropositionSupervisor]
}