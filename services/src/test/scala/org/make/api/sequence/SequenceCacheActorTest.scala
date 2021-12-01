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

package org.make.api.sequence

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import cats.data.{NonEmptyList => Nel}
import com.typesafe.config.{Config, ConfigFactory}
import org.make.api.sequence.SequenceCacheActor.{Expire, GetProposal}
import org.make.api.technical.sequence.{SequenceCacheConfiguration, SequenceCacheConfigurationComponent}
import org.make.api.{MakeUnitTest, TestUtils}
import org.make.core.proposal.ProposalId
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.question.QuestionId

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SequenceCacheActorTest
    extends ScalaTestWithActorTestKit
    with MakeUnitTest
    with SequenceCacheConfigurationComponent
    with SequenceServiceComponent
    with SequenceConfigurationComponent {

  override val sequenceCacheConfiguration: SequenceCacheConfiguration =
    new SequenceCacheConfiguration(SequenceCacheActorTest.configuration.getConfig("make-api.sequence-cache"))
  override val sequenceService: SequenceService = mock[SequenceService]
  override val sequenceConfigurationService: SequenceConfigurationService = mock[SequenceConfigurationService]

  val questionId: QuestionId = QuestionId("question-id")

  val counter: AtomicInteger = new AtomicInteger(0)
  def reloadProposals: QuestionId => Future[Nel[IndexedProposal]] = { _ =>
    Future {
      Thread.sleep(50)
      val batch = counter.getAndIncrement()
      Nel(
        TestUtils.indexedProposal(id = ProposalId(s"batch-$batch-id-1")),
        List(
          TestUtils.indexedProposal(id = ProposalId(s"batch-$batch-id-2")),
          TestUtils.indexedProposal(id = ProposalId(s"batch-$batch-id-3"))
        )
      )
    }
  }

  Feature("cache") {
    Scenario("cache lifecycle") {
      val probe: TestProbe[IndexedProposal] = testKit.createTestProbe[IndexedProposal]()
      Given("a cache actor")
      val cache: ActorRef[SequenceCacheActor.Protocol] =
        testKit.spawn(
          SequenceCacheActor(questionId, reloadProposals, sequenceCacheConfiguration),
          SequenceCacheActor.name(questionId)
        )

      When("the cache is initializing whilst the user gets a proposal")
      cache ! GetProposal(probe.ref)
      Then("it should block until the cache is initialized")
      probe.expectNoMessage(49.millis)
      probe.expectMessageType[IndexedProposal].id shouldBe ProposalId("batch-0-id-1")

      When("the user gets some more proposals")
      Then("the cache cycles through its proposals")
      cache ! GetProposal(probe.ref)
      probe.expectMessageType[IndexedProposal].id shouldBe ProposalId("batch-0-id-2")
      cache ! GetProposal(probe.ref)
      probe.expectMessageType[IndexedProposal].id shouldBe ProposalId("batch-0-id-3")
      cache ! GetProposal(probe.ref)
      probe.expectMessageType[IndexedProposal].id shouldBe ProposalId("batch-0-id-1")
      cache ! GetProposal(probe.ref)
      probe.expectMessageType[IndexedProposal].id shouldBe ProposalId("batch-0-id-2")
      cache ! GetProposal(probe.ref)
      probe.expectMessageType[IndexedProposal].id shouldBe ProposalId("batch-0-id-3")

      When("the user gets some proposals after cache finished its cycles")
      Then("the cache should refresh and not block")
      cache ! GetProposal(probe.ref)
      probe.expectMessageType[IndexedProposal].id shouldBe ProposalId("batch-0-id-1")
      cache ! GetProposal(probe.ref)
      probe.expectMessageType[IndexedProposal].id shouldBe ProposalId("batch-0-id-2")
      And("once the cache is refreshed and the user gets a proposal")
      Thread.sleep(100)
      cache ! GetProposal(probe.ref)
      Then("the user gets the first of the new proposals")
      probe.expectMessageType[IndexedProposal].id shouldBe ProposalId("batch-1-id-1")

      When("the cache should not invalidate")
      Then("it keeps on running")
      cache ! Expire
      cache ! GetProposal(probe.ref)
      probe.expectMessageType[IndexedProposal].id shouldBe ProposalId("batch-1-id-2")

      When("the deadline expires, the cache invalidates")
      Thread.sleep(512)
      cache ! Expire
      And("the actor terminates")
      probe.expectTerminated(cache)
    }
  }

}

object SequenceCacheActorTest {
  val configuration: Config =
    ConfigFactory.parseString(s"""
                                 |make-api.sequence-cache {
                                 |  inactivity-timeout = "500 milliseconds"
                                 |  check-inactivity-timer = "15 minutes"
                                 |  proposals-pool-size = 3
                                 |  cache-refresh-cycles = 2
                                 |}
                                 |""".stripMargin)
}
