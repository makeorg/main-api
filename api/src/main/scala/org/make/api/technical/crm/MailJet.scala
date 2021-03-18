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

import com.sksamuel.avro4s
import com.sksamuel.avro4s.{DefaultFieldMapper, RecordFormat, SchemaFor}
import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import org.make.api.technical.security.SecurityHelper
import org.make.core.user.UserId
import org.make.core.{AvroSerializers, Sharded}

import scala.util.matching.Regex

final case class TemplateErrorReporting(email: String, name: Option[String])

object TemplateErrorReporting {
  implicit val encoder: Encoder[TemplateErrorReporting] =
    (reporting: TemplateErrorReporting) => {

      val fields: Seq[(String, Json)] =
        Seq("Email" -> Some(reporting.email.asJson), "Name" -> reporting.name.map(_.asJson)).collect {
          case (name, Some(value)) => name -> value
        }
      Json.obj(fields: _*)
    }
}

final case class SendEmail(
  id: String = "none",
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
  monitoringCategory: Option[String] = None,
  templateErrorReporting: Option[TemplateErrorReporting] = None
) extends Sharded {
  override def toString =
    s"SendEmail: (id = $id, from = $from, subject = $subject, textPart = $textPart, htmlPart = $htmlPart, useTemplateLanguage = $useTemplateLanguage, templateId = $templateId, variables = $variables, recipients = ${recipients
      .map(_.toAnonymizedString)}, headers = $headers, emailId = $emailId, customCampaign = $customCampaign, monitoringCategory = $monitoringCategory)"
}

object SendEmail extends AvroSerializers {

  lazy val schemaFor: SchemaFor[SendEmail] = SchemaFor.gen[SendEmail]
  implicit lazy val avroDecoder: avro4s.Decoder[SendEmail] = avro4s.Decoder.gen[SendEmail]
  implicit lazy val avroEncoder: avro4s.Encoder[SendEmail] = avro4s.Encoder.gen[SendEmail]
  lazy val recordFormat: RecordFormat[SendEmail] =
    RecordFormat[SendEmail](schemaFor.schema(DefaultFieldMapper))

  def create(
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
    monitoringCategory: Option[String] = None,
    templateErrorReporting: Option[TemplateErrorReporting] = None
  ): SendEmail = {

    SendEmail(
      recipients.headOption.map(head => SecurityHelper.anonymizeEmail(head.email)).getOrElse("unknown"),
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
      monitoringCategory,
      templateErrorReporting
    )
  }

  implicit val encoder: Encoder[SendEmail] =
    (sendEmail: SendEmail) => {

      val fields: Seq[(String, Json)] =
        Seq(
          "From" -> sendEmail.from.map(_.asJson),
          "Subject" -> sendEmail.subject.map(_.asJson),
          "TextPart" -> sendEmail.textPart.map(_.asJson),
          "HTMLPart" -> sendEmail.htmlPart.map(_.asJson),
          "TemplateLanguage" -> sendEmail.useTemplateLanguage.map(_.asJson),
          "TemplateID" -> sendEmail.templateId.map(_.asJson),
          "TemplateID" -> sendEmail.templateId.map(_.asJson),
          "To" -> Some(sendEmail.recipients.asJson),
          "Headers" -> sendEmail.headers.map(_.asJson),
          "CustomID" -> sendEmail.emailId.map(_.asJson),
          "CustomCampaign" -> sendEmail.customCampaign.map(_.asJson),
          "MonitoringCategory" -> sendEmail.monitoringCategory.map(_.asJson),
          "TemplateErrorReporting" -> sendEmail.templateErrorReporting.map(_.asJson),
          "Variables" -> sendEmail.variables.flatMap { variables =>
            if (variables.isEmpty) {
              None
            } else {
              Some(variables.asJson)
            }
          }
        ).collect {
          case (name, Some(value)) => name -> value
        }
      Json.obj(fields: _*)
    }
}
final case class SendMessages(messages: Seq[SendEmail], sandboxMode: Option[Boolean])

object SendMessages {

  val SandboxEmail: Regex = "^yopmail\\+([^@]+)@make\\.org$".r

  def sandboxMode(message: SendEmail): Option[Boolean] = {
    if (message.recipients.forall(_.email match {
          case SandboxEmail(_) => true
          case _               => false
        })) {
      Some(true)
    } else {
      None
    }
  }

  def apply(message: SendEmail): SendMessages = {
    SendMessages(Seq(message), sandboxMode(message))
  }

  implicit val encoder: Encoder[SendMessages] = { sendMessages: SendMessages =>
    val fields: Seq[(String, Json)] =
      Seq("Messages" -> Some(sendMessages.messages.asJson), "SandboxMode" -> sendMessages.sandboxMode.map(_.asJson)).collect {
        case (name, Some(value)) => name -> value
      }

    Json.obj(fields: _*)
  }
}

final case class TransactionDetail(
  status: String,
  customId: String,
  to: Seq[EmailDetail],
  cc: Seq[EmailDetail],
  bcc: Seq[EmailDetail]
)

object TransactionDetail {
  implicit val decoder: Decoder[TransactionDetail] =
    Decoder.forProduct5("Status", "CustomID", "To", "Cc", "Bcc")(TransactionDetail.apply)
}

final case class SendResult(sent: Seq[TransactionDetail])

object SendResult {
  implicit val decoder: Decoder[SendResult] = Decoder.forProduct1("Messages")(SendResult.apply)
}

final case class EmailDetail(email: String, messageUUID: String, messageId: Long, messageHref: String)

object EmailDetail {
  implicit val decoder: Decoder[EmailDetail] =
    Decoder.forProduct4("Email", "MessageUUID", "MessageID", "MessageHref")(EmailDetail.apply)
}

final case class Recipient(email: String, name: Option[String] = None, variables: Option[Map[String, String]] = None) {
  def toAnonymizedString: String =
    s"Recipient: (email = ${SecurityHelper.anonymizeEmail(email)}, name = ${name.flatMap(_.headOption)}, variables = $variables)"

  override def toString =
    s"Recipient: (email = $email, name = ${name.flatMap(_.headOption)}, variables = $variables)"
}

object Recipient {
  implicit val encoder: Encoder[Recipient] =
    (recipient: Recipient) => {
      val fields: Seq[(String, Json)] =
        Seq(
          "Email" -> Some(recipient.email.asJson),
          "Name" -> recipient.name.map(_.asJson),
          "Variables" -> recipient.variables.flatMap { values =>
            if (values.isEmpty) {
              None
            } else {
              Some(values.asJson)
            }
          }
        ).collect {
          case (name, Some(value)) => name -> value
        }

      Json.obj(fields: _*)

    }
}

final case class ManageContact(
  email: String,
  name: String,
  action: ManageContactAction,
  properties: Option[ContactProperties] = None
)

object ManageContact {
  implicit val encoder: Encoder[ManageContact] = Encoder.forProduct4("Email", "Name", "Action", "Properties") {
    manageContact: ManageContact =>
      (manageContact.email, manageContact.name, manageContact.action, manageContact.properties)
  }
}

sealed abstract class ManageContactAction(val value: String) extends StringEnumEntry

object ManageContactAction extends StringEnum[ManageContactAction] with StringCirceEnum[ManageContactAction] {

  case object AddForce extends ManageContactAction("addForce")
  case object AddNoForce extends ManageContactAction("addnoforce")
  case object Remove extends ManageContactAction("remove")
  case object Unsubscribe extends ManageContactAction("unsub")

  override def values: IndexedSeq[ManageContactAction] = findValues

}

final case class ImportOptions(dateTimeFormat: String) {
  override def toString: String = {
    s"""{"DateTimeFormat": "$dateTimeFormat","TimezoneOffset": 0,"FieldNames": ${ContactProperties.getCsvHeader}}"""
  }
}

final case class CsvImport(listId: String, csvId: String, method: ManageContactAction, importOptions: String)

object CsvImport {
  implicit val encoder: Encoder[CsvImport] =
    Encoder.forProduct4("ContactsListID", "DataID", "Method", "ImportOptions") { csvImport: CsvImport =>
      (csvImport.listId, csvImport.csvId, csvImport.method, csvImport.importOptions)
    }
}

final case class Contact(email: String, name: Option[String] = None, properties: Option[ContactProperties] = None) {
  def toStringCsv: String = {
    properties match {
      case None       => ""
      case Some(prop) => s"""\"$email\",${prop.toStringCsv}${String.format("%n")}"""
    }
  }
}
object Contact {
  implicit val encoder: Encoder[Contact] = Encoder.forProduct3("Email", "Name", "Properties") { contact: Contact =>
    (contact.email, contact.name, contact.properties)
  }
}

final case class ContactList(listId: String, action: ManageContactAction)
object ContactList {
  implicit val encoder: Encoder[ContactList] = Encoder.forProduct2("ListID", "Action") { contactList: ContactList =>
    (contactList.listId, contactList.action)
  }
}

final case class ManageManyContacts(contacts: Seq[Contact], contactList: Seq[ContactList])
object ManageManyContacts {
  implicit val encoder: Encoder[ManageManyContacts] = Encoder.forProduct2("Contacts", "ContactsLists") {
    manageManyContacts: ManageManyContacts =>
      (manageManyContacts.contacts, manageManyContacts.contactList)
  }
}

final case class ContactProperty[T](name: String, value: Option[T])

object ContactProperty {
  trait ToJson[T] {
    def toJson(obj: T): Json
  }

  implicit val StringToJson: ToJson[String] = Json.fromString(_)
  implicit val IntToJson: ToJson[Int] = Json.fromInt(_)
  implicit val BooleanToJson: ToJson[Boolean] = Json.fromBoolean(_)

  implicit def encoder[T](implicit toJson: ToJson[T]): Encoder[ContactProperty[T]] =
    (contactProperty: ContactProperty[T]) => {
      val fields: Seq[(String, Json)] =
        Seq("Name" -> Some(contactProperty.name.asJson), "Value" -> contactProperty.value.map(toJson.toJson)).collect {
          case (name, Some(value)) => name -> value
        }

      Json.obj(fields: _*)
    }
}

final case class ContactData(data: Seq[ContactProperty[_]])
object ContactData {
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf", "org.wartremover.warts.Throw"))
  implicit val encoder: Encoder[ContactData] = Encoder.forProduct1("Data") { contactData: ContactData =>
    contactData.data.map {
      case a @ ContactProperty(_, None)             => a.asInstanceOf[ContactProperty[String]].asJson
      case a @ ContactProperty(_, Some(_: String))  => a.asInstanceOf[ContactProperty[String]].asJson
      case a @ ContactProperty(_, Some(_: Int))     => a.asInstanceOf[ContactProperty[Int]].asJson
      case a @ ContactProperty(_, Some(_: Boolean)) => a.asInstanceOf[ContactProperty[Boolean]].asJson
      case other =>
        throw new IllegalStateException(s"Unable to convert ${other.toString}")
    }
  }
}

final case class ContactProperties(
  userId: Option[UserId],
  firstName: Option[String],
  postalCode: Option[String],
  dateOfBirth: Option[String],
  emailValidationStatus: Option[Boolean],
  emailHardBounceValue: Option[Boolean],
  unsubscribeStatus: Option[Boolean],
  accountCreationDate: Option[String],
  accountCreationSource: Option[String],
  accountCreationOrigin: Option[String],
  accountCreationSlug: Option[String],
  accountCreationCountry: Option[String],
  accountCreationLocation: Option[String],
  countriesActivity: Option[String],
  lastCountryActivity: Option[String],
  totalProposals: Option[Int],
  totalVotes: Option[Int],
  firstContributionDate: Option[String],
  lastContributionDate: Option[String],
  operationActivity: Option[String],
  sourceActivity: Option[String],
  daysOfActivity: Option[Int],
  daysOfActivity30: Option[Int],
  userType: Option[String],
  accountType: Option[String],
  updatedAt: Option[String],
  daysBeforeDeletion: Option[Int],
  lastActivityDate: Option[String],
  sessionsCount: Option[Int],
  eventsCount: Option[Int]
) {
  def toContactPropertySeq: Seq[ContactProperty[_]] = {
    Field.fieldsList.map { field =>
      ContactProperty(field.fieldName, field.extractValue(this))
    }
  }

  def toStringCsv: String = {
    toContactPropertySeq.map { properties =>
      properties.value match {
        case None             => ""
        case Some(properties) => s"""\"${properties.toString.replace("\"", "\\\"")}\""""
      }
    }.mkString(",")
  }
}

object ContactProperties {
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf", "org.wartremover.warts.Throw"))
  implicit val encoder: Encoder[ContactProperties] =
    (contactProperties: ContactProperties) => {
      Json.fromFields(contactProperties.toContactPropertySeq.map {
        case property @ ContactProperty(_, None)                 => (property.name, None.asJson)
        case property @ ContactProperty(_, Some(value: String))  => (property.name, Some(value).asJson)
        case property @ ContactProperty(_, Some(value: Int))     => (property.name, Some(value).asJson)
        case property @ ContactProperty(_, Some(value: Boolean)) => (property.name, Some(value).asJson)
        case other =>
          throw new IllegalStateException(s"Unable to convert ${other.toString}")
      })
    }

  val getCsvHeader: String = {
    ("email" :+ Field.fieldsList.map(_.fieldName)).map(name => s"""\"$name\"""").mkString("[", ",", "]")
  }
}

final case class Field[T](fieldName: String, extractValue: ContactProperties => Option[T])

object Field {
  val fieldsList: Seq[Field[_]] = {
    Seq(
      Field("UserId", _.userId.map(_.value)),
      Field("Firstname", _.firstName),
      Field("Zipcode", _.postalCode),
      Field("Date_Of_Birth", _.dateOfBirth),
      Field("Email_Validation_Status", _.emailValidationStatus),
      Field("Email_Hardbounce_Status", _.emailHardBounceValue),
      Field("Unsubscribe_Status", _.unsubscribeStatus),
      Field("Account_Creation_Date", _.accountCreationDate),
      Field("Account_creation_source", _.accountCreationSource),
      Field("Account_creation_origin", _.accountCreationOrigin),
      Field("Account_Creation_Operation", _.accountCreationSlug),
      Field("Account_Creation_Country", _.accountCreationCountry),
      Field("Account_Creation_Location", _.accountCreationLocation),
      Field("Countries_activity", _.countriesActivity),
      Field("Last_country_activity", _.lastCountryActivity),
      Field("Total_Number_Proposals", _.totalProposals),
      Field("Total_number_votes", _.totalVotes),
      Field("First_Contribution_Date", _.firstContributionDate),
      Field("Last_Contribution_Date", _.lastContributionDate),
      Field("Operation_activity", _.operationActivity),
      Field("Source_activity", _.sourceActivity),
      Field("Days_of_Activity", _.daysOfActivity),
      Field("Days_of_Activity_30d", _.daysOfActivity30),
      Field("User_type", _.userType),
      Field("Account_type", _.accountType),
      Field("Updated_at", _.updatedAt),
      Field("Days_before_deletion", _.daysBeforeDeletion),
      Field("Last_activity_date", _.lastActivityDate),
      Field("Sessions_count", _.sessionsCount),
      Field("Events_count", _.eventsCount)
    )
  }
}
