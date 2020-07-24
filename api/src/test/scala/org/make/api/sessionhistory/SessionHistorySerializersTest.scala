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

package org.make.api.sessionhistory

import java.time.ZonedDateTime

import org.make.api.sessionhistory.SessionHistoryActor.SessionHistory
import org.make.api.userhistory.StartSequenceParameters
import org.make.core.RequestContext
import org.make.core.history.HistoryActions.Trusted
import org.make.core.proposal._
import org.make.core.sequence.SequenceId
import org.make.core.session.SessionId
import org.make.core.user.UserId
import org.scalatest.wordspec.AnyWordSpec
import stamina.Persisters
import stamina.testkit.StaminaTestKit

class SessionHistorySerializersTest extends AnyWordSpec with StaminaTestKit {

  val sessionId = SessionId("session-id")
  val persisters = Persisters(SessionHistorySerializers.serializers.toList)
  val userId = UserId("my-user-id")
  val requestContext: RequestContext = RequestContext.empty
  val eventDate: ZonedDateTime = ZonedDateTime.parse("2018-03-01T16:09:30.441Z")

  "session history persister" should {

    val sessionSearchEvent = LogSessionSearchProposalsEvent(
      sessionId = sessionId,
      requestContext = requestContext,
      action = SessionAction(
        date = eventDate,
        actionType = LogSessionSearchProposalsEvent.action,
        arguments = SessionSearchParameters(term = "test")
      )
    )

    val sessionVoteEvent = LogSessionVoteEvent(
      sessionId = sessionId,
      requestContext = requestContext,
      action = SessionAction(
        date = eventDate,
        actionType = ProposalVoteAction.name,
        arguments = SessionVote(proposalId = ProposalId("proposal-id"), voteKey = VoteKey.Disagree, Trusted)
      )
    )

    val sessionUnvoteEvent = LogSessionUnvoteEvent(
      sessionId = sessionId,
      requestContext = requestContext,
      action = SessionAction(
        date = eventDate,
        actionType = ProposalUnvoteAction.name,
        arguments = SessionUnvote(proposalId = ProposalId("proposal-id"), voteKey = VoteKey.Disagree, Trusted)
      )
    )

    val sessionQualificationEvent = LogSessionQualificationEvent(
      sessionId = sessionId,
      requestContext = requestContext,
      action = SessionAction(
        date = eventDate,
        actionType = ProposalQualifyAction.name,
        arguments = SessionQualification(
          proposalId = ProposalId("proposal-id"),
          qualificationKey = QualificationKey.LikeIt,
          Trusted
        )
      )
    )

    val sessionUnqualificationEvent = LogSessionUnqualificationEvent(
      sessionId = sessionId,
      requestContext = requestContext,
      action = SessionAction(
        date = eventDate,
        actionType = ProposalUnqualifyAction.name,
        arguments = SessionUnqualification(
          proposalId = ProposalId("proposal-id"),
          qualificationKey = QualificationKey.LikeIt,
          Trusted
        )
      )
    )

    val sessionTransformedEvent = SessionTransformed(
      sessionId = sessionId,
      requestContext = requestContext,
      action = SessionAction(date = eventDate, actionType = "transformSession", arguments = userId)
    )

    val sessionStartSequenceEvent =
      LogSessionStartSequenceEvent(
        sessionId = sessionId,
        requestContext = requestContext,
        action = SessionAction(
          date = eventDate,
          actionType = LogSessionStartSequenceEvent.action,
          arguments = StartSequenceParameters(
            slug = None,
            questionId = None,
            sequenceId = Some(SequenceId("sequence-id")),
            includedProposals = Seq(ProposalId("proposalId1"), ProposalId("proposalId2"))
          )
        )
      )

    val sessionHistory = SessionHistory(events = List(
      sessionSearchEvent,
      sessionVoteEvent,
      sessionUnvoteEvent,
      sessionQualificationEvent,
      sessionUnqualificationEvent,
      sessionTransformedEvent
    )
    )

    val sessionHistory2 = sessionHistory.copy(events = sessionHistory.events ++ Seq(sessionStartSequenceEvent))

    persisters.generateTestsFor(
      sample(sessionSearchEvent),
      sample(sessionVoteEvent),
      sample(sessionUnvoteEvent),
      sample(sessionQualificationEvent),
      sample(sessionUnqualificationEvent),
      sample(sessionTransformedEvent),
      sample(sessionStartSequenceEvent),
      sample(sessionHistory),
      sample("sessionHistory2", sessionHistory2)
    )
  }
}
