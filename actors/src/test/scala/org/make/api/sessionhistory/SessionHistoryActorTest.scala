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

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import org.make.api.ShardingActorTest
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.sessionhistory.SessionHistoryResponse.Error.{ExpiredSession, LockAlreadyAcquired}
import org.make.api.sessionhistory.SessionHistoryResponse.{Envelope, LockAcquired, LockReleased}
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.api.userhistory.UserHistoryResponse.SessionEventsInjected
import org.make.api.userhistory._
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.history.HistoryActions.VoteTrust.Trusted
import org.make.core.proposal.VoteKey.Agree
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.sequence.SequenceId
import org.make.core.session.SessionId
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext}

import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{Duration, DurationInt}

class SessionHistoryActorTest extends ShardingActorTest with DefaultIdGeneratorComponent with MakeSettingsComponent {

  val userCoordinatorProbe: TestProbe[UserHistoryCommand] = testKit.createTestProbe[UserHistoryCommand]()
  override val makeSettings: MakeSettings = mock[MakeSettings]
  when(makeSettings.maxUserHistoryEvents).thenReturn(10000)
  protected val sessionCookieConfiguration: makeSettings.SessionCookie.type = mock[makeSettings.SessionCookie.type]
  when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  when(sessionCookieConfiguration.lifetime).thenReturn(Duration("800 milliseconds"))

  val coordinator: ActorRef[SessionHistoryCommand] =
    SessionHistoryCoordinator(system, userCoordinatorProbe.ref, idGenerator, makeSettings, 500.milliseconds)

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  Feature("Vote retrieval") {
    Scenario("no vote history") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
      coordinator ! SessionVoteValuesCommand(SessionId("no-vote-history"), Seq(ProposalId("proposal1")), probe.ref)
      probe.expectMessage(Envelope(Map.empty))
    }

    Scenario("vote on proposal") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
      val sessionId = SessionId("vote-on-proposal")
      val proposalId = ProposalId("proposal1")
      val event = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now(), "vote", SessionVote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! EventEnvelope(sessionId, event, probe.ref)

      probe.expectMessage(Envelope(Ack))

      coordinator ! SessionVoteValuesCommand(sessionId, Seq(proposalId), probe.ref)
      val response: Map[ProposalId, VoteAndQualifications] =
        probe.expectMessageType[Envelope[Map[ProposalId, VoteAndQualifications]]].value
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map.empty
    }

    Scenario("vote then unvote proposal") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
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
      coordinator ! EventEnvelope(sessionId, event1, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event2 = LogSessionUnvoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now(), "unvote", SessionUnvote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! EventEnvelope(sessionId, event2, probe.ref)

      probe.expectMessage(Envelope(Ack))

      coordinator ! SessionVoteValuesCommand(sessionId, Seq(proposalId), probe.ref)
      probe.expectMessage(Envelope(Map.empty))
    }

    Scenario("vote then unvote then vote proposal") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
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
      coordinator ! EventEnvelope(sessionId, event1, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event2 = LogSessionUnvoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "unvote",
          SessionUnvote(proposalId, VoteKey.Disagree, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event2, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event3 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now(), "vote", SessionVote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! EventEnvelope(sessionId, event3, probe.ref)

      probe.expectMessage(Envelope(Ack))

      coordinator ! SessionVoteValuesCommand(sessionId, Seq(proposalId), probe.ref)
      val response: Map[ProposalId, VoteAndQualifications] =
        probe.expectMessageType[Envelope[Map[ProposalId, VoteAndQualifications]]].value
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map.empty
    }

    Scenario("vote then one qualification proposal") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
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
      coordinator ! EventEnvelope(sessionId, event1, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event2 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now(),
          "qualification",
          SessionQualification(proposalId, QualificationKey.LikeIt, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event2, probe.ref)

      probe.expectMessage(Envelope(Ack))

      coordinator ! SessionVoteValuesCommand(sessionId, Seq(proposalId), probe.ref)
      val response: Map[ProposalId, VoteAndQualifications] =
        probe.expectMessageType[Envelope[Map[ProposalId, VoteAndQualifications]]].value
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map(QualificationKey.LikeIt -> Trusted)
    }

    Scenario("vote then two qualifications proposal") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
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
      coordinator ! EventEnvelope(sessionId, event1, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event2 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.LikeIt, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event2, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event3 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now(),
          "qualification",
          SessionQualification(proposalId, QualificationKey.PlatitudeAgree, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event3, probe.ref)

      probe.expectMessage(Envelope(Ack))

      coordinator ! SessionVoteValuesCommand(sessionId, Seq(proposalId), probe.ref)
      val response: Map[ProposalId, VoteAndQualifications] =
        probe.expectMessageType[Envelope[Map[ProposalId, VoteAndQualifications]]].value
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map(
        QualificationKey.LikeIt -> Trusted,
        QualificationKey.PlatitudeAgree -> Trusted
      )
    }

    Scenario("vote then three qualification proposal") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
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
      coordinator ! EventEnvelope(sessionId, event1, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event2 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.LikeIt, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event2, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event3 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.Doable, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event3, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event4 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now(),
          "qualification",
          SessionQualification(proposalId, QualificationKey.PlatitudeAgree, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event4, probe.ref)

      probe.expectMessage(Envelope(Ack))

      coordinator ! SessionVoteValuesCommand(sessionId, Seq(proposalId), probe.ref)
      val response: Map[ProposalId, VoteAndQualifications] =
        probe.expectMessageType[Envelope[Map[ProposalId, VoteAndQualifications]]].value
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map(
        QualificationKey.Doable -> Trusted,
        QualificationKey.LikeIt -> Trusted,
        QualificationKey.PlatitudeAgree -> Trusted
      )
    }

    Scenario("vote then qualif then unqualif then requalif proposal") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
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
      coordinator ! EventEnvelope(sessionId, event1, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event2 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(12, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.LikeIt, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event2, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event3 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.Doable, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event3, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event4 = LogSessionUnqualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(8, ChronoUnit.SECONDS),
          "unqualification",
          SessionUnqualification(proposalId, QualificationKey.LikeIt, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event4, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event5 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.PlatitudeAgree, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event5, probe.ref)

      probe.expectMessage(Envelope(Ack))

      coordinator ! SessionVoteValuesCommand(sessionId, Seq(proposalId), probe.ref)
      val response: Map[ProposalId, VoteAndQualifications] =
        probe.expectMessageType[Envelope[Map[ProposalId, VoteAndQualifications]]].value
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map(
        QualificationKey.Doable -> Trusted,
        QualificationKey.PlatitudeAgree -> Trusted
      )
    }

    Scenario("vote on many proposal") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
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
      coordinator ! EventEnvelope(sessionId, event1, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event2 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId2, VoteKey.Disagree, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event2, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event3 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId3, VoteKey.Neutral, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event3, probe.ref)

      probe.expectMessage(Envelope(Ack))

      coordinator ! SessionVoteValuesCommand(sessionId, Seq(proposalId1, proposalId2, proposalId3), probe.ref)
      val response: Map[ProposalId, VoteAndQualifications] =
        probe.expectMessageType[Envelope[Map[ProposalId, VoteAndQualifications]]].value
      response(proposalId1).voteKey shouldBe VoteKey.Agree
      response(proposalId1).qualificationKeys shouldBe Map.empty
      response(proposalId2).voteKey shouldBe VoteKey.Disagree
      response(proposalId2).qualificationKeys shouldBe Map.empty
      response(proposalId3).voteKey shouldBe VoteKey.Neutral
      response(proposalId3).qualificationKeys shouldBe Map.empty
    }

    Scenario("vote and qualif on many proposal") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
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
      coordinator ! EventEnvelope(sessionId, event1, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event2 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(14, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId1, QualificationKey.Doable, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event2, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event3 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId2, VoteKey.Disagree, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event3, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event4 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(9, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId2, QualificationKey.Impossible, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event4, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event5 = LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(8, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId2, QualificationKey.NoWay, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event5, probe.ref)

      probe.expectMessage(Envelope(Ack))

      val event6 = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId3, VoteKey.Neutral, Trusted)
        )
      )
      coordinator ! EventEnvelope(sessionId, event6, probe.ref)

      probe.expectMessage(Envelope(Ack))

      coordinator ! SessionVoteValuesCommand(sessionId, Seq(proposalId1, proposalId2, proposalId3), probe.ref)
      val response: Map[ProposalId, VoteAndQualifications] =
        probe.expectMessageType[Envelope[Map[ProposalId, VoteAndQualifications]]].value
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

  Feature("SessionVoteValuesCommand on closed session") {
    Scenario("no vote on user") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
      val identifier = "SessionVoteValuesCommand on closed session - no vote on user"
      coordinator ! UserConnected(SessionId(identifier), UserId(identifier), RequestContext.empty, probe.ref)
      userCoordinatorProbe.expectMessageType[InjectSessionEvents].replyTo ! UserHistoryResponse(SessionEventsInjected)
      probe.expectMessage(Envelope(Ack))

      coordinator ! SessionVoteValuesCommand(SessionId(identifier), Seq(ProposalId("proposal1")), probe.ref)
      val request = userCoordinatorProbe.expectMessageType[RequestVoteValues]
      request.replyTo ! UserHistoryResponse(Map.empty)

      probe.expectMessage(Envelope(Map.empty))
    }

    Scenario("votes on user") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
      val identifier = "SessionVoteValuesCommand on closed session - votes on user"
      coordinator ! UserConnected(SessionId(identifier), UserId(identifier), RequestContext.empty, probe.ref)
      userCoordinatorProbe.expectMessageType[InjectSessionEvents].replyTo ! UserHistoryResponse(SessionEventsInjected)
      probe.expectMessage(Envelope(Ack))

      val proposalId = ProposalId("proposal1")
      coordinator ! SessionVoteValuesCommand(SessionId(identifier), Seq(proposalId), probe.ref)
      val request = userCoordinatorProbe.expectMessageType[RequestVoteValues]
      val votedProposals = Map(proposalId -> VoteAndQualifications(Agree, Map.empty, DateHelper.now(), Trusted))
      request.replyTo ! UserHistoryResponse(votedProposals)

      probe.expectMessage(Envelope(votedProposals))
    }
  }

  Feature("SessionVotedProposalsCommandPaginate on closed session") {
    Scenario("no vote on user") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
      val identifier = "SessionVotedProposalsCommandPaginate on closed session - no vote on user"
      coordinator ! UserConnected(SessionId(identifier), UserId(identifier), RequestContext.empty, probe.ref)
      userCoordinatorProbe.expectMessageType[InjectSessionEvents].replyTo ! UserHistoryResponse(SessionEventsInjected)
      probe.expectMessage(Envelope(Ack))

      coordinator ! SessionVotedProposalsPaginateCommand(SessionId(identifier), None, 999, 999, probe.ref)
      val request = userCoordinatorProbe.expectMessageType[RequestUserVotedProposalsPaginate]
      request.replyTo ! UserHistoryResponse(Seq.empty)

      probe.expectMessage(Envelope(Seq.empty))
    }

    Scenario("votes on user") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
      val identifier = "SessionVotedProposalsCommandPaginate on closed session - votes on user"
      coordinator ! UserConnected(SessionId(identifier), UserId(identifier), RequestContext.empty, probe.ref)
      userCoordinatorProbe.expectMessageType[InjectSessionEvents].replyTo ! UserHistoryResponse(SessionEventsInjected)
      probe.expectMessage(Envelope(Ack))

      val proposalId = ProposalId("proposal1")
      coordinator ! SessionVotedProposalsPaginateCommand(SessionId(identifier), None, 999, 999, probe.ref)
      val request = userCoordinatorProbe.expectMessageType[RequestUserVotedProposalsPaginate]
      val votedProposals = Seq(proposalId)
      request.replyTo ! UserHistoryResponse(votedProposals)

      probe.expectMessage(Envelope(votedProposals))
    }
  }

  Feature("session transformation") {
    Scenario("normal case") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
      val sessionId = SessionId("normal-session-transformation")
      val userId = UserId("normal-user-id")
      val lastEventDate = DateHelper.now()
      val afterLastEventDate = lastEventDate.plusMinutes(1)
      val event1 = LogSessionStartSequenceEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          lastEventDate,
          LogSessionStartSequenceEvent.action,
          StartSequenceParameters(None, None, Some(SequenceId("some-random-sequence")), Seq.empty)
        )
      )
      val event2 = LogSessionStartSequenceEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          afterLastEventDate,
          LogSessionStartSequenceEvent.action,
          StartSequenceParameters(None, None, Some(SequenceId("some-random-sequence")), Seq.empty)
        )
      )

      coordinator ! EventEnvelope(sessionId, event1, probe.ref)
      probe.expectMessage(Envelope(Ack))

      coordinator ! GetState(sessionId, probe.ref)
      val activeState = probe.expectMessageType[Envelope[Active]]
      activeState.value.sessionHistory.events.size shouldBe 1
      activeState.value.sessionHistory.events.head shouldBe event1

      coordinator ! UserConnected(sessionId, userId, RequestContext.empty, probe.ref)

      // This event should be added to pendingEvents whilst transforming
      coordinator ! EventEnvelope(sessionId, event2, probe.ref)

      val eventsInjected = userCoordinatorProbe.expectMessageType[InjectSessionEvents](5.seconds)
      eventsInjected.userId shouldBe userId
      eventsInjected.events shouldBe Seq(event1.toUserHistoryEvent(userId))
      eventsInjected.replyTo ! UserHistoryResponse(SessionEventsInjected)

      // from above UserConnected
      probe.expectMessage(Envelope(Ack))

      val messageForwarded = userCoordinatorProbe.expectMessageType[UserHistoryTransactionalEnvelope[_]](5.seconds)
      messageForwarded.event shouldBe event2.toUserHistoryEvent(userId)
      messageForwarded.replyTo ! UserHistoryResponse(Ack)

      probe.expectMessage(Envelope(Ack))

      coordinator ! GetState(sessionId, probe.ref)
      val closedState1 = probe.expectMessageType[Envelope[Closed]]
      closedState1.value.userId shouldBe userId
      closedState1.value.lastEventDate.get.isAfter(lastEventDate) shouldBe true
      closedState1.value.lastEventDate.get.isBefore(afterLastEventDate) shouldBe true

      coordinator ! StopSessionActor(sessionId, probe.ref)
      probe.expectMessage(Envelope(Ack))

      Thread.sleep(500)

      coordinator ! GetState(sessionId, probe.ref)
      val closedState2 = probe.expectMessageType[Envelope[Closed]]
      closedState2.value.userId shouldBe userId
      closedState1.value.lastEventDate.get.isAfter(lastEventDate) shouldBe true
      closedState1.value.lastEventDate.get.isBefore(afterLastEventDate) shouldBe true

      val event3 = LogSessionStartSequenceEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          afterLastEventDate.plusMinutes(1),
          LogSessionStartSequenceEvent.action,
          StartSequenceParameters(None, None, Some(SequenceId("some-random-sequence")), Seq.empty)
        )
      )
      coordinator ! EventEnvelope(sessionId, event3, probe.ref)

      val messageForwarded2 = userCoordinatorProbe.expectMessageType[UserHistoryTransactionalEnvelope[_]](5.seconds)
      messageForwarded2.event shouldBe event3.toUserHistoryEvent(userId)
      messageForwarded2.replyTo ! UserHistoryResponse(Ack)

      probe.expectMessage(Envelope(Ack))
      Thread.sleep(800)

      val newSessionId = SessionId("new-session-after-expired")
      coordinator ! GetCurrentSession(sessionId, newSessionId, probe.ref)
      probe.expectMessage(Envelope(newSessionId))

      coordinator ! GetState(sessionId, probe.ref)
      val expiredState = probe.expectMessageType[Envelope[Expired]]
      expiredState.value.newSessionId shouldBe newSessionId
      closedState1.value.lastEventDate.get.isAfter(lastEventDate) shouldBe true
      closedState1.value.lastEventDate.get.isBefore(afterLastEventDate) shouldBe true

    }

  }

  Feature("locking for votes") {
    Scenario("locking for vote") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
      val sessionId = SessionId("locking-for-vote")
      val proposalId = ProposalId("locking-for-vote")

      coordinator ! LockProposalForVote(sessionId, proposalId, probe.ref)
      probe.expectMessage(LockAcquired)

      coordinator ! LockProposalForVote(sessionId, proposalId, probe.ref)
      probe.expectMessageType[LockAlreadyAcquired]

      coordinator ! LockProposalForQualification(sessionId, proposalId, QualificationKey.Doable, probe.ref)
      probe.expectMessageType[LockAlreadyAcquired]

      coordinator ! ReleaseProposalForVote(sessionId, proposalId, probe.ref)
      probe.expectMessage(LockReleased)

      coordinator ! LockProposalForVote(sessionId, proposalId, probe.ref)
      probe.expectMessage(LockAcquired)
    }

    Scenario("deadline security") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
      val sessionId = SessionId("deadline-security")
      val proposalId = ProposalId("deadline-security")

      coordinator ! LockProposalForVote(sessionId, proposalId, probe.ref)
      probe.expectMessage(LockAcquired)

      Thread.sleep(600)

      coordinator ! LockProposalForVote(sessionId, proposalId, probe.ref)
      probe.expectMessage(LockAcquired)
    }
  }

  Feature("locking qualifications") {
    Scenario("multiple qualifications") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
      val sessionId = SessionId("locking-for-qualification")
      val proposalId = ProposalId("locking-for-qualification")

      val qualification1 = QualificationKey.LikeIt
      val qualification2 = QualificationKey.Doable

      coordinator ! LockProposalForQualification(sessionId, proposalId, qualification1, probe.ref)
      probe.expectMessage(LockAcquired)

      coordinator ! LockProposalForQualification(sessionId, proposalId, qualification1, probe.ref)
      probe.expectMessageType[LockAlreadyAcquired]

      coordinator ! LockProposalForVote(sessionId, proposalId, probe.ref)
      probe.expectMessageType[LockAlreadyAcquired]

      coordinator ! LockProposalForQualification(sessionId, proposalId, qualification2, probe.ref)
      probe.expectMessage(LockAcquired)

      coordinator ! ReleaseProposalForQualification(sessionId, proposalId, qualification1, probe.ref)
      probe.expectMessage(LockReleased)

      coordinator ! LockProposalForVote(sessionId, proposalId, probe.ref)
      probe.expectMessageType[LockAlreadyAcquired]

      coordinator ! ReleaseProposalForQualification(sessionId, proposalId, qualification2, probe.ref)
      probe.expectMessage(LockReleased)

      coordinator ! LockProposalForVote(sessionId, proposalId, probe.ref)
      probe.expectMessage(LockAcquired)

    }
  }

  Feature("session expired") {
    Scenario("Session expired") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
      val sessionId = SessionId("session-expired")
      val newSessionId = SessionId("new-session")
      val event = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now(), "vote", SessionVote(ProposalId("proposal-id"), VoteKey.Agree, Trusted))
      )

      coordinator ! EventEnvelope(sessionId, event, probe.ref)
      probe.expectMessage(Envelope(Ack))

      Thread.sleep(1000)

      coordinator ! GetCurrentSession(sessionId, newSessionId, probe.ref)
      probe.expectMessage(Envelope(newSessionId))

      coordinator ! GetState(sessionId, probe.ref)
      val expiredState = probe.expectMessageType[Envelope[Expired]]
      expiredState.value.newSessionId shouldBe newSessionId
    }

    Scenario("session expired after user connected") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
      val sessionId = SessionId("session-expired-2")
      val newSessionId = SessionId("new-session-2")
      val userId = UserId("user-id-session-expired")

      coordinator ! GetState(sessionId, probe.ref)
      val activeState = probe.expectMessageType[Envelope[Active]]
      activeState.value.sessionHistory.events shouldBe empty

      coordinator ! UserConnected(sessionId, userId, RequestContext.empty, probe.ref)
      val command = userCoordinatorProbe.expectMessageType[InjectSessionEvents](5.seconds)
      command.userId shouldBe userId
      command.events shouldBe Seq.empty
      command.replyTo ! UserHistoryResponse(SessionEventsInjected)
      probe.expectMessage(Envelope(Ack))

      Thread.sleep(1000)

      coordinator ! GetCurrentSession(sessionId, newSessionId, probe.ref)
      probe.expectMessage(Envelope(newSessionId))

      coordinator ! GetState(sessionId, probe.ref)
      val expiredState = probe.expectMessageType[Envelope[Expired]]
      expiredState.value.newSessionId shouldBe newSessionId

      coordinator ! LockProposalForVote(sessionId, ProposalId("id"), probe.ref)
      probe.expectMessage(ExpiredSession(newSessionId))
    }

    Scenario("unexpected actor shutdown") {
      val probe = testKit.createTestProbe[SessionHistoryResponse[_, _]]()
      val sessionId = SessionId("session-expired-3")
      val newSessionId = SessionId("new-session-3")
      val event = LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now(), "vote", SessionVote(ProposalId("proposal-id"), VoteKey.Agree, Trusted))
      )

      coordinator ! EventEnvelope(sessionId, event, probe.ref)
      probe.expectMessage(Envelope(Ack))

      coordinator ! StopSessionActor(sessionId, probe.ref)
      probe.expectMessage(Envelope(Ack))

      Thread.sleep(400)

      coordinator ! GetCurrentSession(sessionId, newSessionId, probe.ref)
      probe.expectMessage(Envelope(sessionId))
    }
  }

  Feature("Snapshot adapter") {
    val snapshotAdapter = SessionHistoryActor.snapshotAdapter
    val date = DateHelper.now()
    val voteEvent = LogSessionVoteEvent(
      SessionId("1"),
      RequestContext.empty,
      SessionAction(date.minusSeconds(3), "vote", SessionVote(ProposalId("id"), Agree, Trusted))
    )
    val transforming =
      SessionTransforming(
        SessionId("1"),
        RequestContext.empty,
        SessionAction(date.minusSeconds(2), "expired", UserId("user-id"))
      )
    val transformed =
      SessionTransformed(
        SessionId("1"),
        RequestContext.empty,
        SessionAction(date.minusSeconds(1), "expired", UserId("user-id"))
      )
    val expired =
      SessionExpired(SessionId("1"), RequestContext.empty, SessionAction(date, "expired", SessionId("new-id")))
    Scenario("State ok") {
      val sessionHistory = SessionHistory(events = List(voteEvent))

      snapshotAdapter.fromJournal(sessionHistory) shouldBe Active(sessionHistory, Some(voteEvent.action.date))
    }

    Scenario("session transforming") {
      val sessionHistory = SessionHistory(events = List(voteEvent, transforming))

      snapshotAdapter.fromJournal(sessionHistory) shouldBe Transforming(
        sessionHistory,
        RequestContext.empty,
        Some(transforming.action.date)
      )
    }

    Scenario("session closed") {
      val sessionHistory = SessionHistory(events = List(voteEvent, transforming, transformed))

      snapshotAdapter.fromJournal(sessionHistory) shouldBe Closed(UserId("user-id"), Some(transformed.action.date))
    }

    Scenario("session expired") {
      val sessionHistory = SessionHistory(events = List(voteEvent, transforming, transformed, expired))

      snapshotAdapter.fromJournal(sessionHistory) shouldBe Expired(SessionId("new-id"), Some(expired.action.date))
    }
  }
}
