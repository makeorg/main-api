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

package org.make.api.sequence

import akka.util.Timeout
import org.make.api.proposal.{ProposalResponse, ProposalServiceComponent}
import org.make.api.sequence.SequenceCacheManager.GetProposal
import org.make.api.technical.{ActorSystemComponent, TimeSettings}
import org.make.api.technical.BetterLoggingActors.BetterLoggingTypedActorRef
import org.make.api.technical.security.SecurityConfigurationComponent
import org.make.core.RequestContext
import org.make.core.question.QuestionId

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait DefaultSequenceCacheManagerServiceComponent extends SequenceCacheManagerServiceComponent {
  this: SequenceCacheManagerComponent
    with ActorSystemComponent
    with ProposalServiceComponent
    with SecurityConfigurationComponent =>

  override lazy val sequenceCacheManagerService: SequenceCacheManagerService = new DefaultSequenceCacheManagerService

  class DefaultSequenceCacheManagerService extends SequenceCacheManagerService {
    implicit val timeout: Timeout = TimeSettings.defaultTimeout

    // This method is meant to be used by the widget.
    // Widget does not support cookies thus users will not be authenticated.
    // There's no need to populate `myProposal` and `voteAndQualifications`
    override def getProposal(questionId: QuestionId, requestContext: RequestContext): Future[ProposalResponse] = {
      (sequenceCacheManager ?? (GetProposal(questionId, _))).map { proposal =>
        val proposalKey =
          proposalService.generateProposalKeyHash(
            proposal.id,
            requestContext.sessionId,
            requestContext.location,
            securityConfiguration.secureVoteSalt
          )
        ProposalResponse(
          indexedProposal = proposal,
          myProposal = false,
          voteAndQualifications = None,
          proposalKey = proposalKey
        )
      }
    }
  }
}
