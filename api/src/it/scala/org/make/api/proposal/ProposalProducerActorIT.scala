package org.make.api.proposal

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.sksamuel.avro4s.{FromRecord, RecordFormat}
import com.typesafe.config.ConfigFactory
import org.make.api.proposal.PublishedProposalEvent.{ProposalAuthorInfo, ProposalEventWrapper, ProposalProposed}
import org.make.api.technical.AvroSerializers
import org.make.api.{KafkaTest, KafkaTestConsumerActor}
import org.make.core.proposal.ProposalId
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ProposalProducerActorIT
    extends TestKit(ProposalProducerActorIT.actorSystem)
    with KafkaTest
    with ImplicitSender
    with AvroSerializers {

  feature("Proposal producer") {
    scenario("send wrapped event into kafka") {
      Given("a producer on the proposal topic and a consumer on the same topic")
      val actorSystem = system
      val producer: ActorRef = actorSystem.actorOf(ProposalProducerActor.props, ProposalProducerActor.name)

      val probe = TestProbe()
      implicit val fromRecord: FromRecord[ProposalEventWrapper] = FromRecord[ProposalEventWrapper]
      val consumer = {
        val (name: String, props: Props) =
          KafkaTestConsumerActor.propsAndName(
            RecordFormat[ProposalEventWrapper],
            ProposalProducerActor.topicKey,
            probe.ref
          )
        actorSystem.actorOf(props, name)
      }

      // Wait for actors to init
      Await.result(KafkaTestConsumerActor.waitUntilReady(consumer), atMost = 2.minutes)
      logger.info("Consumer is ready")
      KafkaTestConsumerActor.waitUntilReady(producer)
      logger.info("Producer is ready")

      When("I send a message to the producer")
      actorSystem.eventStream.publish(
        ProposalProposed(
          id = ProposalId("123456789"),
          slug = "ma-raison-de-proposer",
          requestContext = RequestContext.empty,
          author = ProposalAuthorInfo(userId = UserId("123987"), firstName = Some("Julien"), None, None),
          userId = UserId("123987"),
          eventDate = DateHelper.now(),
          content = "Ma raison de proposer",
          operation = None,
          theme = None
        )
      )

      Then("I should receive the wrapped version of it in the consumer")
      val wrapped = probe.expectMsgType[ProposalEventWrapper](2.minutes)
      wrapped.id should be("123456789")
      wrapped.eventType should be("ProposalProposed")

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
      |    }
      |  }
      |}
    """.stripMargin

  val actorSystem = ActorSystem("ProposalProducerIT", ConfigFactory.parseString(configuration))

}