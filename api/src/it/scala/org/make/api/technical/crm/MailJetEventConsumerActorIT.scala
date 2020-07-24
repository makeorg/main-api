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

import java.time.ZonedDateTime

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import com.typesafe.config.ConfigFactory
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.{KafkaConsumerTest, KafkaTestConsumerActor}
import org.make.core.user.MailingErrorLog
import org.make.core.{DateHelper, MakeSerializable}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class MailJetEventConsumerActorIT
    extends TestKit(MailJetEventConsumerActorIT.actorSystem)
    with KafkaConsumerTest[MailJetEventWrapper]
    with ImplicitSender
    with UserServiceComponent {

// If wou want to change ports and names to avoid collisions, just override them
  override val kafkaName: String = "kafkamailjeteventconsumer"
  override val kafkaExposedPort: Int = 29092
  override val registryName: String = "registrymailjeteventconsumer"
  override val registryExposedPort: Int = 28082
  override val zookeeperName: String = "zookeepermailjeteventconsumer"
  override val zookeeperExposedPort: Int = 22183
  override val userService: UserService = mock[UserService]

  override val format: RecordFormat[MailJetEventWrapper] = MailJetEventWrapper.recordFormat
  override val schema: SchemaFor[MailJetEventWrapper] = MailJetEventWrapper.schemaFor

  override val topic: String = "mailjet-events"

  val consumer: ActorRef =
    system.actorOf(MailJetEventConsumerActor.props(userService = userService), "MailJetBounceEvent")

  override def beforeAll(): Unit = {
    super.beforeAll()
    Await.result(KafkaTestConsumerActor.waitUntilReady(consumer), atMost = 2.minutes)
  }

  override def afterAll(): Unit = {
    consumer ! PoisonPill
    super.afterAll()
  }

  Feature("consume MailJet event") {
    val probe = TestProbe()
    val now: ZonedDateTime = DateHelper.now()

    Scenario("Reacting to MailJetBounceEvent") {
      when(userService.updateIsHardBounce(eqTo("test@example.com"), eqTo(true))).thenAnswer { (_: String, _: Boolean) =>
        probe.ref ! "userService.updateIsHardBounce called"
        Future.successful(true)
      }
      when(
        userService.updateLastMailingError(
          eqTo("test@example.com"),
          eqTo[Option[MailingErrorLog]](Some(MailingErrorLog(error = MailJetError.InvalidDomaine.name, date = now)))
        )
      ).thenAnswer { (_: String, _: Option[MailingErrorLog]) =>
        probe.ref ! "userService.updateLastMailingError called"
        Future.successful(true)
      }
      Given("a bounce event to consume")
      val eventBounce: MailJetBounceEvent = MailJetBounceEvent(
        email = "test@example.com",
        time = None,
        messageId = None,
        campaignId = None,
        contactId = None,
        customCampaign = None,
        customId = None,
        payload = None,
        blocked = false,
        hardBounce = true,
        error = Some(MailJetError.InvalidDomaine)
      )
      val wrappedBounceEventBounce =
        MailJetEventWrapper(version = MakeSerializable.V1, id = "some-event", date = now, event = eventBounce)

      When("I send bounce event")
      send(wrappedBounceEventBounce)

      Then("message is consumed and userService is called to update data")
      probe.expectMsg(500.millis, "userService.updateIsHardBounce called")
      probe.expectMsg(500.millis, "userService.updateLastMailingError called")
    }

    Scenario("Reacting to MailJetSpamEvent") {
      when(userService.updateOptInNewsletter(eqTo("test@example.com"), eqTo(false))).thenAnswer {
        (_: String, _: Boolean) =>
          probe.ref ! "userService.updateOptInNewsletter called"
          Future.successful(true)
      }
      Given("A spam event to consume")
      val eventSpam: MailJetSpamEvent = MailJetSpamEvent(
        email = "test@example.com",
        time = None,
        messageId = None,
        campaignId = None,
        contactId = None,
        customCampaign = None,
        customId = None,
        payload = None,
        source = Some("test")
      )
      val wrappedSpamEventBounce =
        MailJetEventWrapper(
          version = MakeSerializable.V1,
          id = "some-event",
          date = DateHelper.now(),
          event = eventSpam
        )

      When("I send spam event")
      send(wrappedSpamEventBounce)

      Then("Message is consumed and userService is called to update data")
      probe.expectMsg(500.millis, "userService.updateOptInNewsletter called")
    }

    Scenario("Reacting to MailJetUnsubscribeEvent") {
      when(
        userService
          .updateOptInNewsletter(eqTo("test_unsubscribe@example.com"), eqTo(false))
      ).thenAnswer { (_: String, _: Boolean) =>
        probe.ref ! "userService.updateOptInNewsletter called for unsubscribe event"
        Future.successful(true)
      }

      Given("A unsubscribe event to consume")
      val eventUnsubscribe: MailJetUnsubscribeEvent = MailJetUnsubscribeEvent(
        email = "test_unsubscribe@example.com",
        time = None,
        messageId = None,
        campaignId = None,
        contactId = None,
        customCampaign = None,
        customId = None,
        payload = None,
        listId = None,
        ip = None,
        geo = None,
        agent = None
      )
      val wrappedUnsubscribeEventBounce = MailJetEventWrapper(
        version = MakeSerializable.V1,
        id = "some-event",
        date = DateHelper.now(),
        event = eventUnsubscribe
      )

      When("I send unsubscribe event")
      send(wrappedUnsubscribeEventBounce)

      Then("Message is consumed and userService is called to update data")
      probe.expectMsg(500.millis, "userService.updateOptInNewsletter called for unsubscribe event")
    }
  }
}

object MailJetEventConsumerActorIT {
  // This configuration cannot be dynamic, port values _must_ match reality
  val configuration: String =
    """
      |akka.log-dead-letters-during-shutdown = off
      |make-api {
      |  kafka {
      |    connection-string = "127.0.0.1:29092"
      |    poll-timeout = 1000
      |    schema-registry = "http://localhost:28082"
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
      |  }
      |}
    """.stripMargin

  val actorSystem = ActorSystem("MailJetEventConsumerActorIT", ConfigFactory.parseString(configuration))
}
