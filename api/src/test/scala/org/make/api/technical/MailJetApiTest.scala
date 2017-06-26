package org.make.api.technical

import io.circe._
import org.make.api.technical.mailjet.MailJetEvent
import org.scalatest.{FlatSpec, Matchers}

class MailJetApiTest extends FlatSpec with Matchers {

  "callback requests" should "be parsed" in {

    val request =
      """
        |[
        |   {
        |      "event": "sent",
        |      "time": 1433333949,
        |      "MessageID": 19421777835146490,
        |      "email": "api@mailjet.com",
        |      "mj_campaign_id": 7257,
        |      "mj_contact_id": 4,
        |      "customcampaign": "",
        |      "mj_message_id": "19421777835146490",
        |      "smtp_reply": "sent (250 2.0.0 OK 1433333948 fa5si855896wjc.199 - gsmtp)",
        |      "CustomID": "helloworld",
        |      "Payload": ""
        |   },
        |   {
        |      "event": "sent",
        |      "time": 1433333949,
        |      "MessageID": 19421777835146491,
        |      "email": "api@mailjet.com",
        |      "mj_campaign_id": 7257,
        |      "mj_contact_id": 4,
        |      "customcampaign": "",
        |      "mj_message_id": "19421777835146491",
        |      "smtp_reply": "sent (250 2.0.0 OK 1433333948 fa5si855896wjc.199 - gsmtp)",
        |      "CustomID": "helloworld",
        |      "Payload": ""
        |   }
        |]
        |
      """.stripMargin

    val maybeJson = jawn.parse(request)

    val parseResult = maybeJson match {
      case Right(json) => json.as[Seq[MailJetEvent]]
      case Left(e)     => fail("unable to parse json", e)
    }

    val events = parseResult match {
      case Right(seq) => seq
      case Left(e)    => fail("unable to decode json", e)
    }

    events.size should be(2)

    events.head should be(
      MailJetEvent(
        event = "sent",
        time = Some(1433333949L),
        messageId = Some(19421777835146490L),
        email = "api@mailjet.com",
        campaignId = Some(7257),
        contactId = Some(4),
        customCampaign = Some(""),
        stringMessageId = Some("19421777835146490"),
        smtpReply = Some("sent (250 2.0.0 OK 1433333948 fa5si855896wjc.199 - gsmtp)"),
        customId = Some("helloworld"),
        payload = Some("")
      )
    )

  }

}
