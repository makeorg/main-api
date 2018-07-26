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

import akka.actor.{ActorLogging, Props}
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.operation.{OperationService, OperationServiceComponent}
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.semantic.{SemanticComponent, SemanticService}
import org.make.api.sequence.{SequenceService, SequenceServiceComponent}
import org.make.api.tag.{TagService, TagServiceComponent}
import org.make.api.technical.KafkaConsumerActor
import org.make.api.technical.elasticsearch.ProposalIndexationStream
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.core.proposal._
import org.make.core.sequence.SequenceId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ProposalConsumerActor(sequenceService: SequenceService,
                            operationService: OperationService,
                            proposalIndexerService: ProposalIndexerService,
                            override val proposalCoordinatorService: ProposalCoordinatorService,
                            override val userService: UserService,
                            override val tagService: TagService,
                            override val semanticService: SemanticService,
                            override val elasticsearchProposalAPI: ProposalSearchEngine)
    extends KafkaConsumerActor[ProposalEventWrapper]
    with KafkaConfigurationExtension
    with ActorLogging
    with ProposalIndexationStream {

  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(ProposalProducerActor.topicKey)
  override protected val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]

  implicit val timeout: Timeout = Timeout(5.seconds)

  override def handleMessage(message: ProposalEventWrapper): Future[Unit] = {
    message.event.fold(ToProposalEvent) match {
      case _: ProposalViewed => Future.successful {}
      case event: ProposalUpdated =>
        onSimilarProposalsUpdated(event.id, event.similarProposals)
        onCreateOrUpdate(event)
      case event: ReindexProposal  => onCreateOrUpdate(event)
      case event: ProposalProposed => onCreateOrUpdate(event)
      case event: ProposalAccepted =>
        onSimilarProposalsUpdated(event.id, event.similarProposals)
        onCreateOrUpdate(event)
      case event: ProposalRefused     => onCreateOrUpdate(event)
      case event: ProposalPostponed   => onCreateOrUpdate(event)
      case event: ProposalVoted       => onCreateOrUpdate(event)
      case event: ProposalUnvoted     => onCreateOrUpdate(event)
      case event: ProposalQualified   => onCreateOrUpdate(event)
      case event: ProposalUnqualified => onCreateOrUpdate(event)
      case event: ProposalPatched     => onCreateOrUpdate(event)
      case event: ProposalAddedToOperation =>
        addToOperation(event)
        onCreateOrUpdate(event)
      case event: ProposalRemovedFromOperation =>
        removeFromOperation(event)
        onCreateOrUpdate(event)
      case _: ProposalLocked => Future.successful {}
      case event: SimilarProposalsAdded =>
        onSimilarProposalsUpdated(event.id, event.similarProposals.toSeq)
        Future.successful {}
    }

  }

  def onCreateOrUpdate(event: ProposalEvent): Future[Unit] = {
    proposalIndexerService.offer(event.id)
  }

  def addToOperation(event: ProposalAddedToOperation): Future[Unit] = {
    proposalCoordinatorService.getProposal(event.id).flatMap {
      case Some(proposal) =>
        operationService.findOne(event.operationId).map { maybeOperation =>
          maybeOperation.foreach { operation =>
            val sequenceId: SequenceId = operation.countriesConfiguration
              .find(countryConfiguration => proposal.country.contains(countryConfiguration.countryCode))
              .getOrElse(operation.countriesConfiguration.head)
              .landingSequenceId
            sequenceService
              .addProposals(sequenceId, event.moderatorId, event.requestContext, Seq(event.id))
          }
        }
      case None => Future.successful[Unit] {}
    }
  }

  def removeFromOperation(event: ProposalRemovedFromOperation): Future[Unit] = {
    proposalCoordinatorService.getProposal(event.id).flatMap {
      case Some(proposal) =>
        operationService.findOne(event.operationId).map { maybeOperation =>
          maybeOperation.foreach { operation =>
            val sequenceId: SequenceId = operation.countriesConfiguration
              .find(countryConfiguration => proposal.country.contains(countryConfiguration.countryCode))
              .getOrElse(operation.countriesConfiguration.head)
              .landingSequenceId
            sequenceService
              .removeProposals(sequenceId, event.moderatorId, event.requestContext, Seq(event.id))
          }
        }
      case None => Future.successful[Unit] {}
    }
  }

  def onSimilarProposalsUpdated(proposalId: ProposalId, newSimilarProposals: Seq[ProposalId]): Unit = {
    val allIds = Seq(proposalId) ++ newSimilarProposals
    newSimilarProposals.foreach { id =>
      proposalCoordinatorService.updateDuplicates(UpdateDuplicatedProposalsCommand(id, allIds.filter(_ != id)))
    }
  }

  override val groupId = "proposal-consumer"
}

object ProposalConsumerActor {
  type ProposalConsumerActorDependencies =
    UserServiceComponent with TagServiceComponent with SequenceServiceComponent with OperationServiceComponent with SemanticComponent with ProposalSearchEngineComponent with ProposalIndexerServiceComponent

  def props(proposalCoordinatorService: ProposalCoordinatorService,
            dependencies: ProposalConsumerActorDependencies): Props =
    Props(
      new ProposalConsumerActor(
        dependencies.sequenceService,
        dependencies.operationService,
        dependencies.proposalIndexerService,
        proposalCoordinatorService,
        dependencies.userService,
        dependencies.tagService,
        dependencies.semanticService,
        dependencies.elasticsearchProposalAPI
      )
    )
  val name: String = "proposal-consumer"
}
