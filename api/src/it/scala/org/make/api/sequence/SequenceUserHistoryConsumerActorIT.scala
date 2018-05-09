package org.make.api.sequence

import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.ProducerRecord
import org.make.api.sequence.PublishedSequenceEvent.{SequenceEventWrapper, SequenceUpdated}
import org.make.api.technical.AvroSerializers
import org.make.api.userhistory.{LogUserUpdateSequenceEvent, UserAction}
import org.make.api.{KafkaTest, KafkaTestConsumerActor}
import org.make.core.sequence.SequenceId
import org.make.core.user.UserId
import org.make.core.{DateHelper, MakeSerializable, RequestContext}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class SequenceUserHistoryConsumerActorIT
    extends TestKit(SequenceUserHistoryConsumerActorIT.actorSystem)
    with KafkaTest
    with ImplicitSender
    with AvroSerializers {

  // If wou want to change ports and names to avoid collisions, just override them
  override val kafkaName: String = "kafkasequserhistconsumer"
  override val kafkaExposedPort: Int = 29091
  override val registryName: String = "registrysequserhistconsumer"
  override val registryExposedPort: Int = 28080
  override val zookeeperName: String = "zookeepersequserhistconsumer"
  override val zookeeperExposedPort: Int = 22181

  feature("SequenceUserHistoryConsumerActor") {
    scenario("Reacting to a SequenceUpdated event") {
      val probe = TestProbe()
      val consumer = system.actorOf(SequenceUserHistoryConsumerActor.props(probe.ref), "SequenceUpdated")
      val format = RecordFormat[SequenceEventWrapper]
      val schema = SchemaFor[SequenceEventWrapper]
      val producer = createProducer(schema, format)

      Await.result(KafkaTestConsumerActor.waitUntilReady(consumer), atMost = 2.minutes)

      val event = SequenceUpdated(
        id = SequenceId("some-sequence"),
        userId = UserId("some-user"),
        eventDate = DateHelper.now(),
        requestContext = RequestContext.empty,
        title = None,
        status = None,
        operation = None,
        operationId = None,
        themeIds = Seq.empty
      )
      val wrappedEvent = SequenceEventWrapper(
        version = MakeSerializable.V1,
        id = "some-sequence",
        date = DateHelper.now(),
        eventType = "SequenceUpdated",
        event = SequenceEventWrapper.wrapEvent(event)
      )
      producer.send(new ProducerRecord[String, SequenceEventWrapper]("sequences", wrappedEvent))

      val expectedMessage = LogUserUpdateSequenceEvent(
        userId = event.userId,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = SequenceUpdated.actionType, arguments = event)
      )

      val answer = probe.expectMsg(30.seconds, expectedMessage)
      probe.reply(answer)

      consumer ! PoisonPill
      producer.close()

      Thread.sleep(2000)
    }
  }

}

object SequenceUserHistoryConsumerActorIT {
  // This configuration cannot be dynamic, port values _must_ match reality
  val configuration: String =
    """
      |akka.log-dead-letters-during-shutdown = off
      |make-api {
      |  kafka {
      |    connection-string = "127.0.0.1:29091"
      |    poll-timeout = 1000
      |    schema-registry = "http://localhost:28080"
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

  val actorSystem = ActorSystem("SequenceUserHistoryConsumerActorIT", ConfigFactory.parseString(configuration))
}
