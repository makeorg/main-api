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

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.sksamuel.avro4s.{FromRecord, RecordFormat}
import com.typesafe.config.ConfigFactory
import org.make.api.technical.AvroSerializers
import org.make.api.technical.crm.PublishedCrmContactEvent._
import org.make.api.{KafkaTest, KafkaTestConsumerActor}
import org.make.core.DateHelper
import org.make.core.user.UserId

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class CrmContactProducerActorIT
    extends TestKit(CrmContactProducerActorIT.actorSystem)
    with KafkaTest
    with ImplicitSender
    with AvroSerializers {

  override val kafkaName: String = "kafkacrmcontacteventconsumer"
  override val registryName: String = "registrycrmcontacteventconsumer"
  override val zookeeperName: String = "zookeepercrmcontacteventconsumer"
  override val kafkaExposedPort: Int = 29096
  override val registryExposedPort: Int = 28086
  override val zookeeperExposedPort: Int = 22186

  feature("CRM contact producer") {
    scenario("send wrapped event into kafka") {
      Given("a producer on the crm contact event topic and a consumer on the same topic")
      val actorSystem = system
      val producer: ActorRef =
        actorSystem.actorOf(CrmContactProducerActor.props, CrmContactProducerActor.name)

      val probe = TestProbe()
      implicit val fromRecord: FromRecord[CrmContactEventWrapper] = FromRecord[CrmContactEventWrapper]
      val consumer = {
        val (name: String, props: Props) =
          KafkaTestConsumerActor.propsAndName(
            RecordFormat[CrmContactEventWrapper],
            CrmContactProducerActor.topicKey,
            probe.ref
          )
        actorSystem.actorOf(props, name)
      }

      // Wait for actors to init
      Await.result(KafkaTestConsumerActor.waitUntilReady(consumer), atMost = 2.minutes)
      logger.info("Consumer is ready")
      KafkaTestConsumerActor.waitUntilReady(producer)
      logger.info("Producer is ready")

      val now: ZonedDateTime = DateHelper.now

      When("I send a new contact message to the producer")
      And("I send a hard bounce contact message to the producer")
      And("I send a unsubscribe contact message to the producer")
      And("I send a subscribe contact message to the producer")
      And("I send a crm contact user properties update message to the producer")
      Then("I should receive the wrapped version in the consumer")

      val newContactEvent: CrmContactNew = CrmContactNew(id = UserId("john-doe"), eventDate = now)
      actorSystem.eventStream.publish(newContactEvent)

      val wrapped = probe.expectMsgType[CrmContactEventWrapper](2.minutes)
      wrapped.id shouldBe ("john-doe")
      wrapped.version > 0 shouldBe (true)
      wrapped.event.fold(ToCrmContactEvent) shouldBe (newContactEvent)
      wrapped.date.getDayOfMonth shouldBe (now.getDayOfMonth)
      wrapped.date.getMonth shouldBe (now.getMonth)
      wrapped.date.getYear shouldBe (now.getYear)

      val hardBounceContactEvent: CrmContactHardBounce =
        CrmContactHardBounce(id = UserId("john-doe2"), eventDate = now)
      actorSystem.eventStream.publish(hardBounceContactEvent)

      val wrappedHardBounce = probe.expectMsgType[CrmContactEventWrapper](2.minutes)
      actorSystem.eventStream.publish(wrappedHardBounce)
      wrappedHardBounce.id shouldBe ("john-doe2")
      wrappedHardBounce.version > 0 shouldBe (true)
      wrappedHardBounce.event.fold(ToCrmContactEvent) shouldBe (hardBounceContactEvent)
      wrappedHardBounce.date.getDayOfMonth shouldBe (now.getDayOfMonth)
      wrappedHardBounce.date.getMonth shouldBe (now.getMonth)
      wrappedHardBounce.date.getYear shouldBe (now.getYear)

      val unsubscribeContactEvent: CrmContactUnsubscribe =
        CrmContactUnsubscribe(id = UserId("john-doe3"), eventDate = now)
      actorSystem.eventStream.publish(unsubscribeContactEvent)

      val wrappedUnsubscribe = probe.expectMsgType[CrmContactEventWrapper](2.minutes)
      actorSystem.eventStream.publish(wrappedUnsubscribe)
      wrappedUnsubscribe.id shouldBe ("john-doe3")
      wrappedUnsubscribe.version > 0 shouldBe (true)
      wrappedUnsubscribe.event.fold(ToCrmContactEvent) shouldBe (unsubscribeContactEvent)
      wrappedUnsubscribe.date.getDayOfMonth shouldBe (now.getDayOfMonth)
      wrappedUnsubscribe.date.getMonth shouldBe (now.getMonth)
      wrappedUnsubscribe.date.getYear shouldBe (now.getYear)

      val subscribeContactEvent: CrmContactSubscribe = CrmContactSubscribe(id = UserId("john-doe4"), eventDate = now)
      actorSystem.eventStream.publish(subscribeContactEvent)

      val wrappedSubscribe = probe.expectMsgType[CrmContactEventWrapper](2.minutes)
      actorSystem.eventStream.publish(wrappedSubscribe)
      wrappedSubscribe.id shouldBe ("john-doe4")
      wrappedSubscribe.version > 0 shouldBe (true)
      wrappedSubscribe.event.fold(ToCrmContactEvent) shouldBe (subscribeContactEvent)
      wrappedSubscribe.date.getDayOfMonth shouldBe (now.getDayOfMonth)
      wrappedSubscribe.date.getMonth shouldBe (now.getMonth)
      wrappedSubscribe.date.getYear shouldBe (now.getYear)

      val crmContactUpdatePropertiesEvent: CrmContactUpdateProperties =
        CrmContactUpdateProperties(id = UserId("john-doe5"), eventDate = now)
      actorSystem.eventStream.publish(crmContactUpdatePropertiesEvent)

      val wrappedCrmContactUpdateProperties = probe.expectMsgType[CrmContactEventWrapper](2.minutes)
      actorSystem.eventStream.publish(wrappedCrmContactUpdateProperties)
      wrappedCrmContactUpdateProperties.id shouldBe ("john-doe5")
      wrappedCrmContactUpdateProperties.version > 0 shouldBe (true)
      wrappedCrmContactUpdateProperties.event.fold(ToCrmContactEvent) shouldBe (crmContactUpdatePropertiesEvent)
      wrappedCrmContactUpdateProperties.date.getDayOfMonth shouldBe (now.getDayOfMonth)
      wrappedCrmContactUpdateProperties.date.getMonth shouldBe (now.getMonth)
      wrappedCrmContactUpdateProperties.date.getYear shouldBe (now.getYear)

      // Clean up stuff
      producer ! PoisonPill
      consumer ! PoisonPill

      // sleep to wait for consumer / producer to be gracefully stopped
      Thread.sleep(2000)
    }
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

}

object CrmContactProducerActorIT {
  val configuration: String =
    """
      |akka.log-dead-letters-during-shutdown = off
      |make-api {
      |  kafka {
      |    connection-string = "127.0.0.1:29096"
      |    poll-timeout = 1000
      |    schema-registry = "http://localhost:28086"
      |    topics {
      |      users = "users"
      |      emails = "emails"
      |      proposals = "proposals"
      |      mailjet-events = "mailjet-events"
      |      duplicates-predicted = "duplicates-predicted"
      |      sequences = "sequences"
      |      tracking-events = "tracking-events"
      |      ideas = "ideas"
      |      crm-contact = "crm-contact"
      |      users-update = "users-update"
      |    }
      |  }
      |}
    """.stripMargin

  val actorSystem = ActorSystem("CrmContactProducerIT", ConfigFactory.parseString(configuration))
}
