package org.make.api.technical.mailjet

import io.circe.{Decoder, Encoder}
import org.make.core.Sharded

case class SendEmail(id: String = "unknown",
                     from: Option[Recipient] = None,
                     subject: Option[String] = None,
                     textPart: Option[String] = None,
                     htmlPart: Option[String] = None,
                     useTemplateLanguage: Option[Boolean] = Some(true),
                     templateId: Option[Int] = None,
                     variables: Option[Map[String, String]] = None,
                     recipients: Seq[Recipient],
                     headers: Option[Map[String, String]] = None,
                     emailId: Option[String] = None,
                     customCampaign: Option[String] = None,
                     monitoringCategory: Option[String] = None)
    extends Sharded

object SendEmail {

  def create(from: Option[Recipient] = None,
             subject: Option[String] = None,
             textPart: Option[String] = None,
             htmlPart: Option[String] = None,
             useTemplateLanguage: Option[Boolean] = Some(true),
             templateId: Option[Int] = None,
             variables: Option[Map[String, String]] = None,
             recipients: Seq[Recipient],
             headers: Option[Map[String, String]] = None,
             emailId: Option[String] = None,
             customCampaign: Option[String] = None,
             monitoringCategory: Option[String] = None): SendEmail = {

    SendEmail(
      recipients.headOption.map(_.email).getOrElse("unknown"),
      from,
      subject,
      textPart,
      htmlPart,
      useTemplateLanguage,
      templateId,
      variables,
      recipients,
      headers,
      emailId,
      customCampaign,
      monitoringCategory
    )
  }

  implicit val encoder: Encoder[SendEmail] = Encoder.forProduct12(
    "From",
    "Subject",
    "TextPart",
    "HTMLPart",
    "TemplateLanguage",
    "TemplateID",
    "Variables",
    "To",
    "Headers",
    "CustomID",
    "CustomCampaign",
    "MonitoringCategory"
  ) { sendEmail =>
    (
      sendEmail.from,
      sendEmail.subject,
      sendEmail.textPart,
      sendEmail.htmlPart,
      sendEmail.useTemplateLanguage,
      sendEmail.templateId,
      sendEmail.variables,
      sendEmail.recipients,
      sendEmail.headers,
      sendEmail.emailId,
      sendEmail.customCampaign,
      sendEmail.monitoringCategory
    )
  }
}

case class SendMessages(messages: Seq[SendEmail])

object SendMessages {
  def apply(message: SendEmail): SendMessages = SendMessages(Seq(message))
  implicit val encoder: Encoder[SendMessages] = Encoder.forProduct1("Messages")(sendMessages => sendMessages.messages)
}

case class TransactionDetail(status: String,
                             customId: String,
                             to: Seq[EmailDetail],
                             cc: Seq[EmailDetail],
                             bcc: Seq[EmailDetail])

object TransactionDetail {
  implicit val decoder: Decoder[TransactionDetail] =
    Decoder.forProduct5("Status", "CustomID", "To", "Cc", "Bcc")(TransactionDetail.apply)
}

case class SendResult(sent: Seq[TransactionDetail])

object SendResult {
  implicit val decoder: Decoder[SendResult] = Decoder.forProduct1("Messages")(SendResult.apply)
}

case class EmailDetail(email: String, messageUUID: String, messageId: Long, messageHref: String)

object EmailDetail {
  implicit val decoder: Decoder[EmailDetail] =
    Decoder.forProduct4("Email", "MessageUUID", "MessageID", "MessageHref")(EmailDetail.apply)
}

case class Recipient(email: String, name: Option[String] = None, variables: Option[Map[String, String]] = None)

object Recipient {
  implicit val encoder: Encoder[Recipient] = Encoder.forProduct3("Email", "Name", "Variables") { recipient =>
    (recipient.email, recipient.name, recipient.variables)
  }
}
