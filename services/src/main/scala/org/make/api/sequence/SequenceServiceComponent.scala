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

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import org.make.api.demographics.DemographicsCardResponse
import org.make.api.proposal.ProposalResponse
import org.make.core.proposal._
import org.make.core.question.QuestionId
import org.make.core.user._
import org.make.core.RequestContext
import org.make.core.demographics.DemographicsCardId
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.sequence.SequenceConfiguration
import org.make.core.session.SessionId

import scala.concurrent.Future

trait SequenceServiceComponent {
  def sequenceService: SequenceService
}

trait SequenceService {
  def startNewSequence[T: SequenceBehaviourProvider](
    behaviourParam: T,
    maybeUserId: Option[UserId],
    questionId: QuestionId,
    includedProposalsIds: Seq[ProposalId],
    requestContext: RequestContext,
    cardId: Option[DemographicsCardId],
    token: Option[String],
    configurationOverride: Option[SequenceConfiguration] = None
  ): Future[SequenceResult]

  def simpleSequence(
    includedProposalsIds: Seq[ProposalId],
    behaviour: SequenceBehaviour,
    proposalsToExclude: Seq[ProposalId],
    sessionId: Option[SessionId]
  ): Future[Seq[IndexedProposal]]
}

final case class SequenceResult(proposals: Seq[ProposalResponse], demographics: Option[DemographicsCardResponse])

object SequenceResult {
  implicit val encoder: Encoder[SequenceResult] = deriveEncoder[SequenceResult]

  val empty: SequenceResult = SequenceResult(proposals = Seq.empty, demographics = None)
}
