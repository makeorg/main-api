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

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.{ask, AskTimeoutException}
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical.{DefaultIdGeneratorComponent, TimeSettings}
import org.make.api.{ActorSystemComponent, ShardingActorTest, TestHelper}
import org.make.core.history.HistoryActions
import org.make.core.history.HistoryActions.{Trusted, VoteTrust}
import org.make.core.proposal.{ProposalId, VoteKey}
import org.make.core.session.SessionId
import org.make.core.{DateHelper, RequestContext}
import org.mockito.Mockito.when
import org.scalacheck.rng.Seed
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class SessionHistoryCoordinatorTest
    extends ShardingActorTest(SessionHistoryCoordinatorTest.actorSystem)
    with MockitoSugar
    with ScalaFutures
    with StrictLogging
    with ScalaCheckDrivenPropertyChecks
    with ImplicitSender
    with DefaultSessionHistoryCoordinatorServiceComponent
    with MakeSettingsComponent
    with ActorSystemComponent
    with SessionHistoryCoordinatorComponent
    with DefaultIdGeneratorComponent {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  override val makeSettings: MakeSettings = mock[MakeSettings]
  when(makeSettings.maxHistoryProposalsPerPage).thenReturn(100)

  val userHistoryCoordinatorProbe: TestProbe = TestProbe()(system)

  override implicit val actorSystem: ActorSystem = system
  actorSystem.actorOf(
    SessionHistoryCoordinator.props(userHistoryCoordinatorProbe.ref, 10.milliseconds),
    SessionHistoryCoordinator.name
  )
  override val sessionHistoryCoordinator: ActorRef =
    SessionHistoryCoordinatorTest.actorSystemSeed
      .actorOf(
        SessionHistoryCoordinator.props(userHistoryCoordinatorProbe.ref, 10.milliseconds),
        SessionHistoryCoordinator.name
      )

  private implicit val arbVotes: Arbitrary[Seq[SessionVote]] = Arbitrary {
    val sessionVoteGen = for {
      proposalId <- Gen.resultOf[Unit, ProposalId](_ => idGenerator.nextProposalId())
      voteKey    <- Gen.oneOf(VoteKey.voteKeys.values.toSeq)
      voteTrust  <- Gen.oneOf(VoteTrust.trustValue.values.toSeq)
    } yield SessionVote(proposalId, voteKey, voteTrust)
    Gen.listOf(sessionVoteGen)
  }

  private implicit val arbSessionId: Arbitrary[SessionId] = Arbitrary(
    Gen.resultOf[Unit, SessionId](_ => SessionId(idGenerator.nextId()))
  )

  feature("get all votes") {
    scenario("arbitrary votes") {
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
          RequestSessionVotedProposals(sessionId = sessionId, proposalsIds = Some(sessionVotes.map(_.proposalId)))
        )
        whenReady(allVotes, Timeout(3.seconds)) { proposalsIds =>
          proposalsIds.toSet shouldBe sessionVotes.map(_.proposalId).toSet
        }

        val allVotesAndQualification: Future[Map[ProposalId, HistoryActions.VoteAndQualifications]] =
          sessionHistoryCoordinatorService.retrieveVoteAndQualifications(
            RequestSessionVoteValues(sessionId = sessionId, proposalIds = sessionVotes.map(_.proposalId))
          )
        whenReady(allVotesAndQualification, Timeout(3.seconds)) { proposalsAndVotes =>
          proposalsAndVotes.keySet shouldBe sessionVotes.map(_.proposalId).toSet
        }
      }
    }

    scenario("too large votes") {
      val sessionVotes: Seq[SessionVote] = {
        val sessionVoteGen =
          Gen.oneOf(VoteKey.voteKeys.values.toSeq).map(SessionVote(idGenerator.nextProposalId(), _, Trusted))
        Gen.listOfN(3195, sessionVoteGen).pureApply(Gen.Parameters.default, Seed.random())
      }
      val sessionId: SessionId = SessionId(idGenerator.nextId())

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
      whenReady(futureHasVoted, Timeout(30.seconds))(_ => ())

      def allVotes(id: SessionId, ids: Seq[ProposalId]): Future[Seq[ProposalId]] = {
        sessionHistoryCoordinatorService.retrieveVotedProposals(
          RequestSessionVotedProposals(sessionId = id, proposalsIds = Some(ids))
        )
      }
      whenReady(allVotes(sessionId, sessionVotes.map(_.proposalId)), Timeout(30.seconds)) { proposalsIds =>
        proposalsIds.toSet shouldBe sessionVotes.map(_.proposalId).toSet
      }

      val requestVotesNotPaginated =
        RequestSessionVotedProposals(sessionId = sessionId, proposalsIds = Some(sessionVotes.map(_.proposalId)))
      implicit val timeout: util.Timeout = TimeSettings.defaultTimeout
      val failedAllVotes = (sessionHistoryCoordinator ? requestVotesNotPaginated).mapTo[Seq[ProposalId]]
      whenReady(failedAllVotes.failed, Timeout(6.seconds)) { exception =>
        logger.info("Previous thrown exception 'oversized payload' was expected.")
        exception shouldBe a[AskTimeoutException]
      }

      val allVotesAndQualification: Future[Map[ProposalId, HistoryActions.VoteAndQualifications]] =
        sessionHistoryCoordinatorService.retrieveVoteAndQualifications(
          RequestSessionVoteValues(sessionId = sessionId, proposalIds = sessionVotes.map(_.proposalId))
        )
      whenReady(allVotesAndQualification.failed, Timeout(6.seconds)) { exception =>
        logger.info("Previous thrown exception 'oversized payload' was expected.")
        exception shouldBe a[AskTimeoutException]
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
