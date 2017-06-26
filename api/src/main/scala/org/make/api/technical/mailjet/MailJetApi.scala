package org.make.api.technical.mailjet

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import io.circe.Decoder
import org.make.api.technical.{EventBusServiceComponent, MakeDirectives}
import org.make.api.technical.auth.MakeDataHandlerComponent

trait MailJetApi extends MakeDirectives with StrictLogging {
  this: MakeDataHandlerComponent with EventBusServiceComponent =>

  val mailJetRoutes: Route = webHook

  def webHook: Route = {
    post {
      path("mailjet")
      makeTrace("mailjet-webhook") {
        decodeRequest {
          entity(as[Seq[MailJetEvent]]) { events: Seq[MailJetEvent] =>
            // Send all events to event bus
            events.foreach(eventBusService.publish)
            complete(StatusCodes.OK)
          }
        }
      }
    }
  }
}

case class MailJetEvent(event: String,
                        time: Option[Long] = None,
                        messageId: Option[Long] = None,
                        email: String,
                        campaignId: Option[Int] = None,
                        contactId: Option[Int] = None,
                        customCampaign: Option[String] = None,
                        stringMessageId: Option[String] = None,
                        smtpReply: Option[String] = None,
                        customId: Option[String] = None,
                        payload: Option[String] = None)

object MailJetEvent {
  implicit val decoder: Decoder[MailJetEvent] = Decoder.forProduct11(
    "event",
    "time",
    "MessageID",
    "email",
    "mj_campaign_id",
    "mj_contact_id",
    "customcampaign",
    "mj_message_id",
    "smtp_reply",
    "CustomID",
    "Payload"
  )(MailJetEvent.apply)
}
