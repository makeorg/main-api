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

import io.circe.{Decoder, Encoder, Json}
import org.make.core.Sharded
import spray.json.{JsString, JsValue, JsonFormat}

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

case class ManageContact(email: String,
                         name: String,
                         action: ManageContactAction,
                         properties: Option[Map[String, String]] = None)

object ManageContact {
  implicit val encoder: Encoder[ManageContact] = Encoder.forProduct4("Email", "Name", "Action", "Properties") {
    manageContact: ManageContact =>
      (manageContact.email, manageContact.name, manageContact.action, manageContact.properties)
  }
}

sealed trait ManageContactAction {
  def shortName: String
}

object ManageContactAction {
  val actionMap: Map[String, ManageContactAction] =
    Map(
      AddForce.shortName -> AddForce,
      AddNoForce.shortName -> AddNoForce,
      Remove.shortName -> Remove,
      Unsubscribe.shortName -> Unsubscribe
    )

  implicit lazy val manageContactActionEncoder: Encoder[ManageContactAction] =
    (manageContactAction: ManageContactAction) => Json.fromString(manageContactAction.shortName)
  implicit lazy val manageContactActionDecoder: Decoder[ManageContactAction] =
    Decoder.decodeString.emap { value: String =>
      actionMap.get(value) match {
        case Some(manageContactAction) => Right(manageContactAction)
        case None                      => Left(s"$value is not a manage contact action")
      }
    }

  implicit val manageContactActionFormatted: JsonFormat[ManageContactAction] = new JsonFormat[ManageContactAction] {
    override def read(json: JsValue): ManageContactAction = json match {
      case JsString(s) => ManageContactAction.actionMap(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: ManageContactAction): JsValue = {
      JsString(obj.shortName)
    }
  }

  case object AddForce extends ManageContactAction {
    override val shortName = "addForce"
  }

  case object AddNoForce extends ManageContactAction {
    override val shortName = "addnoforce"
  }

  case object Remove extends ManageContactAction {
    override val shortName = "remove"
  }

  case object Unsubscribe extends ManageContactAction {
    override val shortName = "unsub"
  }
}

case class Contact(email: String, name: String, properties: Option[Map[String, String]] = None)
object Contact {
  implicit val encoder: Encoder[Contact] = Encoder.forProduct3("Email", "Name", "Properties") { contact: Contact =>
    (contact.email, contact.name, contact.properties)
  }
}

case class ContactList(listId: String, action: ManageContactAction)
object ContactList {
  implicit val encoder: Encoder[ContactList] = Encoder.forProduct2("ListID", "action") { contactList: ContactList =>
    (contactList.listId, contactList.action)
  }
}

case class ManageManyContacts(contacts: Seq[Contact], contactList: Seq[ContactList])
object ManageManyContacts {
  implicit val encoder: Encoder[ManageManyContacts] = Encoder.forProduct2("Contacts", "ContactsLists") {
    manageManyContacts: ManageManyContacts =>
      (manageManyContacts.contacts, manageManyContacts.contactList)
  }
}

case class ContactProperty(name: String, value: String)
object ContactProperty {
  implicit val encoder: Encoder[ContactProperty] = Encoder.forProduct2("Name", "Value") {
    contactProperty: ContactProperty =>
      (contactProperty.name, contactProperty.value)
  }
}

case class ContactData(data: Seq[ContactProperty])
object ContactData {
  implicit val encoder: Encoder[ContactData] = Encoder.forProduct1("Data") { contactData: ContactData =>
    (contactData.data)
  }
}
