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

package org.make.api.technical.crm

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.eventstream.EventStream.Publish
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Scheduler}
import com.typesafe.config.ConfigFactory
import org.make.api.{KafkaTest, KafkaTestConsumerBehavior}
import org.make.core.AvroSerializers

import java.time.ZonedDateTime
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class MailJetEventProducerBehaviorIT
    extends ScalaTestWithActorTestKit(MailJetEventProducerBehaviorIT.actorSystem)
    with KafkaTest
    with AvroSerializers {

  implicit val scheduler: Scheduler = testKit.system.scheduler

  Feature("MailJetCallback producer") {
    Scenario("send wrapped event into kafka") {
      Given("a producer on the Mailjet event topic and a consumer on the same topic")
      testKit.spawn(MailJetEventProducerBehavior(), MailJetEventProducerBehavior.name)

      val probe = testKit.createTestProbe[MailJetEventWrapper]()

      val consumer = testKit.spawn(
        KafkaTestConsumerBehavior(MailJetEventProducerBehavior.topicKey, getClass.getSimpleName, probe.ref)
      )

      // Wait for actors to init
      Await.result(KafkaTestConsumerBehavior.waitUntilReady(consumer), atMost = 2.minutes)
      logger.info("Consumer is ready")

      val bounceEvent: MailJetBounceEvent = MailJetBounceEvent(
        email = "test@make.org",
        time = Some(1430812195L),
        messageId = Some(3),
        campaignId = Some(4),
        contactId = Some(5),
        customCampaign = Some("custom campaign"),
        customId = Some("custom id"),
        payload = Some("payload"),
        blocked = true,
        hardBounce = true,
        error = Some(MailJetError.DuplicateInCampaign)
      )
      When("I send a message to the producer")

      testKit.system.eventStream ! Publish(bounceEvent)

      Then("I should receive the wrapped version of it in the consumer")
      val wrapped = probe.expectMessageType[MailJetEventWrapper](2.minutes)

      wrapped.id shouldBe "test@make.org"
      wrapped.version > 0 shouldBe true
      wrapped.event shouldBe bounceEvent
      wrapped.date shouldBe ZonedDateTime.parse("2015-05-05T07:49:55Z")
    }
  }

  override def afterAll(): Unit = {
    testKit.system.terminate()
    Await.result(testKit.system.whenTerminated, atMost = 10.seconds)
    super.afterAll()
  }

}

object MailJetEventProducerBehaviorIT {
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
    ActorSystem[Nothing](Behaviors.empty, "MailJetCallbackProducerIT", ConfigFactory.parseString(configuration))
}
