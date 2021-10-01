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

package org.make.api.userhistory

import akka.actor.typed.ActorRef
import org.make.api.ShardingTypedActorTest
import org.make.api.sessionhistory.Ack
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.history.HistoryActions.VoteTrust.Trusted
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.user._
import org.make.core.{DateHelper, RequestContext}

import java.time.temporal.ChronoUnit

class UserHistoryActorTest extends ShardingTypedActorTest {

  val coordinator: ActorRef[UserHistoryCommand] = UserHistoryCoordinator(system)

  Feature("Vote retrieval") {
    Scenario("no vote history") {
      val probe = testKit.createTestProbe[UserHistoryResponse[_]]()
      coordinator ! RequestVoteValues(UserId("no-vote-history"), Seq(ProposalId("proposal1")), probe.ref)
      probe.expectMessage(UserHistoryResponse(Map.empty))
    }

    Scenario("vote on proposal") {
      val probe = testKit.createTestProbe[UserHistoryResponse[_]]()
      val userId = UserId("vote-on-proposal")
      val proposalId = ProposalId("proposal1")
      val event = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now(), "vote", UserVote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      coordinator ! RequestVoteValues(userId, Seq(proposalId), probe.ref)
      val response = probe.expectMessageType[UserHistoryResponse[Map[ProposalId, VoteAndQualifications]]].value
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map.empty
    }

    Scenario("vote then unvote proposal") {
      val probe = testKit.createTestProbe[UserHistoryResponse[_]]()
      val userId = UserId("vote-then-unvote-proposal")
      val proposalId = ProposalId("proposal1")
      val event1 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(10, ChronoUnit.SECONDS), "vote", UserVote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event1, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event2 = LogUserUnvoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now(), "unvote", UserUnvote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event2, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      coordinator ! RequestVoteValues(userId, Seq(proposalId), probe.ref)
      probe.expectMessage(UserHistoryResponse(Map.empty))
    }

    Scenario("vote then unvote then vote proposal") {
      val probe = testKit.createTestProbe[UserHistoryResponse[_]]()
      val userId = UserId("vote-then-unvote-then-vote-proposal")
      val proposalId = ProposalId("proposal1")
      val event1 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "vote",
          UserVote(proposalId, VoteKey.Disagree, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event1, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event2 = LogUserUnvoteEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "unvote",
          UserUnvote(proposalId, VoteKey.Disagree, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event2, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event3 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now(), "vote", UserVote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event3, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      coordinator ! RequestVoteValues(userId, Seq(proposalId), probe.ref)
      val response = probe.expectMessageType[UserHistoryResponse[Map[ProposalId, VoteAndQualifications]]].value
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map.empty
    }

    Scenario("vote then one qualification proposal") {
      val probe = testKit.createTestProbe[UserHistoryResponse[_]]()
      val userId = UserId("vote-then-one-qualification-proposal")
      val proposalId = ProposalId("proposal1")
      val event1 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(10, ChronoUnit.SECONDS), "vote", UserVote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event1, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event2 = LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now(), "qualification", UserQualification(proposalId, QualificationKey.LikeIt, Trusted))
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event2, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      coordinator ! RequestVoteValues(userId, Seq(proposalId), probe.ref)
      val response = probe.expectMessageType[UserHistoryResponse[Map[ProposalId, VoteAndQualifications]]].value
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map(QualificationKey.LikeIt -> Trusted)
    }

    Scenario("vote then two qualifications proposal") {
      val probe = testKit.createTestProbe[UserHistoryResponse[_]]()
      val userId = UserId("vote-then-two-qualification-proposal")
      val proposalId = ProposalId("proposal1")
      val event1 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(10, ChronoUnit.SECONDS), "vote", UserVote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event1, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event2 = LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId, QualificationKey.LikeIt, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event2, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event3 = LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now(),
          "qualification",
          UserQualification(proposalId, QualificationKey.PlatitudeAgree, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event3, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      coordinator ! RequestVoteValues(userId, Seq(proposalId), probe.ref)
      val response = probe.expectMessageType[UserHistoryResponse[Map[ProposalId, VoteAndQualifications]]].value
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map(
        QualificationKey.LikeIt -> Trusted,
        QualificationKey.PlatitudeAgree -> Trusted
      )
    }

    Scenario("vote then three qualification proposal") {
      val probe = testKit.createTestProbe[UserHistoryResponse[_]]()
      val userId = UserId("vote-then-three-qualification-proposal")
      val proposalId = ProposalId("proposal1")
      val event1 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(15, ChronoUnit.SECONDS), "vote", UserVote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event1, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event2 = LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId, QualificationKey.LikeIt, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event2, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event3 = LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId, QualificationKey.Doable, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event3, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event4 = LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now(),
          "qualification",
          UserQualification(proposalId, QualificationKey.PlatitudeAgree, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event4, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      coordinator ! RequestVoteValues(userId, Seq(proposalId), probe.ref)
      val response = probe.expectMessageType[UserHistoryResponse[Map[ProposalId, VoteAndQualifications]]].value
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map(
        QualificationKey.LikeIt -> Trusted,
        QualificationKey.Doable -> Trusted,
        QualificationKey.PlatitudeAgree -> Trusted
      )
    }

    Scenario("vote then qualif then unqualif then requalif proposal") {
      val probe = testKit.createTestProbe[UserHistoryResponse[_]]()
      val userId = UserId("vote-then-qualif-then-unqualif-then-requalif-proposal")
      val proposalId = ProposalId("proposal1")
      val event1 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(15, ChronoUnit.SECONDS), "vote", UserVote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event1, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event2 = LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(12, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId, QualificationKey.LikeIt, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event2, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event3 = LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId, QualificationKey.Doable, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event3, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event4 = LogUserUnqualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(8, ChronoUnit.SECONDS),
          "unqualification",
          UserUnqualification(proposalId, QualificationKey.LikeIt, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event4, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event5 = LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId, QualificationKey.PlatitudeAgree, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event5, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      coordinator ! RequestVoteValues(userId, Seq(proposalId), probe.ref)
      val response = probe.expectMessageType[UserHistoryResponse[Map[ProposalId, VoteAndQualifications]]].value
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Map(
        QualificationKey.Doable -> Trusted,
        QualificationKey.PlatitudeAgree -> Trusted
      )
    }

    Scenario("vote twice on the same proposal with the same vote key") {
      val probe = testKit.createTestProbe[UserHistoryResponse[_]]()
      val userId = UserId("vote-twice-on-proposal-same-vote-key")
      val proposalId = ProposalId("proposal1")
      val event1 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(15, ChronoUnit.SECONDS), "vote", UserVote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event1, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event2 = LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(12, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId, QualificationKey.LikeIt, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event2, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event3 = LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId, QualificationKey.Doable, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event3, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      coordinator ! RequestVoteValues(userId, Seq(proposalId), probe.ref)
      val response1 = probe.expectMessageType[UserHistoryResponse[Map[ProposalId, VoteAndQualifications]]].value
      response1(proposalId).voteKey shouldBe VoteKey.Agree
      response1(proposalId).qualificationKeys shouldBe Map(
        QualificationKey.LikeIt -> Trusted,
        QualificationKey.Doable -> Trusted
      )

      val event4 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(15, ChronoUnit.SECONDS), "vote", UserVote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event4, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      coordinator ! RequestVoteValues(userId, Seq(proposalId), probe.ref)
      val response2 = probe.expectMessageType[UserHistoryResponse[Map[ProposalId, VoteAndQualifications]]].value
      response2(proposalId).voteKey shouldBe VoteKey.Agree
      response2(proposalId).qualificationKeys shouldBe Map(
        QualificationKey.LikeIt -> Trusted,
        QualificationKey.Doable -> Trusted
      )
    }

    Scenario("vote twice on the same proposal with a different vote key") {
      val probe = testKit.createTestProbe[UserHistoryResponse[_]]()
      val userId = UserId("vote-twice-on-proposal")
      val proposalId = ProposalId("proposal1")
      val event1 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(15, ChronoUnit.SECONDS), "vote", UserVote(proposalId, VoteKey.Agree, Trusted))
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event1, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event2 = LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(12, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId, QualificationKey.LikeIt, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event2, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event3 = LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId, QualificationKey.Doable, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event3, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      coordinator ! RequestVoteValues(userId, Seq(proposalId), probe.ref)
      val response1 = probe.expectMessageType[UserHistoryResponse[Map[ProposalId, VoteAndQualifications]]].value
      response1(proposalId).voteKey shouldBe VoteKey.Agree
      response1(proposalId).qualificationKeys shouldBe Map(
        QualificationKey.LikeIt -> Trusted,
        QualificationKey.Doable -> Trusted
      )

      val event4 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(15, ChronoUnit.SECONDS),
          "vote",
          UserVote(proposalId, VoteKey.Disagree, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event4, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      coordinator ! RequestVoteValues(userId, Seq(proposalId), probe.ref)
      val response2 = probe.expectMessageType[UserHistoryResponse[Map[ProposalId, VoteAndQualifications]]].value
      response2(proposalId).voteKey shouldBe VoteKey.Disagree
      response2(proposalId).qualificationKeys shouldBe Map.empty
    }

    Scenario("vote on many proposal") {
      val probe = testKit.createTestProbe[UserHistoryResponse[_]]()
      val userId = UserId("vote-on-many-proposal")
      val proposalId1 = ProposalId("proposal1")
      val proposalId2 = ProposalId("proposal2")
      val proposalId3 = ProposalId("proposal3")
      val event1 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(15, ChronoUnit.SECONDS),
          "vote",
          UserVote(proposalId1, VoteKey.Agree, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event1, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event2 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "vote",
          UserVote(proposalId2, VoteKey.Disagree, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event2, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event3 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "vote",
          UserVote(proposalId3, VoteKey.Neutral, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event3, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      coordinator ! RequestVoteValues(userId, Seq(proposalId1, proposalId2, proposalId3), probe.ref)
      val response = probe.expectMessageType[UserHistoryResponse[Map[ProposalId, VoteAndQualifications]]].value
      response(proposalId1).voteKey shouldBe VoteKey.Agree
      response(proposalId1).qualificationKeys shouldBe Map.empty
      response(proposalId2).voteKey shouldBe VoteKey.Disagree
      response(proposalId2).qualificationKeys shouldBe Map.empty
      response(proposalId3).voteKey shouldBe VoteKey.Neutral
      response(proposalId3).qualificationKeys shouldBe Map.empty
    }

    Scenario("vote and qualif on many proposal") {
      val probe = testKit.createTestProbe[UserHistoryResponse[_]]()
      val userId = UserId("vote-and-qualif-on-many-proposal")
      val proposalId1 = ProposalId("proposal1")
      val proposalId2 = ProposalId("proposal2")
      val proposalId3 = ProposalId("proposal3")
      val event1 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(15, ChronoUnit.SECONDS),
          "vote",
          UserVote(proposalId1, VoteKey.Agree, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event1, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event2 = LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(14, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId1, QualificationKey.Doable, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event2, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event3 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "vote",
          UserVote(proposalId2, VoteKey.Disagree, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event3, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event4 = LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(9, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId2, QualificationKey.Impossible, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event4, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event5 = LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(8, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId2, QualificationKey.NoWay, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event5, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      val event6 = LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "vote",
          UserVote(proposalId3, VoteKey.Neutral, Trusted)
        )
      )
      coordinator ! UserHistoryTransactionalEnvelope(userId, event6, probe.ref)

      probe.expectMessage(UserHistoryResponse(Ack))

      coordinator ! RequestVoteValues(userId, Seq(proposalId1, proposalId2, proposalId3), probe.ref)
      val response = probe.expectMessageType[UserHistoryResponse[Map[ProposalId, VoteAndQualifications]]].value
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
}
