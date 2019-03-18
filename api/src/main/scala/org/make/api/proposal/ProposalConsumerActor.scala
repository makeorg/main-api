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
import org.make.api.organisation.{OrganisationService, OrganisationServiceComponent}
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.semantic.{SemanticComponent, SemanticService}
import org.make.api.sequence.{SequenceConfigurationComponent, SequenceConfigurationService, SequenceServiceComponent}
import org.make.api.tag.{TagService, TagServiceComponent}
import org.make.api.technical.KafkaConsumerActor
import org.make.api.technical.elasticsearch.ProposalIndexationStream
import org.make.api.user.{UserService, UserServiceComponent}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ProposalConsumerActor(proposalIndexerService: ProposalIndexerService,
                            override val proposalCoordinatorService: ProposalCoordinatorService,
                            override val userService: UserService,
                            override val organisationService: OrganisationService,
                            override val tagService: TagService,
                            override val semanticService: SemanticService,
                            override val elasticsearchProposalAPI: ProposalSearchEngine,
                            override val sequenceConfigurationService: SequenceConfigurationService)
    extends KafkaConsumerActor[ProposalEventWrapper]
    with KafkaConfigurationExtension
    with ActorLogging
    with ProposalIndexationStream {

  override protected lazy val kafkaTopic: String = ProposalProducerActor.topicKey
  override protected val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]

  implicit val timeout: Timeout = Timeout(5.seconds)

  override def handleMessage(message: ProposalEventWrapper): Future[Unit] = {
    message.event.fold(ToProposalEvent) match {
      case event: ProposalViewed               => doNothing(event)
      case event: ReindexProposal              => onCreateOrUpdate(event)
      case event: ProposalUpdated              => onCreateOrUpdate(event)
      case event: ProposalVotesVerifiedUpdated => onCreateOrUpdate(event)
      case event: ProposalProposed             => onCreateOrUpdate(event)
      case event: ProposalAccepted             => onCreateOrUpdate(event)
      case event: ProposalRefused              => onCreateOrUpdate(event)
      case event: ProposalPostponed            => onCreateOrUpdate(event)
      case event: ProposalVoted                => onCreateOrUpdate(event)
      case event: ProposalUnvoted              => onCreateOrUpdate(event)
      case event: ProposalQualified            => onCreateOrUpdate(event)
      case event: ProposalUnqualified          => onCreateOrUpdate(event)
      case event: ProposalPatched              => onCreateOrUpdate(event)
      case event: ProposalAddedToOperation     => onCreateOrUpdate(event)
      case event: ProposalRemovedFromOperation => onCreateOrUpdate(event)
      case event: ProposalLocked               => doNothing(event)
      case event: ProposalAnonymized           => onCreateOrUpdate(event)
      case event: SimilarProposalsAdded        => doNothing(event)
    }

  }

  def onCreateOrUpdate(event: ProposalEvent): Future[Unit] = {
    proposalIndexerService.offer(event.id).recover {
      case ex =>
        logger.error(s"Error presenting proposal to indexation queue: ${ex.getMessage}")
    }
  }

  override val groupId = "proposal-consumer"
}

object ProposalConsumerActor {
  type ProposalConsumerActorDependencies =
    UserServiceComponent
      with OrganisationServiceComponent
      with TagServiceComponent
      with SequenceServiceComponent
      with SemanticComponent
      with ProposalSearchEngineComponent
      with ProposalIndexerServiceComponent
      with SequenceConfigurationComponent

  def props(proposalCoordinatorService: ProposalCoordinatorService,
            dependencies: ProposalConsumerActorDependencies): Props =
    Props(
      new ProposalConsumerActor(
        dependencies.proposalIndexerService,
        proposalCoordinatorService,
        dependencies.userService,
        dependencies.organisationService,
        dependencies.tagService,
        dependencies.semanticService,
        dependencies.elasticsearchProposalAPI,
        dependencies.sequenceConfigurationService
      )
    )
  val name: String = "proposal-consumer"
}
