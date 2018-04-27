package org.make.api.technical.crm

import java.time.ZonedDateTime

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.sksamuel.avro4s.{FromRecord, RecordFormat}
import com.typesafe.config.ConfigFactory
import org.make.api.technical.AvroSerializers
import org.make.api.{KafkaTest, KafkaTestConsumerActor}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class MailJetCallbackProducerActorIT
    extends TestKit(MailJetCallbackProducerActorIT.actorSystem)
    with KafkaTest
    with ImplicitSender
    with AvroSerializers {

  feature("MailJetCallback producer") {
    scenario("send wrapped event into kafka") {
      Given("a producer on the Mailjet event topic and a consumer on the same topic")
      val actorSystem = system
      val producer: ActorRef =
        actorSystem.actorOf(MailJetCallbackProducerActor.props, MailJetCallbackProducerActor.name)

      val probe = TestProbe()
      implicit val fromRecord: FromRecord[MailJetEventWrapper] = FromRecord[MailJetEventWrapper]
      val consumer = {
        val (name: String, props: Props) =
          KafkaTestConsumerActor.propsAndName(
            RecordFormat[MailJetEventWrapper],
            MailJetCallbackProducerActor.topicKey,
            probe.ref
          )
        actorSystem.actorOf(props, name)
      }

      // Wait for actors to init
      Await.result(KafkaTestConsumerActor.waitUntilReady(consumer), atMost = 2.minutes)
      logger.info("Consumer is ready")
      KafkaTestConsumerActor.waitUntilReady(producer)
      logger.info("Producer is ready")

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

      actorSystem.eventStream.publish(bounceEvent)

      Then("I should receive the wrapped version of it in the consumer")
      val wrapped = probe.expectMsgType[MailJetEventWrapper](2.minutes)
      wrapped.id shouldBe ("test@make.org")
      wrapped.version > 0 shouldBe (true)
      wrapped.event.fold(ToMailJetEvent) shouldBe (bounceEvent)
      wrapped.date shouldBe (ZonedDateTime.parse("2015-05-05T07:49:55Z"))

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

object MailJetCallbackProducerActorIT {
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
      |      crm-contact = "crm-contact"
      |      users-update = "users-update"
      |      duplicates-predicted = "duplicates-predicted"
      |      sequences = "sequences"
      |      tracking-events = "tracking-events"
      |      ideas = "ideas"
      |    }
      |  }
      |}
    """.stripMargin

  val actorSystem = ActorSystem("MailJetCallbackProducerIT", ConfigFactory.parseString(configuration))
}