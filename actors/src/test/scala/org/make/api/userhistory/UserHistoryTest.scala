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

package org.make.api.userhistory
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory
import org.make.api.{ActorSystemTypedComponent, MakeUnitTest, StaminaTestUtils}

class UserHistoryTest extends MakeUnitTest with ActorSystemTypedComponent {
  override implicit val actorSystemTyped: ActorSystem[_] = UserHistoryTest.system

  Feature("source value of register event after deserialization") {
    Scenario("I get 'core' as source value with an event without source in context and action date after 2018-09-01") {
      val eventType = "user-history-registered"
      val registeredEventWithoutContext =
        """{"userId":"a6d005b8-8d7b-425d-a976-ac23847cc15d","context":{"externalId":"1ad17dfe-06e4-4689-8747-ad865c921f81","hostname":"make.org","visitorId":"39165851-9d01-4b24-aa3b-4ca01e36c104","country":"FR","userAgent":"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_3) AppleWebKit/604.5.6 (KHTML, like Gecko) Version/11.0.3 Safari/604.5.6","language":"fr","ipAddress":"82.225.145.221","sessionId":"f4e2f42d-d9d0-41e1-a5ac-95218a4576b8","requestId":"1ad17dfe-06e4-4689-8747-ad865c921f81","detectedCountry":"FR"},"action":{"date":"2018-08-24T17:26:18.711Z","actionType":"register","arguments":{"email":"panamebh@gmail.com","country":"FR","lastName":"Beatbox Hustlers","firstName":"Paname","language":"fr"}}}"""

      val registerEvent: LogRegisterCitizenEvent =
        StaminaTestUtils
          .deserializeEventFromJson[LogRegisterCitizenEvent](eventType, registeredEventWithoutContext)

      registerEvent.requestContext.source should be(Some("core"))
    }

    Scenario(
      "I get 'core' as source value with an event without source in context, action date after 2018-09-01 and V3 version"
    ) {
      val serialized =
        "0x17000000757365722d686973746f72792d72656769737465726564030000007b22757365724964223a2261366430303562382d386437622d343235642d613937362d616332333834376363313564222c22636f6e74657874223a7b2265787465726e616c4964223a2231616431376466652d303665342d343638392d383734372d616438363563393231663831222c22686f73746e616d65223a226d616b652e6f7267222c2276697369746f724964223a2233393136353835312d396430312d346232342d616133622d346361303165333663313034222c22636f756e747279223a224652222c22757365724167656e74223a224d6f7a696c6c612f352e3020284d6163696e746f73683b20496e74656c204d6163204f5320582031305f31335f3329204170706c655765624b69742f3630342e352e3620284b48544d4c2c206c696b65204765636b6f292056657273696f6e2f31312e302e33205361666172692f3630342e352e36222c226c616e6775616765223a226672222c22697041646472657373223a2238322e3232352e3134352e323231222c2273657373696f6e4964223a2266346532663432642d643964302d343165312d613561632d393532313861343537366238222c22726571756573744964223a2231616431376466652d303665342d343638392d383734372d616438363563393231663831222c226465746563746564436f756e747279223a224652227d2c22616374696f6e223a7b2264617465223a22323031382d30382d32345431373a32363a31382e3731315a222c22616374696f6e54797065223a227265676973746572222c22617267756d656e7473223a7b22656d61696c223a2270616e616d65626840676d61696c2e636f6d222c22636f756e747279223a224652222c226c6173744e616d65223a2242656174626f7820487573746c657273222c2266697273744e616d65223a2250616e616d65222c226c616e6775616765223a226672227d7d7d"

      val registerEvent: LogRegisterCitizenEvent =
        StaminaTestUtils.deserializeEventFromHexa[LogRegisterCitizenEvent](serialized)

      StaminaTestUtils.getVersionFromHexa(serialized) should be(3)
      StaminaTestUtils.getEventNameFromHexa(serialized) should be("user-history-registered")
      registerEvent.requestContext.source should be(Some("core"))

    }
  }
}

object UserHistoryTest {
  val configuration: String =
    """
      |akka.actor.serializers {
      |  make-serializer = "org.make.api.technical.MakeEventSerializer"
      |}
      |make-api.security.secure-hash-salt = "salt-secure"
      |make-api.security.secure-vote-salt = "vote-secure"
      |make-api.security.aes-secret-key = "secret-key"
    """.stripMargin

  val system: ActorSystem[Nothing] = {
    val config = ConfigFactory.load(ConfigFactory.parseString(configuration))
    ActorSystem[Nothing](Behaviors.empty, classOf[UserHistoryTest].getSimpleName, config)
  }
}
