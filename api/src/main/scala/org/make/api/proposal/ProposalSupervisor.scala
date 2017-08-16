package org.make.api.proposal

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.ActorMaterializer
import org.make.api.technical.elasticsearch.{DefaultElasticsearchAPIComponent, ElasticsearchConfigurationExtension}
import org.make.api.technical.{AvroSerializers, ShortenedNames}

import scala.util.{Failure, Success}

class ProposalSupervisor
    extends Actor
    with ActorLogging
    with DefaultElasticsearchAPIComponent
    with ElasticsearchConfigurationExtension
    with ProposalStreamToElasticsearchComponent
    with AvroSerializers
    with ShortenedNames {

  implicit private val materializer = ActorMaterializer()(context.system)
  val proposalStreamToElasticsearch: ProposalStreamToElasticsearch =
    new ProposalStreamToElasticsearch(context.system, materializer)

  override def preStart(): Unit = {

    context.watch(
      context
        .actorOf(ProposalCoordinator.props, ProposalCoordinator.name)
    )
    context.watch(
      context
        .actorOf(ProposalProducerActor.props, ProposalProducerActor.name)
    )
    proposalStreamToElasticsearch
      .run()
      .onComplete {
        case Success(result) => log.debug("Stream processed: {}", result)
        case Failure(e)      => log.warning("Failure in stream", e)
        // TODO: restart stream on failure
      }(ECGlobal)

  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }

}

object ProposalSupervisor {

  val name: String = "proposal"
  val props: Props = Props[ProposalSupervisor]
}
