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

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.{Config, ConfigFactory}
import enumeratum.values.scalacheck._
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.api.userhistory.UserHistoryCoordinatorTest.actorSystemSeed
import org.make.api.{ActorSystemTypedComponent, MakeUnitTest, TestHelper}
import org.make.core.history.HistoryActions
import org.make.core.history.HistoryActions.VoteTrust
import org.make.core.proposal.{ProposalId, VoteKey}
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

class UserHistoryCoordinatorTest
    extends ScalaTestWithActorTestKit(UserHistoryCoordinatorTest.actorSystem)
    with MakeUnitTest
    with ScalaCheckDrivenPropertyChecks
    with DefaultUserHistoryCoordinatorServiceComponent
    with MakeSettingsComponent
    with ActorSystemTypedComponent
    with UserHistoryCoordinatorComponent
    with DefaultIdGeneratorComponent {

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    UserHistoryCoordinator(testKit.system)
  }

  protected override def afterAll(): Unit = {
    ActorTestKit.shutdown(actorSystemSeed)
    testKit.shutdownTestKit()
    super.afterAll()
  }

  override val makeSettings: MakeSettings = mock[MakeSettings]
  when(makeSettings.maxHistoryProposalsPerPage).thenReturn(100)

  override implicit val actorSystemTyped: ActorSystem[Nothing] = testKit.system

  override val userHistoryCoordinator: ActorRef[UserHistoryCommand] = UserHistoryCoordinator(actorSystemSeed)

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
        whenReady(allVotes, Timeout(5.seconds)) { proposalsIds =>
          proposalsIds.toSet shouldBe votesTrusted
        }

        val allVotesAndQualification: Future[Map[ProposalId, HistoryActions.VoteAndQualifications]] =
          userHistoryCoordinatorService.retrieveVoteAndQualifications(
            userId = userId,
            proposalIds = userVotes.map(_.proposalId)
          )
        whenReady(allVotesAndQualification, Timeout(3.seconds)) { proposalsAndVotes =>
          proposalsAndVotes.keySet shouldBe userVotes.map(_.proposalId).toSet
        }
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
      |make-api.security.secure-hash-salt = "salt-secure"
      |make-api.security.secure-vote-salt = "vote-secure"
    """.stripMargin

  val conf: Config =
    ConfigFactory
      .parseString(customConfiguration)
      .withFallback(TestHelper.fullConfiguration)
      .resolve()

  val customSeedConfiguration: String =
    s"""
       |akka.cluster.roles = ["seed"]
       |akka.remote.artery.canonical.port = ${TestHelper.counter.getAndIncrement()}
       |""".stripMargin

  val configSeed: Config =
    ConfigFactory
      .parseString(customSeedConfiguration)
      .withFallback(conf)
      .resolve()

  def actorSystem: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, "test_system", conf)
  val actorSystemSeed: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, "test_system", configSeed)
}
