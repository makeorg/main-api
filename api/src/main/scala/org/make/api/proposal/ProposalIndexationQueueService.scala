/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

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

  override lazy val proposalIndexerService: DefaultProposalIndexerService = new DefaultProposalIndexerService

  class DefaultProposalIndexerService extends ProposalIndexerService {
    lazy val bufferSize: Int =
      elasticsearchConfiguration.entityBufferSize * elasticsearchConfiguration.entityBulkSize
    val backoff: RestartSettings = RestartSettings(minBackoff = 1.second, maxBackoff = 20.seconds, randomFactor = 0.2)
    lazy val proposalIndexationQueue: SourceQueueWithComplete[ProposalId] =
      Source
        .queue[ProposalId](bufferSize = bufferSize, OverflowStrategy.backpressure)
        .via(RestartFlow.withBackoff(backoff) { () =>
          ProposalStream.indexOrUpdateFlow
        })
        .withAttributes(ActorAttributes.dispatcher(api.elasticsearchDispatcher))
        .via(RestartFlow.withBackoff(backoff) { () =>
          ProposalStream.semanticIndex
        })
        .withAttributes(ActorAttributes.dispatcher(api.elasticsearchDispatcher))
        .toMat(Sink.ignore)(Keep.left)
        .run()

    override def offer(proposalId: ProposalId): Future[Unit] = {
      proposalIndexationQueue.offer(proposalId).flatMap {
        case QueueOfferResult.Enqueued => Future.successful({})
        case QueueOfferResult.Dropped =>
          Future.failed(QueueOfferException(s"Item with id ${proposalId.value} dropped from indexation queue"))
        case QueueOfferResult.QueueClosed =>
          Future.failed(QueueOfferException("Proposal indexation queue closed. You might want to restart it."))
        case QueueOfferResult.Failure(ex) => Future.failed(ex)
      }
    }
  }
}

final case class QueueOfferException(message: String) extends Exception(message)
