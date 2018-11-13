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

package org.make.api.user

import java.time.temporal.ChronoUnit

import akka.actor.Status.Success
import akka.actor.{ActorRef, ExtendedActorSystem}
import akka.persistence.inmemory.extension.{InMemorySnapshotStorage, StorageExtension, StorageExtensionImpl}
import akka.persistence.serialization.{Snapshot, SnapshotSerializer}
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ShardingActorTest
import org.make.api.userhistory.UserHistoryActor.{
  LogAcknowledged,
  RequestUserVotedProposals,
  RequestVoteValues,
  UserHistory
}
import org.make.api.userhistory.{LogUserVoteEvent, _}
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.user._
import org.make.core.{DateHelper, RequestContext}
import org.scalatest.GivenWhenThen
import scala.concurrent.duration.DurationInt

class UserHistoryActorTest extends ShardingActorTest with GivenWhenThen with StrictLogging {

  private val serializer: SnapshotSerializer = new SnapshotSerializer(system.asInstanceOf[ExtendedActorSystem])
  private val storageExtension: StorageExtensionImpl = StorageExtension.get(system)

  val coordinator: ActorRef =
    system.actorOf(UserHistoryCoordinator.props, UserHistoryCoordinator.name)

  feature("Vote retrieval") {
    scenario("no vote history") {
      coordinator ! RequestVoteValues(UserId("no-vote-history"), Seq(ProposalId("proposal1")))
      expectMsg(Map.empty)
    }

    scenario("vote on proposal") {
      val userId = UserId("vote-on-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now(), "vote", UserVote(proposalId, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestVoteValues(userId, Seq(proposalId))
      val response = expectMsgType[Map[ProposalId, VoteAndQualifications]]
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Seq.empty
    }

    scenario("vote then unvote proposal") {
      val userId = UserId("vote-then-unvote-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(10, ChronoUnit.SECONDS), "vote", UserVote(proposalId, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserUnvoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now(), "unvote", UserUnvote(proposalId, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestVoteValues(userId, Seq(proposalId))
      expectMsg(Map.empty)
    }

    scenario("vote then unvote then vote proposal") {
      val userId = UserId("vote-then-unvote-then-vote-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(10, ChronoUnit.SECONDS), "vote", UserVote(proposalId, VoteKey.Disagree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserUnvoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(5, ChronoUnit.SECONDS), "unvote", UserUnvote(proposalId, VoteKey.Disagree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now(), "vote", UserVote(proposalId, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestVoteValues(userId, Seq(proposalId))
      val response = expectMsgType[Map[ProposalId, VoteAndQualifications]]
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Seq.empty
    }

    scenario("vote then one qualification proposal") {
      val userId = UserId("vote-then-one-qualification-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(10, ChronoUnit.SECONDS), "vote", UserVote(proposalId, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now(), "qualification", UserQualification(proposalId, QualificationKey.LikeIt))
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestVoteValues(userId, Seq(proposalId))
      val response = expectMsgType[Map[ProposalId, VoteAndQualifications]]
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Seq(QualificationKey.LikeIt)
    }

    scenario("vote then two qualifications proposal") {
      val userId = UserId("vote-then-two-qualification-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(10, ChronoUnit.SECONDS), "vote", UserVote(proposalId, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId, QualificationKey.LikeIt)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now(), "qualification", UserQualification(proposalId, QualificationKey.PlatitudeAgree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestVoteValues(userId, Seq(proposalId))
      val response = expectMsgType[Map[ProposalId, VoteAndQualifications]]
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Seq(QualificationKey.LikeIt, QualificationKey.PlatitudeAgree)
    }

    scenario("vote then three qualification proposal") {
      val userId = UserId("vote-then-three-qualification-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(15, ChronoUnit.SECONDS), "vote", UserVote(proposalId, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId, QualificationKey.LikeIt)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId, QualificationKey.Doable)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now(), "qualification", UserQualification(proposalId, QualificationKey.PlatitudeAgree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestVoteValues(userId, Seq(proposalId))
      val response = expectMsgType[Map[ProposalId, VoteAndQualifications]]
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Seq(
        QualificationKey.LikeIt,
        QualificationKey.Doable,
        QualificationKey.PlatitudeAgree
      )
    }

    scenario("vote then qualif then unqualif then requalif proposal") {
      val userId = UserId("vote-then-qualif-then-unqualif-then-requalif-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(15, ChronoUnit.SECONDS), "vote", UserVote(proposalId, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(12, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId, QualificationKey.LikeIt)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId, QualificationKey.Doable)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserUnqualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(8, ChronoUnit.SECONDS),
          "unqualification",
          UserUnqualification(proposalId, QualificationKey.LikeIt)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId, QualificationKey.PlatitudeAgree)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestVoteValues(userId, Seq(proposalId))
      val response = expectMsgType[Map[ProposalId, VoteAndQualifications]]
      response(proposalId).voteKey shouldBe VoteKey.Agree
      response(proposalId).qualificationKeys shouldBe Seq(QualificationKey.Doable, QualificationKey.PlatitudeAgree)
    }

    scenario("vote on many proposal") {
      val userId = UserId("vote-on-many-proposal")
      val proposalId1 = ProposalId("proposal1")
      val proposalId2 = ProposalId("proposal2")
      val proposalId3 = ProposalId("proposal3")
      coordinator ! LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(15, ChronoUnit.SECONDS), "vote", UserVote(proposalId1, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(10, ChronoUnit.SECONDS), "vote", UserVote(proposalId2, VoteKey.Disagree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(5, ChronoUnit.SECONDS), "vote", UserVote(proposalId3, VoteKey.Neutral))
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestVoteValues(userId, Seq(proposalId1, proposalId2, proposalId3))
      val response = expectMsgType[Map[ProposalId, VoteAndQualifications]]
      response(proposalId1).voteKey shouldBe VoteKey.Agree
      response(proposalId1).qualificationKeys shouldBe Seq.empty
      response(proposalId2).voteKey shouldBe VoteKey.Disagree
      response(proposalId2).qualificationKeys shouldBe Seq.empty
      response(proposalId3).voteKey shouldBe VoteKey.Neutral
      response(proposalId3).qualificationKeys shouldBe Seq.empty
    }

    scenario("vote and qualif on many proposal") {
      val userId = UserId("vote-and-qualif-on-many-proposal")
      val proposalId1 = ProposalId("proposal1")
      val proposalId2 = ProposalId("proposal2")
      val proposalId3 = ProposalId("proposal3")
      coordinator ! LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(15, ChronoUnit.SECONDS), "vote", UserVote(proposalId1, VoteKey.Agree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(14, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId1, QualificationKey.Doable)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(10, ChronoUnit.SECONDS), "vote", UserVote(proposalId2, VoteKey.Disagree))
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(9, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId2, QualificationKey.Impossible)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserQualificationEvent(
        userId,
        RequestContext.empty,
        UserAction(
          DateHelper.now().minus(8, ChronoUnit.SECONDS),
          "qualification",
          UserQualification(proposalId2, QualificationKey.NoWay)
        )
      )

      expectMsg(LogAcknowledged)

      coordinator ! LogUserVoteEvent(
        userId,
        RequestContext.empty,
        UserAction(DateHelper.now().minus(5, ChronoUnit.SECONDS), "vote", UserVote(proposalId3, VoteKey.Neutral))
      )

      expectMsg(LogAcknowledged)

      coordinator ! RequestVoteValues(userId, Seq(proposalId1, proposalId2, proposalId3))
      val response = expectMsgType[Map[ProposalId, VoteAndQualifications]]
      response(proposalId1).voteKey shouldBe VoteKey.Agree
      response(proposalId1).qualificationKeys shouldBe Seq(QualificationKey.Doable)
      response(proposalId2).voteKey shouldBe VoteKey.Disagree
      response(proposalId2).qualificationKeys shouldBe Seq(QualificationKey.Impossible, QualificationKey.NoWay)
      response(proposalId3).voteKey shouldBe VoteKey.Neutral
      response(proposalId3).qualificationKeys shouldBe Seq.empty
    }
  }
  feature("recover from old snapshot") {

    scenario("empty user history") {
      val history = UserHistory(Nil)

      val encodedValue = serializer.toBinary(Snapshot(history))
      storageExtension.snapshotStorage !
        InMemorySnapshotStorage.Save("empty-user-history", 0, DateHelper.now().toEpochSecond, encodedValue)

      expectMsg(Success(""))

      coordinator ! RequestUserVotedProposals(UserId("empty-user-history"))
      expectMsg(Seq.empty[ProposalId])
    }

    scenario("user history with a vote") {
      val history = UserHistory(
        List(
          LogUserVoteEvent(
            UserId("user-history-with-vote"),
            RequestContext.empty,
            UserAction(DateHelper.now(), LogUserVoteEvent.action, UserVote(ProposalId("voted"), VoteKey.Agree))
          )
        )
      )

      val encodedValue = serializer.toBinary(Snapshot(history))
      storageExtension.snapshotStorage !
        InMemorySnapshotStorage.Save("user-history-with-vote", 0, DateHelper.now().toEpochSecond, encodedValue)

      expectMsg(Success(""))

      coordinator ! RequestUserVotedProposals(UserId("user-history-with-vote"))
      expectMsg(Seq(ProposalId("voted")))
    }
  }

  feature("retrieve filtered user voted proposals") {
    Given("""6 proposals:
            |  Proposal1: likeIt|agree
            |  Proposal2: likeIt|disagree
            |  Proposal3: noway|agree
            |  Proposal4: noway|disagree
            |  Proposal5: agree
            |  Proposal6: likeIt|doable|platitudeAgree|agree
          """.stripMargin)
    val proposal1 = ProposalId("agree-likeIt")
    val proposal2 = ProposalId("disagree-likeIt")
    val proposal3 = ProposalId("agree-noWay")
    val proposal4 = ProposalId("disagree-noWay")
    val proposal5 = ProposalId("agree")
    val proposal6 = ProposalId("agree-likeIt-doable-platitudeAgree")
    val history = UserHistory(
      List(
        LogUserVoteEvent(
          UserId("1"),
          RequestContext.empty,
          UserAction(DateHelper.now(), LogUserVoteEvent.action, UserVote(proposal1, VoteKey.Agree))
        ),
        LogUserQualificationEvent(
          UserId("1"),
          RequestContext.empty,
          UserAction(
            DateHelper.now(),
            LogUserQualificationEvent.action,
            UserQualification(proposal1, QualificationKey.LikeIt)
          )
        ),
        LogUserVoteEvent(
          UserId("1"),
          RequestContext.empty,
          UserAction(DateHelper.now(), LogUserVoteEvent.action, UserVote(proposal2, VoteKey.Disagree))
        ),
        LogUserQualificationEvent(
          UserId("1"),
          RequestContext.empty,
          UserAction(
            DateHelper.now(),
            LogUserQualificationEvent.action,
            UserQualification(proposal2, QualificationKey.LikeIt)
          )
        ),
        LogUserVoteEvent(
          UserId("1"),
          RequestContext.empty,
          UserAction(DateHelper.now(), LogUserVoteEvent.action, UserVote(proposal3, VoteKey.Agree))
        ),
        LogUserQualificationEvent(
          UserId("1"),
          RequestContext.empty,
          UserAction(
            DateHelper.now(),
            LogUserQualificationEvent.action,
            UserQualification(proposal3, QualificationKey.NoWay)
          )
        ),
        LogUserVoteEvent(
          UserId("1"),
          RequestContext.empty,
          UserAction(DateHelper.now(), LogUserVoteEvent.action, UserVote(proposal4, VoteKey.Disagree))
        ),
        LogUserQualificationEvent(
          UserId("1"),
          RequestContext.empty,
          UserAction(
            DateHelper.now(),
            LogUserQualificationEvent.action,
            UserQualification(proposal4, QualificationKey.NoWay)
          )
        ),
        LogUserVoteEvent(
          UserId("1"),
          RequestContext.empty,
          UserAction(DateHelper.now(), LogUserVoteEvent.action, UserVote(proposal5, VoteKey.Agree))
        ),
        LogUserVoteEvent(
          UserId("1"),
          RequestContext.empty,
          UserAction(DateHelper.now(), LogUserVoteEvent.action, UserVote(proposal6, VoteKey.Agree))
        ),
        LogUserQualificationEvent(
          UserId("1"),
          RequestContext.empty,
          UserAction(
            DateHelper.now(),
            LogUserQualificationEvent.action,
            UserQualification(proposal6, QualificationKey.LikeIt)
          )
        ),
        LogUserQualificationEvent(
          UserId("1"),
          RequestContext.empty,
          UserAction(
            DateHelper.now(),
            LogUserQualificationEvent.action,
            UserQualification(proposal6, QualificationKey.Doable)
          )
        ),
        LogUserQualificationEvent(
          UserId("1"),
          RequestContext.empty,
          UserAction(
            DateHelper.now(),
            LogUserQualificationEvent.action,
            UserQualification(proposal6, QualificationKey.PlatitudeAgree)
          )
        )
      )
    )

    scenario("no filters returns all proposals") {
      val encodedValue = serializer.toBinary(Snapshot(history))
      storageExtension.snapshotStorage !
        InMemorySnapshotStorage.Save("1", 0, DateHelper.now().toEpochSecond, encodedValue)

      expectMsg(Success(""))

      coordinator ! RequestUserVotedProposals(UserId("1"), filterVotes = None, filterQualifications = None)
      val foundProposals: Seq[ProposalId] = expectMsgType[Seq[ProposalId]](3.seconds)
      foundProposals.toSet == Set(proposal1, proposal2, proposal3, proposal4, proposal5, proposal6) shouldBe true
    }

    scenario("filter on one vote") {
      val encodedValue = serializer.toBinary(Snapshot(history))
      storageExtension.snapshotStorage !
        InMemorySnapshotStorage.Save("1", 0, DateHelper.now().toEpochSecond, encodedValue)

      expectMsg(Success(""))

      coordinator ! RequestUserVotedProposals(
        UserId("1"),
        filterVotes = Some(Seq(VoteKey.Agree)),
        filterQualifications = None
      )
      val foundProposals: Seq[ProposalId] = expectMsgType[Seq[ProposalId]](3.seconds)
      foundProposals.toSet == Set(proposal1, proposal3, proposal5, proposal6) shouldBe true
    }

    scenario("filter on one of the qualifications") {
      val encodedValue = serializer.toBinary(Snapshot(history))
      storageExtension.snapshotStorage !
        InMemorySnapshotStorage.Save("1", 0, DateHelper.now().toEpochSecond, encodedValue)

      expectMsg(Success(""))

      coordinator ! RequestUserVotedProposals(
        UserId("1"),
        filterVotes = None,
        filterQualifications = Some(Seq(QualificationKey.LikeIt, QualificationKey.NoWay))
      )
      val foundProposals: Seq[ProposalId] = expectMsgType[Seq[ProposalId]](3.seconds)
      foundProposals.toSet.diff(Set(proposal1, proposal2, proposal3, proposal4, proposal6)) shouldBe Set.empty
    }

    scenario("combine vote AND qualification filters") {
      val encodedValue = serializer.toBinary(Snapshot(history))
      storageExtension.snapshotStorage !
        InMemorySnapshotStorage.Save("1", 0, DateHelper.now().toEpochSecond, encodedValue)

      expectMsg(Success(""))

      coordinator ! RequestUserVotedProposals(
        UserId("1"),
        filterVotes = Some(Seq(VoteKey.Agree, VoteKey.Neutral)),
        filterQualifications = Some(Seq(QualificationKey.LikeIt, QualificationKey.NoWay))
      )
      val foundProposals: Seq[ProposalId] = expectMsgType[Seq[ProposalId]](3.seconds)
      foundProposals.toSet.diff(Set(proposal1, proposal3, proposal6)) shouldBe Set.empty

      coordinator ! RequestUserVotedProposals(
        UserId("1"),
        filterVotes = Some(Seq(VoteKey.Agree, VoteKey.Disagree)),
        filterQualifications = Some(Seq(QualificationKey.NoWay))
      )
      val foundOtherProposals: Seq[ProposalId] = expectMsgType[Seq[ProposalId]](3.seconds)
      foundOtherProposals.toSet.diff(Set(proposal3, proposal4, proposal6)) shouldBe Set.empty
    }

  }
}
