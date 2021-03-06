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
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.KafkaProducer
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.technical.KafkaConsumerBehavior
import org.make.api.userhistory._
import org.make.api.{KafkaConsumerTest, KafkaTestConsumerBehavior}
import org.make.core.proposal.ProposalId
import org.make.core.user.UserId
import org.make.core.{DateHelper, MakeSerializable, RequestContext}

import java.time.ZonedDateTime
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ProposalUserHistoryConsumerBehaviorIT
    extends ScalaTestWithActorTestKit(ProposalUserHistoryConsumerBehaviorIT.actorSystem)
    with KafkaConsumerTest[ProposalEventWrapper]
    with UserHistoryCoordinatorServiceComponent {

  // If wou want to change ports and names to avoid collisions, just override them
  override val kafkaName: String = "kafkaproposaleventconsumer"
  override val kafkaExposedPort: Int = 29192
  override val registryName: String = "registryproposaleventconsumer"
  override val registryExposedPort: Int = 28182
  override val zookeeperName: String = "zookeeperproposaleventconsumer"
  override val zookeeperExposedPort: Int = 32184

  override val topic: String = "proposals"
  override val producer: KafkaProducer[String, ProposalEventWrapper] = createProducer

  override val userHistoryCoordinatorService: UserHistoryCoordinatorService =
    mock[UserHistoryCoordinatorService]

  val consumer: ActorRef[KafkaConsumerBehavior.Protocol] =
    testKit.spawn(ProposalUserHistoryConsumerBehavior(userHistoryCoordinatorService), "ProposalEvent")

  implicit val scheduler: Scheduler = testKit.system.scheduler

  override def beforeAll(): Unit = {
    super.beforeAll()
    Await.result(KafkaTestConsumerBehavior.waitUntilReady(consumer), atMost = 2.minutes)
  }

  override def afterAll(): Unit = {
    testKit.system.terminate()
    Await.result(testKit.system.whenTerminated, atMost = 10.seconds)
    super.afterAll()
  }

  Feature("consume Proposal event") {

    Scenario("Reacting to ProposalProposed") {
      val probe = testKit.createTestProbe[String]()
      val now: ZonedDateTime = DateHelper.now()

      when(
        userHistoryCoordinatorService.logHistory(
          eqTo(
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
      ).thenAnswer { _: LogUserProposalEvent =>
        probe.ref ! "LogUserProposalProposedEvent called"
      }

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
        question = None,
        eventId = None
      )
      val wrappedProposalProposed = ProposalEventWrapper(
        version = MakeSerializable.V3,
        id = "some-proposed-event",
        date = now,
        event = eventProposed,
        eventType = eventProposed.getClass.getSimpleName,
        eventId = None
      )

      send(wrappedProposalProposed)

      probe.expectMessage(2.seconds, "LogUserProposalProposedEvent called")

    }

    Scenario("Reacting to ProposalAccepted") {
      val probe = testKit.createTestProbe[String]()
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
        question = None,
        eventId = None
      )
      val wrappedProposalAccepted = ProposalEventWrapper(
        version = MakeSerializable.V3,
        id = "some-accepted-event",
        date = now,
        event = eventAccepted,
        eventType = eventAccepted.getClass.getSimpleName,
        eventId = None
      )

      when(
        userHistoryCoordinatorService.logHistory(
          eqTo(
            LogAcceptProposalEvent(
              userId = UserId("moderator-id"),
              requestContext = RequestContext.empty,
              action = UserAction(date = now, actionType = ProposalAccepted.actionType, arguments = eventAccepted)
            )
          )
        )
      ).thenAnswer { _: LogAcceptProposalEvent =>
        probe.ref ! "LogUserProposalAcceptedEvent called"
      }

      send(wrappedProposalAccepted)

      probe.expectMessage(2.seconds, "LogUserProposalAcceptedEvent called")

    }

    Scenario("Reacting to ProposalRefused") {
      val probe = testKit.createTestProbe[String]()
      val now: ZonedDateTime = DateHelper.now()

      val eventRefused: ProposalRefused = ProposalRefused(
        id = ProposalId("proposal-id"),
        eventDate = now,
        requestContext = RequestContext.empty,
        moderator = UserId("moderator-id"),
        sendRefuseEmail = false,
        refusalReason = None,
        operation = None,
        eventId = None
      )
      val wrappedProposalRefused = ProposalEventWrapper(
        version = MakeSerializable.V3,
        id = "some-refused-event",
        date = now,
        event = eventRefused,
        eventType = eventRefused.getClass.getSimpleName,
        eventId = None
      )

      when(
        userHistoryCoordinatorService.logHistory(
          eqTo(
            LogRefuseProposalEvent(
              userId = UserId("moderator-id"),
              requestContext = RequestContext.empty,
              action = UserAction(date = now, actionType = ProposalRefused.actionType, arguments = eventRefused)
            )
          )
        )
      ).thenAnswer { _: LogRefuseProposalEvent =>
        probe.ref ! "LogUserProposalRefusedEvent called"
      }

      send(wrappedProposalRefused)

      probe.expectMessage(2.seconds, "LogUserProposalRefusedEvent called")

    }

    Scenario("Reacting to ProposalPostponed") {
      val probe = testKit.createTestProbe[String]()
      val now: ZonedDateTime = DateHelper.now()

      val eventPostponed: ProposalPostponed = ProposalPostponed(
        id = ProposalId("proposal-id"),
        eventDate = now,
        requestContext = RequestContext.empty,
        moderator = UserId("moderator-id"),
        eventId = None
      )
      val wrappedProposalPostponed = ProposalEventWrapper(
        version = MakeSerializable.V3,
        id = "some-postponed-event",
        date = now,
        event = eventPostponed,
        eventType = eventPostponed.getClass.getSimpleName,
        eventId = None
      )

      when(
        userHistoryCoordinatorService.logHistory(
          eqTo(
            LogPostponeProposalEvent(
              userId = UserId("moderator-id"),
              requestContext = RequestContext.empty,
              action = UserAction(date = now, actionType = ProposalPostponed.actionType, arguments = eventPostponed)
            )
          )
        )
      ).thenAnswer { _: LogPostponeProposalEvent =>
        probe.ref ! "LogUserProposalPostponedEvent called"
      }

      send(wrappedProposalPostponed)

      probe.expectMessage(2.seconds, "LogUserProposalPostponedEvent called")

    }

    Scenario("Reacting to ProposalLocked") {
      val probe = testKit.createTestProbe[String]()
      val now: ZonedDateTime = DateHelper.now()

      val eventLocked: ProposalLocked = ProposalLocked(
        id = ProposalId("proposal-id"),
        eventDate = now,
        requestContext = RequestContext.empty,
        moderatorId = UserId("moderator-id"),
        moderatorName = None,
        eventId = None
      )
      val wrappedProposalLocked = ProposalEventWrapper(
        version = MakeSerializable.V3,
        id = "some-accepted-event",
        date = now,
        event = eventLocked,
        eventType = eventLocked.getClass.getSimpleName,
        eventId = None
      )

      when(
        userHistoryCoordinatorService.logHistory(
          eqTo(
            LogLockProposalEvent(
              userId = UserId("moderator-id"),
              requestContext = RequestContext.empty,
              action = UserAction(date = now, actionType = ProposalLocked.actionType, arguments = eventLocked),
              moderatorName = None
            )
          )
        )
      ).thenAnswer { _: LogLockProposalEvent =>
        probe.ref ! "LogUserProposalLockedEvent called"
      }

      send(wrappedProposalLocked)

      probe.expectMessage(2.seconds, "LogUserProposalLockedEvent called")

    }

  }
}

object ProposalUserHistoryConsumerBehaviorIT {
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

  val actorSystem: ActorSystem[Nothing] = ActorSystem[Nothing](
    Behaviors.empty,
    "ProposalUserHistoryConsumerActorIT",
    ConfigFactory.parseString(configuration)
  )
}
