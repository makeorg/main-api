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

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import akka.actor.{Actor, ActorLogging, Props}
import org.make.api.MakeBackoffSupervisor
import org.make.api.proposal.ProposalIndexerActor.{
  IndexProposal,
  ProposalIndexationFailed,
  ProposalIndexed,
  RemoveTimeoutOperations
}
import org.make.api.technical.elasticsearch.ElasticsearchConfigurationExtension
import org.make.core.DateHelper
import org.make.core.proposal.ProposalId
import org.make.core.proposal.indexed.IndexedProposal

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class ProposalIndexerActor
    extends Actor
    with ActorLogging
    with DefaultProposalSearchEngineComponent
    with ElasticsearchConfigurationExtension {

  private val removalThresholdSeconds = 10
  private var currentIndexations = Map.empty[ProposalId, ZonedDateTime]

  override def preStart(): Unit = {
    context.system.scheduler.schedule(10.seconds, 10.seconds, self, RemoveTimeoutOperations)
  }

  override def receive: Receive = {
    case IndexProposal(proposal) =>
      if (currentIndexations.contains(proposal.id)) {
        log.info("Already indexing proposal {}, ignoring command", proposal.id.value)
      } else {
        currentIndexations += proposal.id -> DateHelper.now()
        indexOrUpdate(proposal)
      }

    case ProposalIndexed(id) =>
      currentIndexations -= id

    case ProposalIndexationFailed(id) =>
      currentIndexations -= id

    case RemoveTimeoutOperations =>
      val now = DateHelper.now()
      currentIndexations = currentIndexations.filter {
        case (_, date) => ChronoUnit.SECONDS.between(date, now) < removalThresholdSeconds
      }
  }

  def indexOrUpdate(proposal: IndexedProposal): Unit = {
    log.debug(s"Indexing $proposal")
    val me = self
    elasticsearchProposalAPI
      .findProposalById(proposal.id)
      .flatMap {
        case None    => elasticsearchProposalAPI.indexProposal(proposal)
        case Some(_) => elasticsearchProposalAPI.updateProposal(proposal)
      }
      .map { _ =>
        me ! ProposalIndexed(proposal.id)
      }
      .recover {
        case e =>
          log.error(e, "Error when indexing proposal, retrying")
          me ! ProposalIndexationFailed(proposal.id)
      }
  }
}

object ProposalIndexerActor {

  val (props: Props, name: String) =
    MakeBackoffSupervisor.propsAndName(Props[ProposalIndexerActor], "proposal-indexer-actor")

  case class IndexProposal(proposal: IndexedProposal)
  case class ProposalIndexed(proposalId: ProposalId)
  case class ProposalIndexationFailed(proposalId: ProposalId)
  case object RemoveTimeoutOperations

}
