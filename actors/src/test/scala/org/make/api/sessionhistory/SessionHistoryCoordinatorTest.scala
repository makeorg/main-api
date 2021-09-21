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

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.{Config, ConfigFactory}
import enumeratum.values.scalacheck._
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.sessionhistory.SessionHistoryCoordinatorTest.actorSystemSeed
import org.make.api.technical.{ActorSystemComponent, DefaultIdGeneratorComponent}
import org.make.api.userhistory.{UserHistoryActor, UserHistoryCommand}
import org.make.api.{ActorTest, MakeUnitTest, TestHelper}
import org.make.core.history.HistoryActions
import org.make.core.history.HistoryActions.VoteTrust
import org.make.core.proposal.{ProposalId, VoteKey}
import org.make.core.session.SessionId
import org.make.core.{DateHelper, RequestContext}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class SessionHistoryCoordinatorTest
    extends ActorTest(SessionHistoryCoordinatorTest.actorSystem)
    with MakeUnitTest
    with ScalaCheckDrivenPropertyChecks
    with DefaultSessionHistoryCoordinatorServiceComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorComponent
    with DefaultIdGeneratorComponent
    with ActorSystemComponent {

  val userHistoryCoordinatorProbe: ActorRef[UserHistoryCommand] = testKit.spawn(UserHistoryActor())

  override val makeSettings: MakeSettings = mock[MakeSettings]

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    SessionHistoryCoordinator(testKit.system, userHistoryCoordinatorProbe, idGenerator, makeSettings)
  }

  protected override def afterAll(): Unit = {
    ActorTestKit.shutdown(actorSystemSeed)
    testKit.shutdownTestKit()
    super.afterAll()
  }

  when(makeSettings.maxHistoryProposalsPerPage).thenReturn(100)
  when(makeSettings.maxUserHistoryEvents).thenReturn(10000)

  override val sessionHistoryCoordinator: ActorRef[SessionHistoryCommand] =
    SessionHistoryCoordinator(actorSystemSeed, userHistoryCoordinatorProbe, idGenerator, makeSettings)

  private implicit val arbVotes: Arbitrary[Seq[SessionVote]] = Arbitrary {
    val sessionVoteGen = for {
      proposalId <- Gen.resultOf[Unit, ProposalId](_ => idGenerator.nextProposalId())
      voteKey    <- arbitrary[VoteKey]
      voteTrust  <- arbitrary[VoteTrust]
    } yield SessionVote(proposalId, voteKey, voteTrust)
    Gen.listOf(sessionVoteGen)
  }

  private implicit val arbSessionId: Arbitrary[SessionId] = Arbitrary(
    Gen.resultOf[Unit, SessionId](_ => SessionId(idGenerator.nextId()))
  )

  Feature("get all votes") {
    Scenario("arbitrary votes") {
      forAll { (sessionVotes: Seq[SessionVote], sessionId: SessionId) =>
        val futureHasVoted = Source(sessionVotes)
          .mapAsync(5) { sessionVote =>
            sessionHistoryCoordinatorService.logTransactionalHistory(
              LogSessionVoteEvent(
                sessionId,
                RequestContext.empty,
                SessionAction[SessionVote](date = DateHelper.now(), actionType = "vote", arguments = sessionVote)
              )
            )
          }
          .runWith(Sink.ignore)
        whenReady(futureHasVoted, Timeout(3.seconds))(_ => ())

        val allVotes: Future[Seq[ProposalId]] = sessionHistoryCoordinatorService.retrieveVotedProposals(
          sessionId = sessionId,
          proposalsIds = Some(sessionVotes.map(_.proposalId))
        )
        whenReady(allVotes, Timeout(3.seconds)) { proposalsIds =>
          proposalsIds.toSet shouldBe sessionVotes.map(_.proposalId).toSet
        }

        val allVotesAndQualification: Future[Map[ProposalId, HistoryActions.VoteAndQualifications]] =
          sessionHistoryCoordinatorService.retrieveVoteAndQualifications(
            sessionId = sessionId,
            proposalIds = sessionVotes.map(_.proposalId)
          )
        whenReady(allVotesAndQualification, Timeout(3.seconds)) { proposalsAndVotes =>
          proposalsAndVotes.keySet shouldBe sessionVotes.map(_.proposalId).toSet
        }
      }
    }
  }
}

object SessionHistoryCoordinatorTest {
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

  val customSeedConfiguration: String =
    s"""
       |akka.cluster.roles = ["seed"]
       |akka.remote.artery.canonical.port = ${TestHelper.counter.getAndIncrement()}
    """.stripMargin

  val configSeed: Config =
    ConfigFactory
      .parseString(customSeedConfiguration)
      .withFallback(conf)
      .resolve()

  def actorSystem: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, "test_system", conf)
  val actorSystemSeed: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, "test_system", configSeed)
}
