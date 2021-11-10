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

package org.make.api.proposal

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.eventstream.EventStream.Publish
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Scheduler}
import com.typesafe.config.ConfigFactory
import org.make.api.proposal.PublishedProposalEvent.{ProposalAuthorInfo, ProposalProposed}
import org.make.api.{KafkaTestConsumerBehavior, MakeKafkaTest}
import org.make.core.proposal.ProposalId
import org.make.core.user.UserId
import org.make.core.{AvroSerializers, DateHelper, RequestContext}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ProposalProducerActorIT
    extends ScalaTestWithActorTestKit(ProposalProducerActorIT.actorSystem)
    with MakeKafkaTest
    with AvroSerializers {

  implicit val scheduler: Scheduler = testKit.system.scheduler

  Feature("Proposal producer") {
    Scenario("send wrapped event into kafka") {
      Given("a producer on the proposal topic and a consumer on the same topic")
      testKit.spawn(ProposalKafkaProducerBehavior(), ProposalKafkaProducerBehavior.name)

      val probe = testKit.createTestProbe[ProposalEventWrapper]()

      val consumer = testKit.spawn(
        KafkaTestConsumerBehavior(ProposalKafkaProducerBehavior.topicKey, getClass.getSimpleName, probe.ref)
      )

      // Wait for actors to init
      Await.result(KafkaTestConsumerBehavior.waitUntilReady(consumer), atMost = 2.minutes)
      logger.info("Consumer is ready")

      When("I send a message to the producer")
      testKit.system.eventStream ! Publish(
        ProposalProposed(
          id = ProposalId("123456789"),
          slug = "ma-raison-de-proposer",
          requestContext = RequestContext.empty,
          author = ProposalAuthorInfo(userId = UserId("123987"), firstName = Some("Julien"), None, None),
          userId = UserId("123987"),
          eventDate = DateHelper.now(),
          content = "Ma raison de proposer",
          operation = None,
          theme = None,
          eventId = None
        )
      )

      Then("I should receive the wrapped version of it in the consumer")
      val wrapped = probe.expectMessageType[ProposalEventWrapper](2.minutes)
      wrapped.id should be("123456789")
      wrapped.eventType should be("ProposalProposed")
    }
  }

  override def afterAll(): Unit = {
    testKit.system.terminate()
    Await.result(testKit.system.whenTerminated, atMost = 10.seconds)
    super.afterAll()
  }
}

object ProposalProducerActorIT {
  // This configuration cannot be dynamic, port values _must_ match reality
  val configuration: String =
    """
      |akka.log-dead-letters-during-shutdown = off
      |make-api {
      |  kafka {
      |    connection-string = "127.0.0.1:29092"
      |    poll-timeout = 1000
      |    schema-registry = "http://localhost:28081"
      |    topics {
      |      users = "users"
      |      emails = "emails"
      |      proposals = "proposals"
      |      mailjet-events = "mailjet-events"
      |      duplicates-predicted = "duplicates-predicted"
      |      sequences = "sequences"
      |      tracking-events = "tracking-events"
      |      ideas = "ideas"
      |      predictions = "predictions"
      |    }
      |    dispatcher {
      |      type = Dispatcher
      |      executor = "thread-pool-executor"
      |      thread-pool-executor {
      |        fixed-pool-size = 32
      |      }
      |      throughput = 1
      |    }
      |  }
      |}
    """.stripMargin

  val actorSystem: ActorSystem[Nothing] =
    ActorSystem[Nothing](Behaviors.empty, "ProposalProducerIT", ConfigFactory.parseString(configuration))

}
