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

import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.ProducerRecord
import org.make.api.technical.AvroSerializers
import org.make.api.technical.crm.PublishedCrmContactEvent._
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.{KafkaTest, KafkaTestConsumerActor}
import org.make.core.profile.Profile
import org.make.core.reference.{Country, Language}
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, MakeSerializable}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{ArgumentMatchers, Mockito}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.implicitConversions

class CrmContactEventConsumerActorIt
    extends TestKit(CrmContactEventConsumerActorIt.actorSystem)
    with KafkaTest
    with ImplicitSender
    with AvroSerializers
    with UserServiceComponent
    with CrmServiceComponent {
  override val kafkaName: String = "kafkacrmcontacteventconsumer"
  override val registryName: String = "registrycrmcontacteventconsumer"
  override val zookeeperName: String = "zookeepercrmcontacteventconsumer"
  override val kafkaExposedPort: Int = 29097
  override val registryExposedPort: Int = 28087
  override val zookeeperExposedPort: Int = 22187
  override val userService: UserService = mock[UserService]
  override val crmService: CrmService = mock[CrmService]

  implicit def toAnswerWithArguments[T](f: InvocationOnMock => T): Answer[T] =
    (invocation: InvocationOnMock) => f(invocation)
  implicit def toAnswer[T](f: () => T): Answer[T] = (_: InvocationOnMock) => f()

  feature("consume crm contact event") {

    scenario("Reacting to a crm contact event") {

      val probe = TestProbe()
      val consumer = system.actorOf(
        CrmContactEventConsumerActor.props(userService = userService, crmService = crmService),
        "CrmContactNewEvent"
      )
      val format = RecordFormat[CrmContactEventWrapper]
      val schema = SchemaFor[CrmContactEventWrapper]
      val producer = createProducer(schema, format)

      Await.result(KafkaTestConsumerActor.waitUntilReady(consumer), atMost = 2.minutes)

      val nowDate: ZonedDateTime = DateHelper.now()
      val johnDoeUserProfile: Profile = Profile(
        dateOfBirth = None,
        avatarUrl = None,
        profession = None,
        phoneNumber = None,
        description = None,
        twitterId = None,
        facebookId = None,
        googleId = None,
        gender = None,
        genderName = None,
        postalCode = None,
        karmaLevel = None,
        locale = None,
        socioProfessionalCategory = None
      )
      val johnDoeUser: User = User(
        userId = UserId("JOHNDOE"),
        email = "john.doe@example.com",
        firstName = Some("John"),
        lastName = Some("Doe"),
        lastIp = None,
        hashedPassword = None,
        enabled = true,
        emailVerified = false,
        lastConnection = DateHelper.now(),
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq.empty,
        country = Country("FR"),
        language = Language("fr"),
        profile = Some(johnDoeUserProfile),
        availableQuestions = Seq.empty
      )

      Given("a crm new contact event to consume with an opt out user")
      val newUserOptOut: User =
        johnDoeUser.copy(userId = UserId("user1"), profile = Some(johnDoeUserProfile.copy(optInNewsletter = false)))
      Mockito
        .when(userService.getUser(ArgumentMatchers.eq(UserId("user1"))))
        .thenReturn(Future.successful(Some(newUserOptOut)))
      Mockito
        .when(crmService.addUsersToUnsubscribeList(ArgumentMatchers.eq(Seq(newUserOptOut))))
        .thenAnswer(() => {
          probe.ref ! "crmService.addUserToUnsubscribeList called"
          Future.successful {}
        })
      val newContactEvent: CrmContactNew = CrmContactNew(id = UserId("user1"), eventDate = nowDate)
      val wrappedNewContactEvent: CrmContactEventWrapper = CrmContactEventWrapper(
        version = MakeSerializable.V1,
        id = "some-event",
        date = nowDate,
        eventType = "CrmContactNewEvent",
        event = CrmContactEventWrapper.wrapEvent(newContactEvent)
      )

      When("I send crm contact event")
      producer.send(new ProducerRecord[String, CrmContactEventWrapper]("crm-contact", wrappedNewContactEvent))

      Then("message is consumed and crmService is called to update data")
      probe.expectMsg(500.millis, "crmService.addUserToUnsubscribeList called")

      Given("a crm new contact event to consume with an opt-in user")
      val newUserOptIn: User =
        johnDoeUser.copy(userId = UserId("user2"), profile = Some(johnDoeUserProfile.copy(optInNewsletter = true)))
      Mockito
        .when(userService.getUser(ArgumentMatchers.eq(UserId("user2"))))
        .thenReturn(Future.successful(Some(newUserOptIn)))
      Mockito
        .when(crmService.addUsersToOptInList(ArgumentMatchers.eq(Seq(newUserOptIn))))
        .thenAnswer(() => {
          probe.ref ! "crmService.addUserToOptInList called"
          Future.successful {}
        })
      val newOptinContactEvent: CrmContactNew = CrmContactNew(id = UserId("user2"), eventDate = nowDate)
      val wrappedNewOptinContactEvent: CrmContactEventWrapper = CrmContactEventWrapper(
        version = MakeSerializable.V1,
        id = "some-event",
        date = nowDate,
        eventType = "CrmContactNewEvent",
        event = CrmContactEventWrapper.wrapEvent(newOptinContactEvent)
      )

      When("I send crm contact event")
      producer.send(new ProducerRecord[String, CrmContactEventWrapper]("crm-contact", wrappedNewOptinContactEvent))

      Then("message is consumed and crmService is called to update data")
      probe.expectMsg(500.millis, "crmService.addUserToOptInList called")

      Given("a crm hard bounce contact event to consume")
      val hardBounceUser: User = johnDoeUser.copy(userId = UserId("user3"))
      Mockito
        .when(userService.getUser(ArgumentMatchers.eq(UserId("user3"))))
        .thenReturn(Future.successful(Some(hardBounceUser)))
      Mockito
        .when(crmService.removeUserFromOptInList(ArgumentMatchers.eq(hardBounceUser)))
        .thenAnswer(() => {
          probe.ref ! "crmService.removeUserFromOptInList called"
          Future.successful {}
        })
      Mockito
        .when(crmService.removeUserFromUnsubscribeList(ArgumentMatchers.eq(hardBounceUser)))
        .thenAnswer(() => {
          probe.ref ! "crmService.removeUserFromUnsubscribeList called"
          Future.successful {}
        })
      Mockito
        .when(crmService.addUserToHardBounceList(ArgumentMatchers.eq(hardBounceUser)))
        .thenAnswer(() => {
          probe.ref ! "crmService.addUserToHardBounceList called"
          Future.successful {}
        })
      val hardBounceContactEvent: CrmContactHardBounce = CrmContactHardBounce(id = UserId("user3"), eventDate = nowDate)
      val wrappedHardBounceContactEvent: CrmContactEventWrapper = CrmContactEventWrapper(
        version = MakeSerializable.V1,
        id = "some-event",
        date = nowDate,
        eventType = "CrmContactHardBounce",
        event = CrmContactEventWrapper.wrapEvent(hardBounceContactEvent)
      )

      When("I send crm hard bounce event")
      producer.send(new ProducerRecord[String, CrmContactEventWrapper]("crm-contact", wrappedHardBounceContactEvent))

      Then("message is consumed and crmService is called to update data")
      probe.expectMsg(500.millis, "crmService.removeUserFromOptInList called")
      probe.expectMsg(500.millis, "crmService.removeUserFromUnsubscribeList called")
      probe.expectMsg(500.millis, "crmService.addUserToHardBounceList called")

      Given("a crm unsubscribe event to consume")
      val unsubscribeUser: User = johnDoeUser.copy(userId = UserId("user4"))
      Mockito
        .when(userService.getUser(ArgumentMatchers.eq(UserId("user4"))))
        .thenReturn(Future.successful(Some(unsubscribeUser)))
      Mockito
        .when(crmService.removeUserFromOptInList(ArgumentMatchers.eq(unsubscribeUser)))
        .thenAnswer(() => {
          probe.ref ! "crmService.removeUserFromOptInList unsubscribe called"
          Future.successful {}
        })
      Mockito
        .when(crmService.addUserToUnsubscribeList(ArgumentMatchers.eq(unsubscribeUser)))
        .thenAnswer(() => {
          probe.ref ! "crmService.addUserToUnsubscribeList unsubscribe called"
          Future.successful {}
        })
      val unsubscribeContactEvent: CrmContactUnsubscribe =
        CrmContactUnsubscribe(id = UserId("user4"), eventDate = nowDate)
      val wrappedUnsubscribeContactEvent: CrmContactEventWrapper = CrmContactEventWrapper(
        version = MakeSerializable.V1,
        id = "some-event",
        date = nowDate,
        eventType = "CrmContactUnsubscribe",
        event = CrmContactEventWrapper.wrapEvent(unsubscribeContactEvent)
      )

      When("I send crm unsubscribe event")
      producer.send(new ProducerRecord[String, CrmContactEventWrapper]("crm-contact", wrappedUnsubscribeContactEvent))

      Then("message is consumed and crmService is called to update data")
      probe.expectMsg(500.millis, "crmService.removeUserFromOptInList unsubscribe called")
      probe.expectMsg(500.millis, "crmService.addUserToUnsubscribeList unsubscribe called")

      Given("a crm subscribe event to consume")
      val subscribeUser: User = johnDoeUser.copy(userId = UserId("user5"))
      Mockito
        .when(userService.getUser(ArgumentMatchers.eq(UserId("user5"))))
        .thenReturn(Future.successful(Some(subscribeUser)))
      Mockito
        .when(crmService.removeUserFromUnsubscribeList(ArgumentMatchers.eq(subscribeUser)))
        .thenAnswer(() => {
          probe.ref ! "crmService.removeUserFromUnsubscribeList subscribe called"
          Future.successful {}
        })
      Mockito
        .when(crmService.addUsersToOptInList(ArgumentMatchers.eq(Seq(subscribeUser))))
        .thenAnswer(() => {
          probe.ref ! "crmService.addUserToUnsubscribeList unsubscribe called"
          Future.successful {}
        })
      val subscribeContactEvent: CrmContactSubscribe =
        CrmContactSubscribe(id = UserId("user5"), eventDate = nowDate)
      val wrappedSubscribeContactEvent: CrmContactEventWrapper = CrmContactEventWrapper(
        version = MakeSerializable.V1,
        id = "some-event",
        date = nowDate,
        eventType = "CrmContactSubscribe",
        event = CrmContactEventWrapper.wrapEvent(subscribeContactEvent)
      )

      When("I send crm subscribe event")
      producer.send(new ProducerRecord[String, CrmContactEventWrapper]("crm-contact", wrappedSubscribeContactEvent))

      Then("message is consumed and crmService is called to update data")
      probe.expectMsg(500.millis, "crmService.removeUserFromUnsubscribeList subscribe called")
      probe.expectMsg(500.millis, "crmService.addUserToUnsubscribeList unsubscribe called")

      Given("a crm subscribe event to consume with a user marked as hard bounce")
      val subscribeHardBounceUser: User = johnDoeUser.copy(userId = UserId("user6"), isHardBounce = true)
      Mockito
        .when(userService.getUser(ArgumentMatchers.eq(UserId("user6"))))
        .thenReturn(Future.successful(Some(subscribeHardBounceUser)))
      Mockito
        .when(crmService.removeUserFromUnsubscribeList(ArgumentMatchers.eq(subscribeHardBounceUser)))
        .thenAnswer(() => {
          probe.ref ! "crmService.removeUserFromUnsubscribeList subscribe hard bounce called"
          Future.successful {}
        })
      Mockito
        .when(crmService.addUserToOptInList(ArgumentMatchers.eq(subscribeHardBounceUser)))
        .thenAnswer(() => {
          probe.ref ! "crmService.addUserToUnsubscribeList subscribe hard bounce called"
          Future.successful {}
        })
      val subscribeHardBounceContactEvent: CrmContactSubscribe =
        CrmContactSubscribe(id = UserId("user6"), eventDate = nowDate)
      val wrappedSubscribeHardBounceContactEvent: CrmContactEventWrapper = CrmContactEventWrapper(
        version = MakeSerializable.V1,
        id = "some-event",
        date = nowDate,
        eventType = "CrmContactSubscribe",
        event = CrmContactEventWrapper.wrapEvent(subscribeHardBounceContactEvent)
      )

      When("I send crm subscribe event")
      producer.send(
        new ProducerRecord[String, CrmContactEventWrapper]("crm-contact", wrappedSubscribeHardBounceContactEvent)
      )

      Then("message is consumed and crmService is called to update data")
      probe.expectMsg(500.millis, "crmService.removeUserFromUnsubscribeList subscribe hard bounce called")
      probe.expectNoMessage(500.millis)

      Given("a crm contact update properties event to consume with a user")
      val contactUpdatePropertiesUser: User = johnDoeUser.copy(userId = UserId("user7"))
      Mockito
        .when(userService.getUser(ArgumentMatchers.eq(UserId("user7"))))
        .thenReturn(Future.successful(Some(contactUpdatePropertiesUser)))
      Mockito
        .when(crmService.updateUserProperties(ArgumentMatchers.eq(contactUpdatePropertiesUser)))
        .thenAnswer(() => {
          probe.ref ! "crmService.updateUserProperties called"
          Future.successful {}
        })
      val updateCrmContactUpdatePropertiesEvent: CrmContactUpdateProperties =
        CrmContactUpdateProperties(id = contactUpdatePropertiesUser.userId, eventDate = DateHelper.now())
      val wrappedCrmContactUpdatePropertiesEvent: CrmContactEventWrapper = CrmContactEventWrapper(
        version = MakeSerializable.V1,
        id = "some-event",
        date = nowDate,
        eventType = "CrmContactUpdatePropertiesEvent",
        event = CrmContactEventWrapper.wrapEvent(updateCrmContactUpdatePropertiesEvent)
      )

      When("I send an update properties contact event")

      producer.send(
        new ProducerRecord[String, CrmContactEventWrapper]("crm-contact", wrappedCrmContactUpdatePropertiesEvent)
      )

      Then("message is consumed and crmService is called to update data")
      probe.expectMsg(500.millis, "crmService.updateUserProperties called")

      consumer ! PoisonPill
      producer.close()

      Thread.sleep(2000)
    }
  }

}

object CrmContactEventConsumerActorIt {
  val configuration: String =
    """
      |akka.log-dead-letters-during-shutdown = off
      |make-api {
      |   mail-jet {
      |    url = "mailjeturl"
      |    http-buffer-size = 5
      |    api-key = "apikey"
      |    secret-key = "secretkey"
      |    basic-auth-login = "basicauthlogin"
      |    basic-auth-password = "basicauthpassword"
      |    campaign-api-key = "campaignapikey"
      |    campaign-secret-key = "campaignsecretkey"
      |
      |
      |    user-list {
      |      hard-bounce-list-id = "hardbouncelistid"
      |      unsubscribe-list-id = "unsubscribelistid"
      |      opt-in-list-id = "optinlistid"
      |      batch-size = 100
      |    }
      |  }
      |
      |  kafka {
      |    connection-string = "127.0.0.1:29097"
      |    poll-timeout = 1000
      |    schema-registry = "http://localhost:28087"
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
      |      predictions = "predictions"
      |    }
      |  }
      |}
    """.stripMargin

  val actorSystem = ActorSystem("CrmContactEventConsumerIT", ConfigFactory.parseString(configuration))
}
