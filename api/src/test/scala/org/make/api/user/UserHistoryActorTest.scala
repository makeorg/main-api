package org.make.api.user

import java.time.temporal.ChronoUnit

import akka.actor.ActorRef
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ShardingActorTest
import org.make.api.proposal.PublishedProposalEvent.ProposalLocked
import org.make.api.userhistory.UserHistoryActor.{LogAcknowledged, RequestVoteValues, UserHistory}
import org.make.api.userhistory._
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.user._
import org.make.core.{DateHelper, RequestContext}
import org.scalatest.GivenWhenThen

class UserHistoryActorTest extends ShardingActorTest with GivenWhenThen with StrictLogging {

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
      expectMsg(Map(proposalId -> VoteAndQualifications(VoteKey.Agree, Seq.empty)))
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
      expectMsg(Map(proposalId -> VoteAndQualifications(VoteKey.Agree, Seq.empty)))
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
      expectMsg(Map(proposalId -> VoteAndQualifications(VoteKey.Agree, Seq(QualificationKey.LikeIt))))
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
      expectMsg(
        Map(
          proposalId -> VoteAndQualifications(
            VoteKey.Agree,
            Seq(QualificationKey.LikeIt, QualificationKey.PlatitudeAgree)
          )
        )
      )
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
      expectMsg(
        Map(
          proposalId -> VoteAndQualifications(
            VoteKey.Agree,
            Seq(QualificationKey.Doable, QualificationKey.LikeIt, QualificationKey.PlatitudeAgree)
          )
        )
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
      expectMsg(
        Map(
          proposalId -> VoteAndQualifications(
            VoteKey.Agree,
            Seq(QualificationKey.Doable, QualificationKey.PlatitudeAgree)
          )
        )
      )
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
      expectMsg(
        Map(
          proposalId1 -> VoteAndQualifications(VoteKey.Agree, Seq.empty),
          proposalId2 -> VoteAndQualifications(VoteKey.Disagree, Seq.empty),
          proposalId3 -> VoteAndQualifications(VoteKey.Neutral, Seq.empty)
        )
      )
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
      expectMsg(
        Map(
          proposalId1 -> VoteAndQualifications(VoteKey.Agree, Seq(QualificationKey.Doable)),
          proposalId2 -> VoteAndQualifications(
            VoteKey.Disagree,
            Seq(QualificationKey.Impossible, QualificationKey.NoWay)
          ),
          proposalId3 -> VoteAndQualifications(VoteKey.Neutral, Seq.empty)
        )
      )
    }
  }

  feature("lock set on proposal") {
    scenario("start moderation and continue to lock") {
      coordinator ! LogLockProposalEvent(
        UserId("Mod"),
        Some("Mod"),
        RequestContext.empty,
        UserAction(DateHelper.now(), "lock", ProposalLocked(ProposalId("1234"), UserId("1234")))
      )

      coordinator ! GetUserHistory(UserId("Mod"))
      val history1 = expectMsgType[UserHistory]
      history1.events.length should be(1)

      coordinator ! LogLockProposalEvent(
        UserId("Mod"),
        Some("Mod"),
        RequestContext.empty,
        UserAction(DateHelper.now(), "lock", ProposalLocked(ProposalId("1234"), UserId("1234")))
      )

      coordinator ! GetUserHistory(UserId("Mod"))
      val history2 = expectMsgType[UserHistory]
      history2.events.length should be(1)
    }
  }
}
