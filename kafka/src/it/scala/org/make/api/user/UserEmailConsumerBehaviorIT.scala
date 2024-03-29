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

package org.make.api.user

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.KafkaProducer
import org.make.api.technical.KafkaConsumerBehavior.Protocol
import org.make.api.technical.crm.{SendMailPublisherService, SendMailPublisherServiceComponent}
import org.make.api.userhistory._
import org.make.api.{KafkaConsumerTest, KafkaTestConsumerBehavior, TestUtils}
import org.make.core.reference.Country
import org.make.core.user.{User, UserId, UserType}
import org.make.core.{DateHelper, EventId, MakeSerializable, RequestContext}

import java.time.ZonedDateTime
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class UserEmailConsumerBehaviorIT
    extends ScalaTestWithActorTestKit(UserEmailConsumerBehaviorIT.actorSystem)
    with KafkaConsumerTest[UserEventWrapper]
    with UserServiceComponent
    with SendMailPublisherServiceComponent {

// If wou want to change ports and names to avoid collisions, just override them
  override val kafkaName: String = "kafkamailjeteventconsumer"
  override val kafkaExposedPort: Int = 29292
  override val registryName: String = "registrymailjeteventconsumer"
  override val registryExposedPort: Int = 28282
  override val zookeeperName: String = "zookeepermailjeteventconsumer"
  override val zookeeperExposedPort: Int = 42185
  override val userService: UserService = mock[UserService]
  override val sendMailPublisherService: SendMailPublisherService = mock[SendMailPublisherService]

  override val topic: String = "users"
  override val producer: KafkaProducer[String, UserEventWrapper] = createProducer

  implicit val scheduler: Scheduler = testKit.scheduler

  val consumer: ActorRef[Protocol] =
    testKit.spawn(UserEmailConsumerBehavior(userService, sendMailPublisherService), "UserEvent")

  override def beforeAll(): Unit = {
    super.beforeAll()
    Await.result(KafkaTestConsumerBehavior.waitUntilReady(consumer), atMost = 2.minutes)
  }

  override def afterAll(): Unit = {
    testKit.system.terminate()
    Await.result(testKit.system.whenTerminated, atMost = 10.seconds)
    super.afterAll()
  }

  val dateNow: ZonedDateTime = DateHelper.now()

  val user: User = TestUtils.user(UserId("user"), userType = UserType.UserTypeUser)
  val organisation: User = TestUtils.user(UserId("organisation"), userType = UserType.UserTypeOrganisation)
  val personality1: User = TestUtils.user(UserId("personality1"), userType = UserType.UserTypePersonality)

  when(userService.getUser(eqTo(UserId("user")))).thenReturn(Future.successful(Some(user)))
  when(userService.getUser(eqTo(UserId("organisation"))))
    .thenReturn(Future.successful(Some(organisation)))
  when(userService.getUser(eqTo(UserId("personality1"))))
    .thenReturn(Future.successful(Some(personality1)))

  val country: Country = Country("FR")

  Feature("consume ResetPasswordEvent") {
    val probeReset = testKit.createTestProbe[String]()
    Scenario("user reset password") {
      when(
        sendMailPublisherService
          .publishForgottenPassword(eqTo(user), eqTo(RequestContext.empty))
      ).thenAnswer { (_: User, _: RequestContext) =>
        probeReset.ref ! "sendMailPublisherService.publishForgottenPassword called"
        Future.unit
      }

      val event: ResetPasswordEvent =
        ResetPasswordEvent(None, user, country, RequestContext.empty, EventId("consume ResetPasswordEvent"))
      val wrappedEvent = UserEventWrapper(MakeSerializable.V1, "some-event", dateNow, "ResetPasswordEvent", event, None)

      send(wrappedEvent)

      probeReset.expectMessage(2.seconds, "sendMailPublisherService.publishForgottenPassword called")
    }

    Scenario("organisation reset password") {
      when(
        sendMailPublisherService
          .publishForgottenPasswordOrganisation(eqTo(organisation), eqTo(RequestContext.empty))
      ).thenAnswer { (_: User, _: RequestContext) =>
        probeReset.ref ! "sendMailPublisherService.publishForgottenPasswordOrganisation called"
        Future.unit
      }
      val event: ResetPasswordEvent =
        ResetPasswordEvent(None, organisation, country, RequestContext.empty, EventId("organisation reset password"))
      val wrappedEvent =
        UserEventWrapper(MakeSerializable.V1, "some-event", dateNow, "ResetPasswordEvent", event, event.eventId)

      send(wrappedEvent)

      probeReset.expectMessage(2.seconds, "sendMailPublisherService.publishForgottenPasswordOrganisation called")
    }

    Scenario("perso reset password") {
      when(
        sendMailPublisherService
          .publishForgottenPasswordOrganisation(eqTo(personality1), eqTo(RequestContext.empty))
      ).thenAnswer { (_: User, _: RequestContext) =>
        probeReset.ref ! "sendMailPublisherService.publishForgottenPasswordOrganisation perso called"
        Future.unit
      }

      val eventPerso: ResetPasswordEvent =
        ResetPasswordEvent(None, personality1, country, RequestContext.empty, EventId("perso reset password"))
      val wrappedEventPerso =
        UserEventWrapper(
          MakeSerializable.V1,
          "some-event",
          dateNow,
          "ResetPasswordEvent",
          eventPerso,
          eventPerso.eventId
        )

      send(wrappedEventPerso)

      probeReset.expectMessage(2.seconds, "sendMailPublisherService.publishForgottenPasswordOrganisation perso called")
    }
  }

  Feature("consume UserRegisteredEvent") {
    val probeRegistered = testKit.createTestProbe[String]()

    Scenario("user register") {
      when(
        sendMailPublisherService
          .publishRegistration(eqTo(user), eqTo(RequestContext.empty))
      ).thenAnswer { (_: User, _: RequestContext) =>
        probeRegistered.ref ! "sendMailPublisherService.publishRegistration called"
        Future.unit
      }
      val eventUser: UserRegisteredEvent = UserRegisteredEvent(
        connectedUserId = None,
        eventDate = dateNow,
        userId = user.userId,
        requestContext = RequestContext.empty,
        firstName = None,
        country = country,
        eventId = Some(EventId("user register"))
      )

      val wrappedEventUser =
        UserEventWrapper(
          MakeSerializable.V1,
          "some-event",
          dateNow,
          "UserRegisteredEvent",
          eventUser,
          eventUser.eventId
        )

      send(wrappedEventUser)

      probeRegistered.expectMessage(2.seconds, "sendMailPublisherService.publishRegistration called")
    }
  }

  Feature("consume B2BRegistered") {

    def event(organisation: User): UserEventWrapper = {
      val eventOrganisation: OrganisationRegisteredEvent = OrganisationRegisteredEvent(
        connectedUserId = None,
        eventDate = dateNow,
        userId = organisation.userId,
        requestContext = RequestContext.empty,
        email = "some@mail.com",
        country = country,
        eventId = Some(EventId("organisation register"))
      )
      UserEventWrapper(
        MakeSerializable.V1,
        "some-event",
        dateNow,
        "OrganisationRegisteredEvent",
        eventOrganisation,
        eventOrganisation.eventId
      )
    }

    Scenario("organisation register with password") {
      val organisationWithPassword = organisation.copy(
        userId = organisation.userId.copy(organisation.userId.value + "-with-password"),
        hashedPassword = Some("password")
      )
      send(event(organisationWithPassword))
      verifyZeroInteractions(
        sendMailPublisherService.publishRegistrationB2B(organisationWithPassword, RequestContext.empty)
      )
    }

    Scenario("organisation register without password") {
      val orgaProbe = testKit.createTestProbe[String]()
      when(
        sendMailPublisherService
          .publishRegistrationB2B(eqTo(organisation), eqTo(RequestContext.empty))
      ).thenAnswer { (_: User, _: RequestContext) =>
        orgaProbe.ref ! "sendMailPublisherService.publishRegistrationB2B called"
        Future.unit
      }

      send(event(organisation))

      orgaProbe.expectMessage(2.seconds, "sendMailPublisherService.publishRegistrationB2B called")
    }

    Scenario("personality register") {
      val persoProbe = testKit.createTestProbe[String]()
      when(
        sendMailPublisherService
          .publishRegistrationB2B(eqTo(personality1), eqTo(RequestContext.empty))
      ).thenAnswer { (_: User, _: RequestContext) =>
        persoProbe.ref ! "sendMailPublisherService.publishRegistrationB2B called"
        Future.unit
      }
      val event: PersonalityRegisteredEvent = PersonalityRegisteredEvent(
        connectedUserId = None,
        eventDate = dateNow,
        userId = personality1.userId,
        requestContext = RequestContext.empty,
        email = "some@mail.com",
        country = country,
        eventId = Some(EventId("personality register"))
      )
      val wrappedEvent =
        UserEventWrapper(MakeSerializable.V1, "some-event", dateNow, "PersonalityRegisteredEvent", event, event.eventId)

      send(wrappedEvent)

      persoProbe.expectMessage(2.seconds, "sendMailPublisherService.publishRegistrationB2B called")
    }
  }

  Feature("consume UserValidatedAccountEvent") {
    Scenario("user validate") {
      val userProbe = testKit.createTestProbe[String]()
      when(sendMailPublisherService.publishWelcome(eqTo(user), eqTo(RequestContext.empty))).thenAnswer {
        (_: User, _: RequestContext) =>
          userProbe.ref ! "sendMailPublisherService.publishWelcome called"
          Future.unit
      }

      val event: UserValidatedAccountEvent = UserValidatedAccountEvent(
        connectedUserId = None,
        eventDate = dateNow,
        userId = user.userId,
        requestContext = RequestContext.empty,
        country = country,
        eventId = Some(EventId("user validate"))
      )

      val wrappedEvent =
        UserEventWrapper(MakeSerializable.V1, "some-event", dateNow, "UserValidatedAccountEvent", event, event.eventId)

      send(wrappedEvent)

      userProbe.expectMessage(2.seconds, "sendMailPublisherService.publishWelcome called")
    }

    Scenario("organisation validate") {
      val orgaProbe = testKit.createTestProbe[String]()
      val event: UserValidatedAccountEvent = UserValidatedAccountEvent(
        connectedUserId = None,
        eventDate = dateNow,
        userId = organisation.userId,
        requestContext = RequestContext.empty,
        country = country,
        eventId = Some(EventId("organisation validate"))
      )
      val wrappedEvent =
        UserEventWrapper(MakeSerializable.V1, "some-event", dateNow, "UserValidatedAccountEvent", event, event.eventId)

      send(wrappedEvent)

      orgaProbe.expectNoMessage()
    }

    Scenario("personality validate") {
      val persoProbe = testKit.createTestProbe[String]()
      val event: UserValidatedAccountEvent = UserValidatedAccountEvent(
        connectedUserId = None,
        eventDate = dateNow,
        userId = personality1.userId,
        requestContext = RequestContext.empty,
        country = country,
        eventId = Some(EventId("personality validate"))
      )
      val wrappedEvent =
        UserEventWrapper(MakeSerializable.V1, "some-event", dateNow, "UserValidatedAccountEvent", event, event.eventId)

      send(wrappedEvent)

      persoProbe.expectNoMessage()
    }
  }

  Feature("consume ResendValidationEmailEvent") {
    val probeValidation = testKit.createTestProbe[String]()

    Scenario("user resend") {
      when(
        sendMailPublisherService
          .resendRegistration(eqTo(user), eqTo(RequestContext.empty))
      ).thenAnswer { (_: User, _: RequestContext) =>
        probeValidation.ref ! "sendMailPublisherService.resendRegistration called"
        Future.unit
      }

      val event: ResendValidationEmailEvent =
        ResendValidationEmailEvent(
          None,
          dateNow,
          user.userId,
          country,
          RequestContext.empty,
          Some(EventId("user resend"))
        )
      val wrappedEvent =
        UserEventWrapper(MakeSerializable.V1, "some-event", dateNow, "ResendValidationEmailEvent", event, event.eventId)

      send(wrappedEvent)

      probeValidation.expectMessage(2.seconds, "sendMailPublisherService.resendRegistration called")
    }

    Scenario("organisation resend") {
      when(
        sendMailPublisherService
          .resendRegistration(eqTo(organisation), eqTo(RequestContext.empty))
      ).thenAnswer { (_: User, _: RequestContext) =>
        probeValidation.ref ! "sendMailPublisherService.resendRegistration called"
        Future.unit
      }

      val event: ResendValidationEmailEvent =
        ResendValidationEmailEvent(
          None,
          dateNow,
          organisation.userId,
          country,
          RequestContext.empty,
          Some(EventId("organisation resend"))
        )
      val wrappedEvent =
        UserEventWrapper(MakeSerializable.V1, "some-event", dateNow, "ResendValidationEmailEvent", event, event.eventId)

      send(wrappedEvent)

      probeValidation.expectMessage(2.seconds, "sendMailPublisherService.resendRegistration called")
    }

    Scenario("personality resend") {
      when(
        sendMailPublisherService
          .resendRegistration(eqTo(personality1), eqTo(RequestContext.empty))
      ).thenAnswer { (_: User, _: RequestContext) =>
        probeValidation.ref ! "sendMailPublisherService.resendRegistration called"
        Future.unit
      }

      val event: ResendValidationEmailEvent =
        ResendValidationEmailEvent(
          None,
          dateNow,
          personality1.userId,
          country,
          RequestContext.empty,
          Some(EventId("personality resend"))
        )
      val wrappedEvent =
        UserEventWrapper(MakeSerializable.V1, "some-event", dateNow, "ResendValidationEmailEvent", event, event.eventId)

      send(wrappedEvent)

      probeValidation.expectMessage(2.seconds, "sendMailPublisherService.resendRegistration called")
    }
  }
}

object UserEmailConsumerBehaviorIT {
  // This configuration cannot be dynamic, port values _must_ match reality
  val configuration: String =
    """
      |akka.log-dead-letters-during-shutdown = off
      |make-api {
      |  kafka {
      |    connection-string = "127.0.0.1:29292"
      |    poll-timeout = 1000
      |    schema-registry = "http://localhost:28282"
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
    ActorSystem[Nothing](Behaviors.empty, "UserEmailConsumerActorIT", ConfigFactory.parseString(configuration))
}
