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
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ShardingActorTest
import org.make.api.sessionhistory.SessionHistoryActor.SessionHistory
import org.make.api.userhistory.StartSequenceParameters
import org.make.api.userhistory.UserHistoryActor.{InjectSessionEvents, LogAcknowledged, SessionEventsInjected}
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.sequence.SequenceId
import org.make.core.session.SessionId
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext}
import org.scalatest.GivenWhenThen

import scala.concurrent.duration.DurationInt

class SessionHistoryActorTest extends ShardingActorTest with GivenWhenThen with StrictLogging {

  val userCoordinatorProbe: TestProbe = TestProbe()(system)

  val coordinator: ActorRef =
    system.actorOf(SessionHistoryCoordinator.props(userCoordinatorProbe.ref), SessionHistoryCoordinator.name)

  feature("Vote retrieval") {
    scenario("no vote history") {
      coordinator ! RequestSessionVoteValues(SessionId("no-vote-history"), Seq(ProposalId("proposal1")))
      expectMsg(Map.empty)
    }

    scenario("vote on proposal") {
      val sessionId = SessionId("vote-on-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now(), "vote", SessionVote(proposalId, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      val response = expectMsgType[Map[ProposalId, VoteAndQualifications]]
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Seq.empty
    }

    scenario("vote then unvote proposal") {
      val sessionId = SessionId("vote-then-unvote-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(10, ChronoUnit.SECONDS), "vote", SessionVote(proposalId, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionUnvoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now(), "unvote", SessionUnvote(proposalId, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      expectMsg(Map.empty)
    }

    scenario("vote then unvote then vote proposal") {
      val sessionId = SessionId("vote-then-unvote-then-vote-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(10, ChronoUnit.SECONDS), "vote", SessionVote(proposalId, VoteKey.Disagree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionUnvoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "unvote",
          SessionUnvote(proposalId, VoteKey.Disagree)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now(), "vote", SessionVote(proposalId, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      val response = expectMsgType[Map[ProposalId, VoteAndQualifications]]
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Seq.empty
    }

    scenario("vote then one qualification proposal") {
      val sessionId = SessionId("vote-then-one-qualification-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(10, ChronoUnit.SECONDS), "vote", SessionVote(proposalId, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now(), "qualification", SessionQualification(proposalId, QualificationKey.LikeIt))
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      val response = expectMsgType[Map[ProposalId, VoteAndQualifications]]
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Seq(QualificationKey.LikeIt)
    }

    scenario("vote then two qualifications proposal") {
      val sessionId = SessionId("vote-then-two-qualification-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(10, ChronoUnit.SECONDS), "vote", SessionVote(proposalId, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.LikeIt)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now(),
          "qualification",
          SessionQualification(proposalId, QualificationKey.PlatitudeAgree)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      val response = expectMsgType[Map[ProposalId, VoteAndQualifications]]
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Seq(QualificationKey.LikeIt, QualificationKey.PlatitudeAgree)
    }

    scenario("vote then three qualification proposal") {
      val sessionId = SessionId("vote-then-three-qualification-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(15, ChronoUnit.SECONDS), "vote", SessionVote(proposalId, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.LikeIt)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.Doable)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now(),
          "qualification",
          SessionQualification(proposalId, QualificationKey.PlatitudeAgree)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      val response = expectMsgType[Map[ProposalId, VoteAndQualifications]]
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Seq(
        QualificationKey.Doable,
        QualificationKey.LikeIt,
        QualificationKey.PlatitudeAgree
      )
    }

    scenario("vote then qualif then unqualif then requalif proposal") {
      val sessionId = SessionId("vote-then-qualif-then-unqualif-then-requalif-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(15, ChronoUnit.SECONDS), "vote", SessionVote(proposalId, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(12, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.LikeIt)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.Doable)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionUnqualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(8, ChronoUnit.SECONDS),
          "unqualification",
          SessionUnqualification(proposalId, QualificationKey.LikeIt)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.PlatitudeAgree)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      val response = expectMsgType[Map[ProposalId, VoteAndQualifications]]
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Seq(QualificationKey.Doable, QualificationKey.PlatitudeAgree)
    }

    scenario("vote on many proposal") {
      val sessionId = SessionId("vote-on-many-proposal")
      val proposalId1 = ProposalId("proposal1")
      val proposalId2 = ProposalId("proposal2")
      val proposalId3 = ProposalId("proposal3")
      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(15, ChronoUnit.SECONDS), "vote", SessionVote(proposalId1, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId2, VoteKey.Disagree)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(5, ChronoUnit.SECONDS), "vote", SessionVote(proposalId3, VoteKey.Neutral))
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId1, proposalId2, proposalId3))
      val response = expectMsgType[Map[ProposalId, VoteAndQualifications]]
      response(proposalId1).voteKey shouldBe VoteKey.Agree
      response(proposalId1).qualificationKeys shouldBe Seq.empty
      response(proposalId2).voteKey shouldBe VoteKey.Disagree
      response(proposalId2).qualificationKeys shouldBe Seq.empty
      response(proposalId3).voteKey shouldBe VoteKey.Neutral
      response(proposalId3).qualificationKeys shouldBe Seq.empty
    }

    scenario("vote and qualif on many proposal") {
      val sessionId = SessionId("vote-and-qualif-on-many-proposal")
      val proposalId1 = ProposalId("proposal1")
      val proposalId2 = ProposalId("proposal2")
      val proposalId3 = ProposalId("proposal3")
      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(15, ChronoUnit.SECONDS), "vote", SessionVote(proposalId1, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(14, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId1, QualificationKey.Doable)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId2, VoteKey.Disagree)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(9, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId2, QualificationKey.Impossible)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(8, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId2, QualificationKey.NoWay)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(5, ChronoUnit.SECONDS), "vote", SessionVote(proposalId3, VoteKey.Neutral))
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId1, proposalId2, proposalId3))
      val response = expectMsgType[Map[ProposalId, VoteAndQualifications]]
      response(proposalId1).voteKey shouldBe VoteKey.Agree
      response(proposalId1).qualificationKeys shouldBe Seq(QualificationKey.Doable)
      response(proposalId2).voteKey shouldBe VoteKey.Disagree
      response(proposalId2).qualificationKeys shouldBe Seq(QualificationKey.Impossible, QualificationKey.NoWay)
      response(proposalId3).voteKey shouldBe VoteKey.Neutral
      response(proposalId3).qualificationKeys shouldBe Seq.empty
    }
  }

  feature("session transformation") {
    scenario("normal case") {
      val sessionId = SessionId("normal-session-transformation")
      val userId = UserId("normal-user-id")
      val event1 = LogSessionStartSequenceEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now(),
          LogSessionStartSequenceEvent.action,
          StartSequenceParameters(None, Some(SequenceId("some-random-sequence")), Seq.empty)
        )
      )

      val event2 = LogSessionStartSequenceEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().plus(1, ChronoUnit.MINUTES),
          LogSessionStartSequenceEvent.action,
          StartSequenceParameters(None, Some(SequenceId("some-random-sequence")), Seq.empty)
        )
      )

      coordinator ! event1
      coordinator ! UserConnected(sessionId, userId)

      // This event should be forwarded to user history
      coordinator ! event2

      userCoordinatorProbe.expectMsg(5.seconds, InjectSessionEvents(userId, Seq(event1.toUserHistoryEvent(userId))))
      userCoordinatorProbe.reply(SessionEventsInjected)
      userCoordinatorProbe.expectMsg(5.seconds, event2.toUserHistoryEvent(userId))

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
          StartSequenceParameters(None, Some(SequenceId("some-random-sequence")), Seq.empty)
        )
      )
      coordinator ! event3

      userCoordinatorProbe.expectMsg(5.seconds, event3.toUserHistoryEvent(userId))

      coordinator ! GetSessionHistory(sessionId)
      expectMsg(SessionHistory(List(transformation)))

    }

  }
}
