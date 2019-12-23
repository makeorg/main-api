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

import java.time.ZonedDateTime

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.userhistory._
import org.make.api.{KafkaTest, KafkaTestConsumerActor}
import org.make.core.proposal.ProposalId
import org.make.core.user.UserId
import org.make.core.{AvroSerializers, DateHelper, MakeSerializable, RequestContext}
import org.mockito.{ArgumentMatchers, Mockito}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class ProposalUserHistoryConsumerActorIT
    extends TestKit(ProposalUserHistoryConsumerActorIT.actorSystem)
    with KafkaTest
    with ImplicitSender
    with AvroSerializers
    with UserHistoryCoordinatorServiceComponent {

  // If wou want to change ports and names to avoid collisions, just override them
  override val kafkaName: String = "kafkaproposaleventconsumer"
  override val kafkaExposedPort: Int = 29192
  override val registryName: String = "registryproposaleventconsumer"
  override val registryExposedPort: Int = 28182
  override val zookeeperName: String = "zookeeperproposaleventconsumer"
  override val zookeeperExposedPort: Int = 32184

  override val userHistoryCoordinatorService: UserHistoryCoordinatorService =
    mock[UserHistoryCoordinatorService]

  val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]
  val schema: SchemaFor[ProposalEventWrapper] = SchemaFor[ProposalEventWrapper]
  val consumer: ActorRef =
    system.actorOf(ProposalUserHistoryConsumerActor.props(userHistoryCoordinatorService), "ProposalEvent")
  val producer: KafkaProducer[String, ProposalEventWrapper] = createProducer(schema, format)

  override def beforeAll() = {
    super.beforeAll()
    Await.result(KafkaTestConsumerActor.waitUntilReady(consumer), atMost = 2.minutes)
  }

  override def afterAll() = {
    consumer ! PoisonPill
    producer.close()
    Thread.sleep(2000)
    super.afterAll()
  }

  feature("consume Proposal event") {

    scenario("Reacting to ProposalProposed") {
      val probe = TestProbe()
      val now: ZonedDateTime = DateHelper.now()

      Mockito
        .when(
          userHistoryCoordinatorService.logHistory(
            ArgumentMatchers.eq(
              LogUserProposalEvent(
                userId = UserId("user-id"),
                requestContext = RequestContext.empty,
                action = UserAction(
                  date = now,
                  actionType = LogUserProposalEvent.action,
                  arguments = UserProposal(content = "content", None)
                )
              )
            )
          )
        )
        .thenAnswer(_ => {
          probe.ref ! "LogUserProposalProposedEvent called"
          Future.successful(true)
        })

      val eventProposed: ProposalProposed = ProposalProposed(
        id = ProposalId("proposal-id"),
        slug = "slug",
        requestContext = RequestContext.empty,
        author = ProposalAuthorInfo(UserId("user-id"), None, None, None),
        userId = UserId("user-id"),
        eventDate = now,
        content = "content",
        operation = None,
        theme = None,
        language = None,
        country = None,
        question = None
      )
      val wrappedProposalProposed = ProposalEventWrapper(
        version = MakeSerializable.V3,
        id = "some-proposed-event",
        date = now,
        event = eventProposed,
        eventType = eventProposed.getClass.getSimpleName
      )

      producer.send(new ProducerRecord[String, ProposalEventWrapper]("proposals", wrappedProposalProposed))

      probe.expectMsg(500 millis, "LogUserProposalProposedEvent called")

    }

    scenario("Reacting to ProposalAccepted") {
      val probe = TestProbe()
      val now: ZonedDateTime = DateHelper.now()

      val eventAccepted: ProposalAccepted = ProposalAccepted(
        id = ProposalId("proposal-id"),
        eventDate = now,
        requestContext = RequestContext.empty,
        moderator = UserId("moderator-id"),
        edition = None,
        sendValidationEmail = false,
        labels = Seq.empty,
        tags = Seq.empty,
        similarProposals = Seq.empty,
        idea = None,
        theme = None,
        operation = None,
        question = None
      )
      val wrappedProposalAccepted = ProposalEventWrapper(
        version = MakeSerializable.V3,
        id = "some-accepted-event",
        date = now,
        event = eventAccepted,
        eventType = eventAccepted.getClass.getSimpleName
      )

      Mockito
        .when(
          userHistoryCoordinatorService.logHistory(
            ArgumentMatchers.eq(
              LogAcceptProposalEvent(
                userId = UserId("moderator-id"),
                requestContext = RequestContext.empty,
                action = UserAction(date = now, actionType = ProposalAccepted.actionType, arguments = eventAccepted)
              )
            )
          )
        )
        .thenAnswer(_ => {
          probe.ref ! "LogUserProposalAcceptedEvent called"
          Future.successful(true)
        })

      producer.send(new ProducerRecord[String, ProposalEventWrapper]("proposals", wrappedProposalAccepted))

      probe.expectMsg(500 millis, "LogUserProposalAcceptedEvent called")

    }

    scenario("Reacting to ProposalRefused") {
      val probe = TestProbe()
      val now: ZonedDateTime = DateHelper.now()

      val eventRefused: ProposalRefused = ProposalRefused(
        id = ProposalId("proposal-id"),
        eventDate = now,
        requestContext = RequestContext.empty,
        moderator = UserId("moderator-id"),
        sendRefuseEmail = false,
        refusalReason = None,
        operation = None
      )
      val wrappedProposalRefused = ProposalEventWrapper(
        version = MakeSerializable.V3,
        id = "some-refused-event",
        date = now,
        event = eventRefused,
        eventType = eventRefused.getClass.getSimpleName
      )

      Mockito
        .when(
          userHistoryCoordinatorService.logHistory(
            ArgumentMatchers.eq(
              LogRefuseProposalEvent(
                userId = UserId("moderator-id"),
                requestContext = RequestContext.empty,
                action = UserAction(date = now, actionType = ProposalRefused.actionType, arguments = eventRefused)
              )
            )
          )
        )
        .thenAnswer(_ => {
          probe.ref ! "LogUserProposalRefusedEvent called"
          Future.successful(true)
        })

      producer.send(new ProducerRecord[String, ProposalEventWrapper]("proposals", wrappedProposalRefused))

      probe.expectMsg(500 millis, "LogUserProposalRefusedEvent called")

    }

    scenario("Reacting to ProposalPostponed") {
      val probe = TestProbe()
      val now: ZonedDateTime = DateHelper.now()

      val eventPostponed: ProposalPostponed = ProposalPostponed(
        id = ProposalId("proposal-id"),
        eventDate = now,
        requestContext = RequestContext.empty,
        moderator = UserId("moderator-id")
      )
      val wrappedProposalPostponed = ProposalEventWrapper(
        version = MakeSerializable.V3,
        id = "some-postponed-event",
        date = now,
        event = eventPostponed,
        eventType = eventPostponed.getClass.getSimpleName
      )

      Mockito
        .when(
          userHistoryCoordinatorService.logHistory(
            ArgumentMatchers.eq(
              LogPostponeProposalEvent(
                userId = UserId("moderator-id"),
                requestContext = RequestContext.empty,
                action = UserAction(date = now, actionType = ProposalPostponed.actionType, arguments = eventPostponed)
              )
            )
          )
        )
        .thenAnswer(_ => {
          probe.ref ! "LogUserProposalPostponedEvent called"
          Future.successful(true)
        })

      producer.send(new ProducerRecord[String, ProposalEventWrapper]("proposals", wrappedProposalPostponed))

      probe.expectMsg(500 millis, "LogUserProposalPostponedEvent called")

    }

    scenario("Reacting to ProposalLocked") {
      val probe = TestProbe()
      val now: ZonedDateTime = DateHelper.now()

      val eventLocked: ProposalLocked = ProposalLocked(
        id = ProposalId("proposal-id"),
        eventDate = now,
        requestContext = RequestContext.empty,
        moderatorId = UserId("moderator-id"),
        moderatorName = None
      )
      val wrappedProposalLocked = ProposalEventWrapper(
        version = MakeSerializable.V3,
        id = "some-accepted-event",
        date = now,
        event = eventLocked,
        eventType = eventLocked.getClass.getSimpleName
      )

      Mockito
        .when(
          userHistoryCoordinatorService.logHistory(
            ArgumentMatchers.eq(
              LogLockProposalEvent(
                userId = UserId("moderator-id"),
                requestContext = RequestContext.empty,
                action = UserAction(date = now, actionType = ProposalLocked.actionType, arguments = eventLocked),
                moderatorName = None
              )
            )
          )
        )
        .thenAnswer(_ => {
          probe.ref ! "LogUserProposalLockedEvent called"
          Future.successful(true)
        })

      producer.send(new ProducerRecord[String, ProposalEventWrapper]("proposals", wrappedProposalLocked))

      probe.expectMsg(500 millis, "LogUserProposalLockedEvent called")

    }

  }
}

object ProposalUserHistoryConsumerActorIT {
  // This configuration cannot be dynamic, port values _must_ match reality
  val configuration: String =
    """
      |akka.log-dead-letters-during-shutdown = off
      |make-api {
      |  kafka {
      |    connection-string = "127.0.0.1:29192"
      |    poll-timeout = 1000
      |    schema-registry = "http://localhost:28182"
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

  val actorSystem = ActorSystem("ProposalUserHistoryConsumerActorIT", ConfigFactory.parseString(configuration))
}
