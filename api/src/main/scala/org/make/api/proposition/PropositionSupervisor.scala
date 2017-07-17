package org.make.api.proposition

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.ActorMaterializer
import org.make.api.technical.elasticsearch.{ElasticsearchAPIComponent, ElasticsearchConfigurationExtension}
import org.make.api.technical.{AvroSerializers, ShortenedNames}

import scala.util.{Failure, Success}

class PropositionSupervisor
    extends Actor
    with ActorLogging
    with ElasticsearchConfigurationExtension
    with ElasticsearchAPIComponent
    with PropositionStreamToElasticsearchComponent
    with AvroSerializers
    with ShortenedNames {

  override val elasticsearchAPI =
    new ElasticsearchAPI(elasticsearchConfiguration.host, elasticsearchConfiguration.port)

  implicit private val materializer = ActorMaterializer()(context.system)
  val propositionStreamToElasticsearch: PropositionStreamToElasticsearch =
    new PropositionStreamToElasticsearch(context.system, materializer)

  override def preStart(): Unit = {

    context.watch(
      context
        .actorOf(PropositionCoordinator.props, PropositionCoordinator.name)
    )
    context.watch(
      context
        .actorOf(PropositionProducerActor.props, PropositionProducerActor.name)
    )
    propositionStreamToElasticsearch
      .run()
      .onComplete {
        case Success(result) => log.debug("Stream processed: {}", result)
        case Failure(e)      => log.warning("Failure in stream", e)
      }(ECGlobal)

  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }
}

object PropositionSupervisor {

  val name: String = "proposition"
  val props: Props = Props[PropositionSupervisor]
}
