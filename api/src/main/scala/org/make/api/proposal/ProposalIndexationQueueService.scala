package org.make.api.proposal

import akka.stream.scaladsl.{Keep, RestartFlow, Sink, Source, SourceQueueWithComplete}
import akka.stream._
import org.make.api
import org.make.api.ActorSystemComponent
import org.make.api.technical.elasticsearch.{ElasticsearchConfigurationComponent, ProposalIndexationStream}
import org.make.core.proposal.ProposalId

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global

trait ProposalIndexerServiceComponent {
  def proposalIndexerService: ProposalIndexerService
}

trait ProposalIndexerService {
  def offer(proposalId: ProposalId): Future[Unit]
}

trait DefaultProposalIndexerServiceComponent
    extends ProposalIndexerServiceComponent
    with ElasticsearchConfigurationComponent
    with ProposalIndexationStream {
  this: ActorSystemComponent =>

  override def proposalIndexerService: ProposalIndexerService = new ProposalIndexerService {

    lazy val bufferSize: Int =
      elasticsearchConfiguration.entityBufferSize * elasticsearchConfiguration.entityBulkSize
    lazy val proposalIndexationQueue: SourceQueueWithComplete[ProposalId] =
      Source
        .queue[ProposalId](bufferSize = bufferSize, OverflowStrategy.backpressure)
        .via(RestartFlow.withBackoff(minBackoff = 1.second, maxBackoff = 20.seconds, randomFactor = 0.2) { () =>
          ProposalStream.indexOrUpdateFlow
        })
        .withAttributes(ActorAttributes.dispatcher(api.elasticsearchDispatcher))
        .via(RestartFlow.withBackoff(minBackoff = 1.second, maxBackoff = 20.seconds, randomFactor = 0.2) { () =>
          ProposalStream.semanticIndex
        })
        .withAttributes(ActorAttributes.dispatcher(api.elasticsearchDispatcher))
        .toMat(Sink.ignore)(Keep.left)
        .run()(ActorMaterializer()(actorSystem))

    override def offer(proposalId: ProposalId): Future[Unit] = {
      proposalIndexationQueue.offer(proposalId).flatMap {
        case QueueOfferResult.Enqueued ⇒ Future.successful({})
        case QueueOfferResult.Dropped ⇒
          Future.failed(QueueOfferException(s"Item with id ${proposalId.value} dropped from indexation queue"))
        case QueueOfferResult.QueueClosed ⇒
          Future.failed(QueueOfferException("Proposal indexation queue closed. You might want to restart it."))
        case QueueOfferResult.Failure(ex) ⇒ Future.failed(ex)
      }
    }
  }
}

case class QueueOfferException(message: String) extends Exception(message)
