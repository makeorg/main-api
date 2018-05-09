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
      |    
      |    
      |    user-list {
      |      hard-bounce-list-id = "hardbouncelistid"
      |      unsubscribe-list-id = "unsubscribelistid"
      |      opt-in-list-id = "optinlistid"
      |      batch-size = 100
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

  override def afterAll: Unit = {
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
  }

}
