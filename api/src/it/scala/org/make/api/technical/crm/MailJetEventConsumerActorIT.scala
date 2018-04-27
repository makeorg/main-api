package org.make.api.technical.crm

import java.time.ZonedDateTime

import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.ProducerRecord
import org.make.api.technical.AvroSerializers
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.{KafkaTest, KafkaTestConsumerActor}
import org.make.core.user.MailingErrorLog
import org.make.core.{DateHelper, MakeSerializable}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{ArgumentMatchers, Mockito}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class MailJetEventConsumerActorIT
    extends TestKit(MailJetEventConsumerActorIT.actorSystem)
    with KafkaTest
    with ImplicitSender
    with AvroSerializers
    with UserServiceComponent {

// If wou want to change ports and names to avoid collisions, just override them
  override val kafkaName: String = "kafkamailjeteventconsumer"
  override val kafkaExposedPort: Int = 29092
  override val registryName: String = "registrymailjeteventconsumer"
  override val registryExposedPort: Int = 28082
  override val zookeeperName: String = "zookeepermailjeteventconsumer"
  override val zookeeperExposedPort: Int = 22183
  override val userService: UserService = mock[UserService]

  implicit def toAnswerWithArguments[T](f: (InvocationOnMock) => T): Answer[T] =
    (invocation: InvocationOnMock) => f(invocation)
  implicit def toAnswer[T](f: () => T): Answer[T] = (_: InvocationOnMock) => f()

  feature("consume MailJet event") {

    scenario("Reacting to a MailJet events") {
      val probe = TestProbe()
      val consumer = system.actorOf(MailJetEventConsumerActor.props(userService = userService), "MailJetBounceEvent")
      val format = RecordFormat[MailJetEventWrapper]
      val schema = SchemaFor[MailJetEventWrapper]
      val producer = createProducer(schema, format)

      Await.result(KafkaTestConsumerActor.waitUntilReady(consumer), atMost = 2.minutes)

      Mockito
        .when(userService.updateIsHardBounce(ArgumentMatchers.eq("test@example.com"), ArgumentMatchers.eq(true)))
        .thenAnswer(() => {
          probe.ref ! "userService.updateIsHardBounce called"
          Future.successful(true)
        })
      val now: ZonedDateTime = DateHelper.now()
      Mockito
        .when(
          userService.updateLastMailingError(
            ArgumentMatchers.eq("test@example.com"),
            ArgumentMatchers
              .eq[Option[MailingErrorLog]](Some(MailingErrorLog(error = MailJetError.InvalidDomaine.name, date = now)))
          )
        )
        .thenAnswer(() => {
          probe.ref ! "userService.updateLastMailingError called"
          Future.successful(true)
        })
      Mockito
        .when(userService.updateOptInNewsletter(ArgumentMatchers.eq("test@example.com"), ArgumentMatchers.eq(false)))
        .thenAnswer(() => {
          probe.ref ! "userService.updateOptInNewsletter called"
          Future.successful(true)
        })
      Mockito
        .when(
          userService
            .updateOptInNewsletter(ArgumentMatchers.eq("test_unsubscribe@example.com"), ArgumentMatchers.eq(false))
        )
        .thenAnswer(() => {
          probe.ref ! "userService.updateOptInNewsletter called for unsubscribe event"
          Future.successful(true)
        })

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
      val wrappedBounceEventBounce = MailJetEventWrapper(
        version = MakeSerializable.V1,
        id = "some-event",
        date = now,
        event = MailJetEventWrapper.wrapEvent(eventBounce)
      )

      When("I send bounce event")
      producer.send(new ProducerRecord[String, MailJetEventWrapper]("mailjet-events", wrappedBounceEventBounce))

      Then("message is consumed and userService is called to update data")
      probe.expectMsg(500 millis, "userService.updateIsHardBounce called")
      probe.expectMsg(500 millis, "userService.updateLastMailingError called")

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
      val wrappedSpamEventBounce = MailJetEventWrapper(
        version = MakeSerializable.V1,
        id = "some-event",
        date = DateHelper.now(),
        event = MailJetEventWrapper.wrapEvent(eventSpam)
      )

      When("I send spam event")
      producer.send(new ProducerRecord[String, MailJetEventWrapper]("mailjet-events", wrappedSpamEventBounce))

      Then("Message is consumed and userService is called to update data")
      probe.expectMsg(500 millis, "userService.updateOptInNewsletter called")

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
        event = MailJetEventWrapper.wrapEvent(eventUnsubscribe)
      )

      When("I send unsubscribe event")
      producer.send(new ProducerRecord[String, MailJetEventWrapper]("mailjet-events", wrappedUnsubscribeEventBounce))

      Then("Message is consumed and userService is called to update data")
      probe.expectMsg(500 millis, "userService.updateOptInNewsletter called for unsubscribe event")

      consumer ! PoisonPill
      producer.close()
      Thread.sleep(2000)
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

  val actorSystem = ActorSystem("MailJetEventConsumerActorIT", ConfigFactory.parseString(configuration))
}