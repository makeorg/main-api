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

import java.time.temporal.ChronoUnit

import akka.actor.ActorRef
import akka.testkit.TestProbe
import org.make.api.ShardingActorTest
import org.make.api.sessionhistory.SessionHistoryActor.{SessionHistory, SessionVotesValues}
import org.make.api.userhistory.{StartSequenceParameters, UserHistoryEnvelope}
import org.make.api.userhistory.UserHistoryActor.{InjectSessionEvents, LogAcknowledged, SessionEventsInjected}
import org.make.core.history.HistoryActions.Trusted
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.sequence.SequenceId
import org.make.core.session.SessionId
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext}

import scala.concurrent.duration.DurationInt

class SessionHistoryActorTest extends ShardingActorTest {

  val userCoordinatorProbe: TestProbe = TestProbe()(system)

  val coordinator: ActorRef =
    system.actorOf(
      SessionHistoryCoordinator.props(userCoordinatorProbe.ref, 500.milliseconds),
      SessionHistoryCoordinator.name
    )

  Feature("Vote retrieval") {
    Scenario("no vote history") {
      coordinator ! RequestSessionVoteValues(SessionId("no-vote-history"), Seq(ProposalId("proposal1")))
      expectMsg(SessionVotesValues(Map.empty))
    }

    Scenario("vote on proposal") {
      val sessionId = SessionId("vote-on-proposal")
      val proposalId = ProposalId("proposal1")
      val event = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now(), "vote", SessionVote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event)

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      val response = expectMsgType[SessionVotesValues].votesValues
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map.empty
    }

    Scenario("vote then unvote proposal") {
      val sessionId = SessionId("vote-then-unvote-proposal")
      val proposalId = ProposalId("proposal1")
      val event1 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId, VoteKey.Agree, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event1)

      expectMsg(LogAcknowledged)

      val event2 = LogSessionUnvoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now(), "unvote", SessionUnvote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event2)

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      expectMsg(SessionVotesValues(Map.empty))
    }

    Scenario("vote then unvote then vote proposal") {
      val sessionId = SessionId("vote-then-unvote-then-vote-proposal")
      val proposalId = ProposalId("proposal1")
      val event1 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId, VoteKey.Disagree, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event1)

      expectMsg(LogAcknowledged)

      val event2 = LogSessionUnvoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "unvote",
          SessionUnvote(proposalId, VoteKey.Disagree, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event2)

      expectMsg(LogAcknowledged)

      val event3 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now(), "vote", SessionVote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event3)

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      val response = expectMsgType[SessionVotesValues].votesValues
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map.empty
    }

    Scenario("vote then one qualification proposal") {
      val sessionId = SessionId("vote-then-one-qualification-proposal")
      val proposalId = ProposalId("proposal1")
      val event1 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId, VoteKey.Agree, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event1)

      expectMsg(LogAcknowledged)

      val event2 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now(),
          "qualification",
          SessionQualification(proposalId, QualificationKey.LikeIt, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event2)

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      val response = expectMsgType[SessionVotesValues].votesValues
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map(QualificationKey.LikeIt -> Trusted)
    }

    Scenario("vote then two qualifications proposal") {
      val sessionId = SessionId("vote-then-two-qualification-proposal")
      val proposalId = ProposalId("proposal1")
      val event1 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId, VoteKey.Agree, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event1)

      expectMsg(LogAcknowledged)

      val event2 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.LikeIt, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event2)

      expectMsg(LogAcknowledged)

      val event3 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now(),
          "qualification",
          SessionQualification(proposalId, QualificationKey.PlatitudeAgree, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event3)

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      val response = expectMsgType[SessionVotesValues].votesValues
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map(
        QualificationKey.LikeIt -> Trusted,
        QualificationKey.PlatitudeAgree -> Trusted
      )
    }

    Scenario("vote then three qualification proposal") {
      val sessionId = SessionId("vote-then-three-qualification-proposal")
      val proposalId = ProposalId("proposal1")
      val event1 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(15, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId, VoteKey.Agree, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event1)

      expectMsg(LogAcknowledged)

      val event2 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.LikeIt, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event2)

      expectMsg(LogAcknowledged)

      val event3 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.Doable, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event3)

      expectMsg(LogAcknowledged)

      val event4 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now(),
          "qualification",
          SessionQualification(proposalId, QualificationKey.PlatitudeAgree, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event4)

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      val response = expectMsgType[SessionVotesValues].votesValues
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map(
        QualificationKey.Doable -> Trusted,
        QualificationKey.LikeIt -> Trusted,
        QualificationKey.PlatitudeAgree -> Trusted
      )
    }

    Scenario("vote then qualif then unqualif then requalif proposal") {
      val sessionId = SessionId("vote-then-qualif-then-unqualif-then-requalif-proposal")
      val proposalId = ProposalId("proposal1")
      val event1 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(15, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId, VoteKey.Agree, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event1)

      expectMsg(LogAcknowledged)

      val event2 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(12, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.LikeIt, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event2)

      expectMsg(LogAcknowledged)

      val event3 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.Doable, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event3)

      expectMsg(LogAcknowledged)

      val event4 = LogSessionUnqualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(8, ChronoUnit.SECONDS),
          "unqualification",
          SessionUnqualification(proposalId, QualificationKey.LikeIt, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event4)

      expectMsg(LogAcknowledged)

      val event5 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.PlatitudeAgree, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event5)

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      val response = expectMsgType[SessionVotesValues].votesValues
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map(
        QualificationKey.Doable -> Trusted,
        QualificationKey.PlatitudeAgree -> Trusted
      )
    }

    Scenario("vote on many proposal") {
      val sessionId = SessionId("vote-on-many-proposal")
      val proposalId1 = ProposalId("proposal1")
      val proposalId2 = ProposalId("proposal2")
      val proposalId3 = ProposalId("proposal3")
      val event1 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(15, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId1, VoteKey.Agree, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event1)

      expectMsg(LogAcknowledged)

      val event2 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId2, VoteKey.Disagree, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event2)

      expectMsg(LogAcknowledged)

      val event3 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId3, VoteKey.Neutral, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event3)

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId1, proposalId2, proposalId3))
      val response = expectMsgType[SessionVotesValues].votesValues
      response(proposalId1).voteKey shouldBe VoteKey.Agree
      response(proposalId1).qualificationKeys shouldBe Map.empty
      response(proposalId2).voteKey shouldBe VoteKey.Disagree
      response(proposalId2).qualificationKeys shouldBe Map.empty
      response(proposalId3).voteKey shouldBe VoteKey.Neutral
      response(proposalId3).qualificationKeys shouldBe Map.empty
    }

    Scenario("vote and qualif on many proposal") {
      val sessionId = SessionId("vote-and-qualif-on-many-proposal")
      val proposalId1 = ProposalId("proposal1")
      val proposalId2 = ProposalId("proposal2")
      val proposalId3 = ProposalId("proposal3")
      val event1 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(15, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId1, VoteKey.Agree, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event1)

      expectMsg(LogAcknowledged)

      val event2 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(14, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId1, QualificationKey.Doable, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event2)

      expectMsg(LogAcknowledged)

      val event3 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId2, VoteKey.Disagree, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event3)

      expectMsg(LogAcknowledged)

      val event4 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(9, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId2, QualificationKey.Impossible, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event4)

      expectMsg(LogAcknowledged)

      val event5 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(8, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId2, QualificationKey.NoWay, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event5)

      expectMsg(LogAcknowledged)

      val event6 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId3, VoteKey.Neutral, Trusted)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event6)

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId1, proposalId2, proposalId3))
      val response = expectMsgType[SessionVotesValues].votesValues
      response(proposalId1).voteKey shouldBe VoteKey.Agree
      response(proposalId1).qualificationKeys shouldBe Map(QualificationKey.Doable -> Trusted)
      response(proposalId2).voteKey shouldBe VoteKey.Disagree
      response(proposalId2).qualificationKeys shouldBe Map(
        QualificationKey.Impossible -> Trusted,
        QualificationKey.NoWay -> Trusted
      )
      response(proposalId3).voteKey shouldBe VoteKey.Neutral
      response(proposalId3).qualificationKeys shouldBe Map.empty
    }
  }

  Feature("session transformation") {
    Scenario("normal case") {
      val sessionId = SessionId("normal-session-transformation")
      val userId = UserId("normal-user-id")
      val now = DateHelper.now()
      val event1 = LogSessionStartSequenceEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          now,
          LogSessionStartSequenceEvent.action,
          StartSequenceParameters(None, None, Some(SequenceId("some-random-sequence")), Seq.empty)
        )
      )

      val event2 = LogSessionStartSequenceEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          now.plus(1, ChronoUnit.MINUTES),
          LogSessionStartSequenceEvent.action,
          StartSequenceParameters(None, None, Some(SequenceId("some-random-sequence")), Seq.empty)
        )
      )

      coordinator ! SessionHistoryEnvelope(sessionId, event1)
      coordinator ! UserConnected(sessionId, userId, RequestContext.empty)

      // This event should be forwarded to user history
      coordinator ! SessionHistoryEnvelope(sessionId, event2)

      userCoordinatorProbe.expectMsg(5.seconds, InjectSessionEvents(userId, Seq(event1.toUserHistoryEvent(userId))))
      userCoordinatorProbe.reply(SessionEventsInjected)
      userCoordinatorProbe.expectMsg(5.seconds, UserHistoryEnvelope(userId, event2.toUserHistoryEvent(userId)))

      val transformation = expectMsgType[SessionTransformed]

      coordinator ! GetSessionHistory(sessionId)
      // Once transformed, there shouldn't be any more messages added to the session
      expectMsg(SessionHistory(List(transformation)))

      coordinator ! StopSession(sessionId)

      Thread.sleep(500)

      coordinator ! GetSessionHistory(sessionId)
      expectMsg(SessionHistory(List(transformation)))

      val event3 = LogSessionStartSequenceEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().plus(2, ChronoUnit.MINUTES),
          LogSessionStartSequenceEvent.action,
          StartSequenceParameters(None, None, Some(SequenceId("some-random-sequence")), Seq.empty)
        )
      )
      coordinator ! SessionHistoryEnvelope(sessionId, event3)

      userCoordinatorProbe.expectMsg(5.seconds, UserHistoryEnvelope(userId, event3.toUserHistoryEvent(userId)))

      coordinator ! GetSessionHistory(sessionId)
      expectMsg(SessionHistory(List(transformation)))

    }

  }

  Feature("locking for votes") {
    Scenario("locking for vote") {
      val sessionId = SessionId("locking-for-vote")
      val proposalId = ProposalId("locking-for-vote")

      coordinator ! LockProposalForVote(sessionId, proposalId)
      expectMsg(LockAcquired)

      coordinator ! LockProposalForVote(sessionId, proposalId)
      expectMsg(LockAlreadyAcquired)

      coordinator ! LockProposalForQualification(sessionId, proposalId, QualificationKey.Doable)
      expectMsg(LockAlreadyAcquired)

      coordinator ! ReleaseProposalForVote(sessionId, proposalId)

      coordinator ! LockProposalForVote(sessionId, proposalId)
      expectMsg(LockAcquired)
    }

    Scenario("deadline security") {
      val sessionId = SessionId("deadline-security")
      val proposalId = ProposalId("deadline-security")

      coordinator ! LockProposalForVote(sessionId, proposalId)
      expectMsg(LockAcquired)

      Thread.sleep(600)

      coordinator ! LockProposalForVote(sessionId, proposalId)
      expectMsg(LockAcquired)
    }
  }

  Feature("locking qualifications") {
    Scenario("multiple qualifications") {
      val sessionId = SessionId("locking-for-qualification")
      val proposalId = ProposalId("locking-for-qualification")

      val qualification1 = QualificationKey.LikeIt
      val qualification2 = QualificationKey.Doable

      coordinator ! LockProposalForQualification(sessionId, proposalId, qualification1)
      expectMsg(LockAcquired)

      coordinator ! LockProposalForQualification(sessionId, proposalId, qualification1)
      expectMsg(LockAlreadyAcquired)

      coordinator ! LockProposalForVote(sessionId, proposalId)
      expectMsg(LockAlreadyAcquired)

      coordinator ! LockProposalForQualification(sessionId, proposalId, qualification2)
      expectMsg(LockAcquired)

      coordinator ! ReleaseProposalForQualification(sessionId, proposalId, qualification1)

      coordinator ! LockProposalForVote(sessionId, proposalId)
      expectMsg(LockAlreadyAcquired)

      coordinator ! ReleaseProposalForQualification(sessionId, proposalId, qualification2)

      coordinator ! LockProposalForVote(sessionId, proposalId)
      expectMsg(LockAcquired)

    }
  }

}
