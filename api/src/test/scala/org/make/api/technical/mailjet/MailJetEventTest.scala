package org.make.api.technical.mailjet

import io.circe
import io.circe.parser.decode
import org.make.api.MakeUnitTest

class MailJetEventTest extends MakeUnitTest {

  feature("deserialize MailJet events") {
    scenario("deserialize a bounce event") {
      Given("a bounce json event from Mailjet")
      When("I deserialize json")
      Then("I get a MailJetBounceEvent")

      val mailJetBounceEvent: Either[circe.Error, MailJetEvent] =
        decode[MailJetEvent]("""
          |{
          |   "event": "bounce",
          |   "time": 1430812195,
          |   "MessageID": 13792286917004336,
          |   "email": "bounce@mailjet.com",
          |   "mj_campaign_id": 0,
          |   "mj_contact_id": 1,
          |   "customcampaign": "custom campaign",
          |   "CustomID": "helloworld",
          |   "Payload": "payload here",
          |   "blocked": true,
          |   "hard_bounce": true,
          |   "error_related_to": "recipient",
          |   "error": "user unknown"
          |}
        """.stripMargin)
      mailJetBounceEvent.isRight shouldBe true
      mailJetBounceEvent should be(
        Right(
          MailJetBounceEvent(
            email = "bounce@mailjet.com",
            time = Some(1430812195L),
            messageId = Some(13792286917004336L),
            campaignId = Some(0),
            contactId = Some(1),
            customCampaign = Some("custom campaign"),
            customId = Some("helloworld"),
            payload = Some("payload here"),
            blocked = true,
            hardBounce = true,
            error = Some(MailJetError.UserUnknown)
          )
        )
      )
    }

    scenario("deserialize a blocked event") {
      Given("a blocked json event from Mailjet")
      When("I deserialize json")
      Then("I get a MailJetBlockedEvent")

      val mailJetBlockedEvent: Either[circe.Error, MailJetEvent] =
        decode[MailJetEvent]("""
                               |{
                               |   "event": "blocked",
                               |   "time": 1430812195,
                               |   "MessageID": 13792286917004336,
                               |   "email": "bounce@mailjet.com",
                               |   "mj_campaign_id": 0,
                               |   "mj_contact_id": 1,
                               |   "customcampaign": "",
                               |   "CustomID": "helloworld",
                               |   "Payload": "",
                               |   "error_related_to": "recipient",
                               |   "error": "user unknown"
                               |}
                             """.stripMargin)
      mailJetBlockedEvent.isRight shouldBe true
      mailJetBlockedEvent should be(
        Right(
          MailJetBlockedEvent(
            email = "bounce@mailjet.com",
            time = Some(1430812195L),
            messageId = Some(13792286917004336L),
            campaignId = Some(0),
            contactId = Some(1),
            customCampaign = Some(""),
            customId = Some("helloworld"),
            payload = Some(""),
            error = Some(MailJetError.UserUnknown)
          )
        )
      )
    }

    scenario("deserialize a spam event") {
      Given("a spam json event from Mailjet")
      When("I deserialize json")
      Then("I get a MailJetSpamEvent")

      val mailJetSpamEvent: Either[circe.Error, MailJetEvent] =
        decode[MailJetEvent]("""
                               |{
                               |   "event": "spam",
                               |   "time": 1430812195,
                               |   "MessageID": 13792286917004336,
                               |   "email": "spam@mailjet.com",
                               |   "mj_campaign_id": 0,
                               |   "mj_contact_id": 1,
                               |   "customcampaign": "",
                               |   "CustomID": "helloworld",
                               |   "Payload": "",
                               |   "source": "JMRPP"
                               |}
                             """.stripMargin)
      mailJetSpamEvent.isRight shouldBe true
      mailJetSpamEvent should be(
        Right(
          MailJetSpamEvent(
            email = "spam@mailjet.com",
            time = Some(1430812195L),
            messageId = Some(13792286917004336L),
            campaignId = Some(0),
            contactId = Some(1),
            customCampaign = Some(""),
            customId = Some("helloworld"),
            payload = Some(""),
            source = Some("JMRPP")
          )
        )
      )
    }

    scenario("deserialize a unsub event") {
      Given("a unsub json event from Mailjet")
      When("I deserialize json")
      Then("I get a MailJetUnsubEvent")

      val mailJetUnsubEvent: Either[circe.Error, MailJetEvent] =
        decode[MailJetEvent](
          """
                               |{
                               |   "event": "unsub",
                               |   "time": 1433334941,
                               |   "MessageID": 20547674933128000,
                               |   "email": "api@mailjet.com",
                               |   "mj_campaign_id": 7276,
                               |   "mj_contact_id": 126,
                               |   "customcampaign": "",
                               |   "CustomID": "helloworld",
                               |   "Payload": "",
                               |   "mj_list_id": 1,
                               |   "ip": "127.0.0.1",
                               |   "geo": "FR",
                               |   "agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/42.0.2311.135 Safari/537.36"
                               |}
                             """.stripMargin
        )
      mailJetUnsubEvent.isRight shouldBe true
      mailJetUnsubEvent should be(
        Right(
          MailJetUnsubscribeEvent(
            email = "api@mailjet.com",
            time = Some(1433334941L),
            messageId = Some(20547674933128000L),
            campaignId = Some(7276),
            contactId = Some(126),
            customCampaign = Some(""),
            customId = Some("helloworld"),
            payload = Some(""),
            listId = Some(1),
            ip = Some("127.0.0.1"),
            geo = Some("FR"),
            agent = Some(
              "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/42.0.2311.135 Safari/537.36"
            )
          )
        )
      )
    }

    scenario("deserialize an unknown Mailjet event") {
      Given("an unknown json event from Mailjet")
      When("I deserialize json")
      Then("I get a MailJetBaseEvent")

      val mailJetBaseEvent: Either[circe.Error, MailJetEvent] =
        decode[MailJetEvent]("""
                               |{
                               |   "event": "click",
                               |   "time": 1433334941,
                               |   "MessageID": 19421777836302490,
                               |   "email": "api@mailjet.com",
                               |   "mj_campaign_id": 7272,
                               |   "mj_contact_id": 4,
                               |   "customcampaign": "",
                               |   "CustomID": "helloworld",
                               |   "Payload": "",
                               |   "url": "https://mailjet.com",
                               |   "ip": "127.0.0.1",
                               |   "geo": "FR",
                               |   "agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_0) AppleWebKit/537.36"
                               |}
                             """.stripMargin)
      mailJetBaseEvent.isRight shouldBe true
      mailJetBaseEvent should be(
        Right(
          MailJetBaseEvent(
            event = "click",
            email = "api@mailjet.com",
            time = Some(1433334941L),
            messageId = Some(19421777836302490L),
            campaignId = Some(7272),
            contactId = Some(4),
            customCampaign = Some(""),
            customId = Some("helloworld"),
            payload = Some("")
          )
        )
      )
    }
  }
}
