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

package org.make.api.extensions

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FeatureSpecLike, Matchers}

class MailJetConfigurationTest
    extends TestKit(
      ActorSystem(
        "MailJetConfigurationExtensionTest",
        ConfigFactory.parseString("""
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
      |    error-reporting {
      |      recipient = "emailing@make.org"
      |      recipient-name = "emailing"
      |    }
      |    
      |    user-list {
      |      hard-bounce-list-id = "hardbouncelistid"
      |      unsubscribe-list-id = "unsubscribelistid"
      |      opt-in-list-id = "optinlistid"
      |      batch-size = 100
      |      csv-bytes-size = 2097152
      |      csv-directory = "/tmp/make/crm"
      |    }
      |  }
      |}
      """.stripMargin)
      )
    )
    with FeatureSpecLike
    with Matchers
    with MailJetConfigurationComponent
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val mailJetConfiguration: MailJetConfiguration = MailJetConfiguration(system)

  scenario("Register user and create proposal") {
    mailJetConfiguration.apiKey shouldBe ("apikey")
    mailJetConfiguration.basicAuthLogin shouldBe ("basicauthlogin")
    mailJetConfiguration.basicAuthPassword shouldBe ("basicauthpassword")
    mailJetConfiguration.campaignApiKey shouldBe ("campaignapikey")
    mailJetConfiguration.campaignSecretKey shouldBe ("campaignsecretkey")
    mailJetConfiguration.secretKey shouldBe ("secretkey")
    mailJetConfiguration.optInListId shouldBe ("optinlistid")
    mailJetConfiguration.unsubscribeListId shouldBe ("unsubscribelistid")
    mailJetConfiguration.hardBounceListId shouldBe ("hardbouncelistid")
    mailJetConfiguration.url shouldBe ("mailjeturl")
    mailJetConfiguration.userListBatchSize shouldBe (100)
    mailJetConfiguration.httpBufferSize shouldBe (5)
    mailJetConfiguration.csvSize shouldBe (2097152)
    mailJetConfiguration.csvDirectory shouldBe ("/tmp/make/crm")
  }

}
