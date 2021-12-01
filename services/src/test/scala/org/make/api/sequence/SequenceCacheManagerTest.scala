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

import akka.actor.testkit.typed.Effect._
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestInbox}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl._
import com.typesafe.config.{Config, ConfigFactory}
import org.make.api.{MakeUnitTest, TestUtils}
import org.make.api.sequence.SequenceCacheManager.{ChildTerminated, ExpireChildren, GetProposal}
import org.make.api.technical.sequence.{SequenceCacheConfiguration, SequenceCacheConfigurationComponent}
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.question.QuestionId

import scala.concurrent.Future

class SequenceCacheManagerTest
    extends MakeUnitTest
    with SequenceCacheConfigurationComponent
    with SequenceServiceComponent
    with SequenceConfigurationComponent {

  override val sequenceCacheConfiguration: SequenceCacheConfiguration =
    new SequenceCacheConfiguration(SequenceCacheManagerTest.configuration.getConfig("make-api.sequence-cache"))
  override val sequenceService: SequenceService = mock[SequenceService]
  override val sequenceConfigurationService: SequenceConfigurationService = mock[SequenceConfigurationService]

  val probe: TestInbox[IndexedProposal] = TestInbox[IndexedProposal]()

  val questionId1: QuestionId = QuestionId("question-id-1")
  val questionId2: QuestionId = QuestionId("question-id-2")

  val testKit: BehaviorTestKit[SequenceCacheManager.Protocol] = BehaviorTestKit(
    SequenceCacheManager(sequenceCacheConfiguration, sequenceService, sequenceConfigurationService)
  )
  val timer: TimerScheduled[SequenceCacheManager.ExpireChildren.type] =
    testKit.expectEffectType[TimerScheduled[ExpireChildren.type]]

  when(sequenceConfigurationService.getSequenceConfigurationByQuestionId(questionId1))
    .thenReturn(Future.successful(TestUtils.sequenceConfiguration(questionId1)))
  when(sequenceConfigurationService.getSequenceConfigurationByQuestionId(questionId2))
    .thenReturn(Future.successful(TestUtils.sequenceConfiguration(questionId2)))

  Feature("behave") {
    Scenario("spawn and expire children") {
      testKit.run(GetProposal(questionId1, probe.ref))
      testKit.expectEffectType[Spawned[SequenceCacheActor]]
      testKit.expectEffectType[WatchedWith[SequenceCacheActor, ChildTerminated]]
      val child1Inbox: TestInbox[SequenceCacheActor.Protocol] =
        testKit.childInbox[SequenceCacheActor.Protocol](questionId1.value)
      child1Inbox.expectMessage(SequenceCacheActor.GetProposal(probe.ref))

      testKit.run(GetProposal(questionId2, probe.ref))
      testKit.expectEffectType[Spawned[SequenceCacheActor]]
      testKit.expectEffectType[WatchedWith[SequenceCacheActor, ChildTerminated]]
      val child2Inbox: TestInbox[SequenceCacheActor.Protocol] =
        testKit.childInbox[SequenceCacheActor.Protocol](questionId2.value)
      child2Inbox.expectMessage(SequenceCacheActor.GetProposal(probe.ref))
      testKit.run(GetProposal(questionId2, probe.ref))
      child2Inbox.expectMessage(SequenceCacheActor.GetProposal(probe.ref))

      testKit.run(ExpireChildren)
      child1Inbox.expectMessage(SequenceCacheActor.Expire)
      child2Inbox.expectMessage(SequenceCacheActor.Expire)
    }
  }
}

object SequenceCacheManagerTest {
  val configuration: Config =
    ConfigFactory.parseString(s"""
       |make-api.sequence-cache {
       |  inactivity-timeout = "1 hour"
       |  check-inactivity-timer = "15 minutes"
       |  proposals-pool-size = 20
       |  cache-refresh-cycles = 3
       |}
       |""".stripMargin)

  val actorSystem: ActorSystem[Nothing] =
    ActorSystem[Nothing](Behaviors.empty, "SequenceCacheManagerTest", configuration)
}
