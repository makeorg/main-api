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

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.{ask, AskTimeoutException}
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util
import com.typesafe.config.{Config, ConfigFactory}
import enumeratum.values.scalacheck._
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical.{DefaultIdGeneratorComponent, TimeSettings}
import org.make.api.userhistory.UserHistoryActor.{RequestUserVotedProposals, RequestVoteValues, UserVotedProposals}
import org.make.api.{ActorSystemComponent, ShardingActorTest, TestHelper}
import org.make.core.history.HistoryActions
import org.make.core.history.HistoryActions.VoteTrust
import org.make.core.history.HistoryActions.VoteTrust.Trusted
import org.make.core.proposal.{ProposalId, VoteKey}
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext}
import org.scalacheck.rng.Seed
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class UserHistoryCoordinatorTest
    extends ShardingActorTest(UserHistoryCoordinatorTest.actorSystem)
    with ScalaFutures
    with ScalaCheckDrivenPropertyChecks
    with ImplicitSender
    with DefaultUserHistoryCoordinatorServiceComponent
    with MakeSettingsComponent
    with ActorSystemComponent
    with UserHistoryCoordinatorComponent
    with DefaultIdGeneratorComponent {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  override val makeSettings: MakeSettings = mock[MakeSettings]
  when(makeSettings.maxHistoryProposalsPerPage).thenReturn(100)

  override implicit val actorSystem: ActorSystem = system
  actorSystem.actorOf(UserHistoryCoordinator.props, UserHistoryCoordinator.name)
  override val userHistoryCoordinator: ActorRef =
    UserHistoryCoordinatorTest.actorSystemSeed.actorOf(UserHistoryCoordinator.props, UserHistoryCoordinator.name)

  private implicit val arbVotes: Arbitrary[Seq[UserVote]] = Arbitrary {
    val userVoteGen = for {
      proposalId <- Gen.resultOf[Unit, ProposalId](_ => idGenerator.nextProposalId())
      voteKey    <- arbitrary[VoteKey]
      voteTrust  <- arbitrary[VoteTrust]
    } yield UserVote(proposalId, voteKey, voteTrust)
    Gen.listOf(userVoteGen)
  }

  private implicit val arbUserId: Arbitrary[UserId] = Arbitrary(
    Gen.resultOf[Unit, UserId](_ => idGenerator.nextUserId())
  )

  Feature("get all votes") {
    Scenario("arbitrary votes") {
      forAll { (userVotes: Seq[UserVote], userId: UserId) =>
        val futureHasVoted = Source(userVotes)
          .mapAsync(5) { userVote =>
            userHistoryCoordinatorService.logTransactionalHistory(
              LogUserVoteEvent(
                userId,
                RequestContext.empty,
                UserAction[UserVote](date = DateHelper.now(), actionType = "vote", arguments = userVote)
              )
            )
          }
          .runWith(Sink.ignore)
        whenReady(futureHasVoted, Timeout(3.seconds))(_ => ())

        val votesTrusted = userVotes.collect { case userVote if userVote.trust.isTrusted => userVote.proposalId }.toSet

        val allVotes: Future[Seq[ProposalId]] = userHistoryCoordinatorService.retrieveVotedProposals(
          RequestUserVotedProposals(userId = userId, proposalsIds = Some(userVotes.map(_.proposalId)))
        )
        whenReady(allVotes, Timeout(3.seconds)) { proposalsIds =>
          proposalsIds.toSet shouldBe votesTrusted
        }

        val allVotesAndQualification: Future[Map[ProposalId, HistoryActions.VoteAndQualifications]] =
          userHistoryCoordinatorService.retrieveVoteAndQualifications(
            RequestVoteValues(userId = userId, proposalIds = userVotes.map(_.proposalId))
          )
        whenReady(allVotesAndQualification, Timeout(3.seconds)) { proposalsAndVotes =>
          proposalsAndVotes.keySet shouldBe userVotes.map(_.proposalId).toSet
        }
      }
    }

    Scenario("too large votes") {
      val userVotes: Seq[UserVote] = {
        val userVoteGen = arbitrary[VoteKey].map(UserVote(idGenerator.nextProposalId(), _, Trusted))
        Gen.listOfN(3195, userVoteGen).pureApply(Gen.Parameters.default, Seed.random())
      }
      val userId = idGenerator.nextUserId()

      val futureHasVoted = Source(userVotes)
        .mapAsync(5) { userVote =>
          userHistoryCoordinatorService.logTransactionalHistory(
            LogUserVoteEvent(
              userId,
              RequestContext.empty,
              UserAction[UserVote](date = DateHelper.now(), actionType = "vote", arguments = userVote)
            )
          )
        }
        .runWith(Sink.ignore)
      whenReady(futureHasVoted, Timeout(30.seconds))(_ => ())

      def allVotes(id: UserId, ids: Seq[ProposalId]): Future[Seq[ProposalId]] = {
        userHistoryCoordinatorService.retrieveVotedProposals(
          RequestUserVotedProposals(userId = id, proposalsIds = Some(ids))
        )
      }
      whenReady(allVotes(userId, userVotes.map(_.proposalId)), Timeout(30.seconds)) { proposalsIds =>
        proposalsIds.toSet shouldBe userVotes.map(_.proposalId).toSet
      }

      val requestVotesNotPaginated =
        RequestUserVotedProposals(userId = userId, proposalsIds = Some(userVotes.map(_.proposalId)))
      implicit val timeout: util.Timeout = TimeSettings.defaultTimeout
      val failedAllVotes =
        (userHistoryCoordinator ? requestVotesNotPaginated).mapTo[UserVotedProposals].map(_.proposals)
      whenReady(failedAllVotes.failed, Timeout(6.seconds)) { exception =>
        logger.info("Previous thrown exception 'oversized payload' was expected.")
        exception shouldBe a[AskTimeoutException]
      }

      val allVotesAndQualification: Future[Map[ProposalId, HistoryActions.VoteAndQualifications]] =
        userHistoryCoordinatorService.retrieveVoteAndQualifications(
          RequestVoteValues(userId = userId, proposalIds = userVotes.map(_.proposalId))
        )
      whenReady(allVotesAndQualification.failed, Timeout(6.seconds)) { exception =>
        logger.info("Previous thrown exception 'oversized payload' was expected.")
        exception shouldBe a[AskTimeoutException]
      }

    }
  }
}

object UserHistoryCoordinatorTest {
  val customConfiguration: String =
    """
      |akka.remote.artery.advanced.maximum-frame-size = 128000b
      |akka.actor.serialize-messages = on
      |akka-kryo-serialization.post-serialization-transformations = "off"
    """.stripMargin

  val conf: Config =
    ConfigFactory
      .parseString(customConfiguration)
      .withFallback(TestHelper.fullConfiguration)
      .resolve()

  val actorSystem: ActorSystem = TestHelper.defaultActorSystem(conf)

  val customSeedConfiguration: String =
    s"""
      |akka.cluster.roles = ["seed"]
      |akka.remote.artery.canonical.port = ${TestHelper.counter.getAndIncrement()}
      |""".stripMargin

  val configSeed: Config =
    ConfigFactory
      .parseString(customSeedConfiguration)
      .withFallback(actorSystem.settings.config)
      .resolve()

  val actorSystemSeed: ActorSystem = TestHelper.defaultActorSystem(configSeed)
}
