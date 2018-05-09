package org.make.api.sessionhistory

import java.time.ZonedDateTime

import org.make.api.sessionhistory.SessionHistoryActor.SessionHistory
import org.make.api.userhistory.StartSequenceParameters
import org.make.core.RequestContext
import org.make.core.proposal._
import org.make.core.sequence.SequenceId
import org.make.core.session.SessionId
import org.make.core.user.UserId
import org.scalatest.WordSpec
import stamina.Persisters
import stamina.testkit.StaminaTestKit

class SessionHistorySerializersTest extends WordSpec with StaminaTestKit {

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
        arguments = SessionVote(proposalId = ProposalId("proposal-id"), voteKey = VoteKey.Disagree)
      )
    )

    val sessionUnvoteEvent = LogSessionUnvoteEvent(
      sessionId = sessionId,
      requestContext = requestContext,
      action = SessionAction(
        date = eventDate,
        actionType = ProposalUnvoteAction.name,
        arguments = SessionUnvote(proposalId = ProposalId("proposal-id"), voteKey = VoteKey.Disagree)
      )
    )

    val sessionQualificationEvent = LogSessionQualificationEvent(
      sessionId = sessionId,
      requestContext = requestContext,
      action = SessionAction(
        date = eventDate,
        actionType = ProposalQualifyAction.name,
        arguments =
          SessionQualification(proposalId = ProposalId("proposal-id"), qualificationKey = QualificationKey.LikeIt)
      )
    )

    val sessionUnqualificationEvent = LogSessionUnqualificationEvent(
      sessionId = sessionId,
      requestContext = requestContext,
      action = SessionAction(
        date = eventDate,
        actionType = ProposalUnqualifyAction.name,
        arguments =
          SessionUnqualification(proposalId = ProposalId("proposal-id"), qualificationKey = QualificationKey.LikeIt)
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
            sequenceId = Some(SequenceId("sequence-id")),
            includedProposals = Seq(ProposalId("proposalId1"), ProposalId("proposalId2"))
          )
        )
      )

    val sessionHistory = SessionHistory(
      events = List(
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
