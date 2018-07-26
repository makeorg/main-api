package org.make.api.proposal

import akka.stream.scaladsl.{Keep, RestartFlow, Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, OverflowStrategy, QueueOfferResult}
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
        .via(RestartFlow.withBackoff(minBackoff = 1.second, maxBackoff = 20.seconds, randomFactor = 0.2) { () =>
          ProposalStream.semanticIndex
        })
        .toMat(Sink.ignore)(Keep.left)
        .run()(
          ActorMaterializer(ActorMaterializerSettings(actorSystem).withDispatcher(api.elasticsearchDispatcher))(
            actorSystem
          )
        )

    override def offer(proposalId: ProposalId): Future[Unit] = {
      proposalIndexationQueue.offer(proposalId).map {
        case QueueOfferResult.Enqueued ⇒ ()
        case QueueOfferResult.Dropped ⇒
          logger.error(s"Can't index proposal with id ${proposalId.value}: item dropped from indexation queue")
        case QueueOfferResult.Failure(ex) ⇒
          logger.error(s"Error presenting proposal to indexation queue: ${ex.getMessage}")
        case QueueOfferResult.QueueClosed ⇒
          logger.error("Proposal indexation queue closed. You might want to restart it.")
      }
    }
  }
}
