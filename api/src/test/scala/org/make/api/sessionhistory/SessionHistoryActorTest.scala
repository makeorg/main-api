package org.make.api.sessionhistory

import java.time.temporal.ChronoUnit

import akka.actor.ActorRef
import akka.testkit.TestProbe
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ShardingActorTest
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.session.SessionId
import org.make.core.{DateHelper, RequestContext}
import org.scalatest.GivenWhenThen

class SessionHistoryActorTest extends ShardingActorTest with GivenWhenThen with StrictLogging {

  val userCoordinator: ActorRef = TestProbe()(system).ref

  val coordinator: ActorRef =
    system.actorOf(SessionHistoryCoordinator.props(userCoordinator), SessionHistoryCoordinator.name)

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
      expectMsgType[LogSessionVoteEvent]

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      expectMsg(Map(proposalId -> VoteAndQualifications(VoteKey.Agree, Seq.empty)))
    }

    scenario("vote then unvote proposal") {
      val sessionId = SessionId("vote-then-unvote-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(10, ChronoUnit.SECONDS), "vote", SessionVote(proposalId, VoteKey.Agree))
      )
      expectMsgType[LogSessionVoteEvent]

      coordinator ! LogSessionUnvoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now(), "unvote", SessionUnvote(proposalId, VoteKey.Agree))
      )
      expectMsgType[LogSessionUnvoteEvent]

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
      expectMsgType[LogSessionVoteEvent]

      coordinator ! LogSessionUnvoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "unvote",
          SessionUnvote(proposalId, VoteKey.Disagree)
        )
      )
      expectMsgType[LogSessionUnvoteEvent]

      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now(), "vote", SessionVote(proposalId, VoteKey.Agree))
      )
      expectMsgType[LogSessionVoteEvent]

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      expectMsg(Map(proposalId -> VoteAndQualifications(VoteKey.Agree, Seq.empty)))
    }

    scenario("vote then one qualification proposal") {
      val sessionId = SessionId("vote-then-one-qualification-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(10, ChronoUnit.SECONDS), "vote", SessionVote(proposalId, VoteKey.Agree))
      )
      expectMsgType[LogSessionVoteEvent]

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now(), "qualification", SessionQualification(proposalId, QualificationKey.LikeIt))
      )
      expectMsgType[LogSessionQualificationEvent]

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
      expectMsg(Map(proposalId -> VoteAndQualifications(VoteKey.Agree, Seq(QualificationKey.LikeIt))))
    }

    scenario("vote then two qualifications proposal") {
      val sessionId = SessionId("vote-then-two-qualification-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(10, ChronoUnit.SECONDS), "vote", SessionVote(proposalId, VoteKey.Agree))
      )
      expectMsgType[LogSessionVoteEvent]

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.LikeIt)
        )
      )
      expectMsgType[LogSessionQualificationEvent]

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now(),
          "qualification",
          SessionQualification(proposalId, QualificationKey.PlatitudeAgree)
        )
      )
      expectMsgType[LogSessionQualificationEvent]

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
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
      val sessionId = SessionId("vote-then-three-qualification-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(15, ChronoUnit.SECONDS), "vote", SessionVote(proposalId, VoteKey.Agree))
      )
      expectMsgType[LogSessionVoteEvent]

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.LikeIt)
        )
      )
      expectMsgType[LogSessionQualificationEvent]

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.Doable)
        )
      )
      expectMsgType[LogSessionQualificationEvent]

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now(),
          "qualification",
          SessionQualification(proposalId, QualificationKey.PlatitudeAgree)
        )
      )
      expectMsgType[LogSessionQualificationEvent]

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
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
      val sessionId = SessionId("vote-then-qualif-then-unqualif-then-requalif-proposal")
      val proposalId = ProposalId("proposal1")
      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(15, ChronoUnit.SECONDS), "vote", SessionVote(proposalId, VoteKey.Agree))
      )
      expectMsgType[LogSessionVoteEvent]

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(12, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.LikeIt)
        )
      )
      expectMsgType[LogSessionQualificationEvent]

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.Doable)
        )
      )
      expectMsgType[LogSessionQualificationEvent]

      coordinator ! LogSessionUnqualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(8, ChronoUnit.SECONDS),
          "unqualification",
          SessionUnqualification(proposalId, QualificationKey.LikeIt)
        )
      )
      expectMsgType[LogSessionUnqualificationEvent]

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(5, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId, QualificationKey.PlatitudeAgree)
        )
      )
      expectMsgType[LogSessionQualificationEvent]

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId))
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
      val sessionId = SessionId("vote-on-many-proposal")
      val proposalId1 = ProposalId("proposal1")
      val proposalId2 = ProposalId("proposal2")
      val proposalId3 = ProposalId("proposal3")
      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(15, ChronoUnit.SECONDS), "vote", SessionVote(proposalId1, VoteKey.Agree))
      )
      expectMsgType[LogSessionVoteEvent]

      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId2, VoteKey.Disagree)
        )
      )
      expectMsgType[LogSessionVoteEvent]

      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(5, ChronoUnit.SECONDS), "vote", SessionVote(proposalId3, VoteKey.Neutral))
      )
      expectMsgType[LogSessionVoteEvent]

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId1, proposalId2, proposalId3))
      expectMsg(
        Map(
          proposalId1 -> VoteAndQualifications(VoteKey.Agree, Seq.empty),
          proposalId2 -> VoteAndQualifications(VoteKey.Disagree, Seq.empty),
          proposalId3 -> VoteAndQualifications(VoteKey.Neutral, Seq.empty)
        )
      )
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
      expectMsgType[LogSessionVoteEvent]

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(14, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId1, QualificationKey.Doable)
        )
      )
      expectMsgType[LogSessionQualificationEvent]

      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(10, ChronoUnit.SECONDS),
          "vote",
          SessionVote(proposalId2, VoteKey.Disagree)
        )
      )
      expectMsgType[LogSessionVoteEvent]

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(9, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId2, QualificationKey.Impossible)
        )
      )
      expectMsgType[LogSessionQualificationEvent]

      coordinator ! LogSessionQualificationEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(
          DateHelper.now().minus(8, ChronoUnit.SECONDS),
          "qualification",
          SessionQualification(proposalId2, QualificationKey.NoWay)
        )
      )
      expectMsgType[LogSessionQualificationEvent]

      coordinator ! LogSessionVoteEvent(
        sessionId,
        RequestContext.empty,
        SessionAction(DateHelper.now().minus(5, ChronoUnit.SECONDS), "vote", SessionVote(proposalId3, VoteKey.Neutral))
      )
      expectMsgType[LogSessionVoteEvent]

      coordinator ! RequestSessionVoteValues(sessionId, Seq(proposalId1, proposalId2, proposalId3))
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

}
