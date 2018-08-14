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

import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import org.make.core.Sharded
import org.make.core.user.UserId
import spray.json.{JsString, JsValue, JsonFormat}

case class Field(name: String, value: Option[Json]) {
  def toSeq: Seq[(String, Json)] =
    value match {
      case Some(originalValue) => Seq((name, originalValue))
      case None                => Seq.empty
    }
}

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

  implicit val encoder: Encoder[SendEmail] =
    (sendEmail: SendEmail) => {

      val fields: Seq[(String, Json)] =
        Seq.empty ++
          Field("From", sendEmail.from.map(_.asJson)).toSeq ++
          Field("Subject", sendEmail.subject.map(_.asJson)).toSeq ++
          Field("TextPart", sendEmail.textPart.map(_.asJson)).toSeq ++
          Field("HTMLPart", sendEmail.htmlPart.map(_.asJson)).toSeq ++
          Field("TemplateLanguage", sendEmail.useTemplateLanguage.map(_.asJson)).toSeq ++
          Field("TemplateID", sendEmail.templateId.map(_.asJson)).toSeq ++
          Field("TemplateID", sendEmail.templateId.map(_.asJson)).toSeq ++
          Field("To", Some(sendEmail.recipients.asJson)).toSeq ++
          Field("Headers", sendEmail.headers.map(_.asJson)).toSeq ++
          Field("CustomID", sendEmail.emailId.map(_.asJson)).toSeq ++
          Field("CustomCampaign", sendEmail.customCampaign.map(_.asJson)).toSeq ++
          Field("MonitoringCategory", sendEmail.monitoringCategory.map(_.asJson)).toSeq ++
          (sendEmail.variables match {
            case Some(values: Map[String, String]) if values.nonEmpty =>
              Field("Variables", Some(values.asJson)).toSeq
            case _ => Seq.empty
          })

      Json.obj(fields: _*)
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
  implicit val encoder: Encoder[Recipient] =
    (recipient: Recipient) => {
      val fields: Seq[(String, Json)] =
        Seq.empty ++
          Field("Email", Some(recipient.email.asJson)).toSeq ++
          Field("Name", recipient.name.map(_.asJson)).toSeq ++
          (recipient.variables match {
            case Some(values) if values.nonEmpty => Field("Variables", Some(values.asJson)).toSeq
            case _                               => Seq.empty
          })

      Json.obj(fields: _*)

    }
}

case class ManageContact(email: String,
                         name: String,
                         action: ManageContactAction,
                         properties: Option[ContactProperties] = None)

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

case class Contact(email: String, name: String, properties: Option[ContactProperties] = None)
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

case class ContactProperty[T](name: String, value: Option[T])

object ContactProperty {
  trait ToJson[T] {
    def toJson(obj: T): Json
  }

  implicit val StringToJson: ToJson[String] = Json.fromString(_)
  implicit val IntToJson: ToJson[Int] = Json.fromInt(_)
  implicit val BooleanToJson: ToJson[Boolean] = Json.fromBoolean(_)

  implicit def encoder[T](implicit toJson: ToJson[T]): Encoder[ContactProperty[T]] =
    (contactProperty: ContactProperty[T]) => {
      val fields: Seq[(String, Json)] = Field("Name", Some(contactProperty.name.asJson)).toSeq ++
        Field("Value", contactProperty.value.map(toJson.toJson)).toSeq

      Json.obj(fields: _*)
    }
}

case class ContactData(data: Seq[ContactProperty[_]])
object ContactData {
  implicit val encoder: Encoder[ContactData] = Encoder.forProduct1("Data") { contactData: ContactData =>
    contactData.data.map {
      case a @ ContactProperty(_, None)             => a.asInstanceOf[ContactProperty[String]].asJson
      case a @ ContactProperty(_, Some(_: String))  => a.asInstanceOf[ContactProperty[String]].asJson
      case a @ ContactProperty(_, Some(_: Int))     => a.asInstanceOf[ContactProperty[Int]].asJson
      case a @ ContactProperty(_, Some(_: Boolean)) => a.asInstanceOf[ContactProperty[Boolean]].asJson
      case other                                    => throw new IllegalStateException(s"Unable to convert ${other.toString}")
    }
  }
}

case class ContactProperties(userId: Option[UserId],
                             firstName: Option[String],
                             postalCode: Option[String],
                             dateOfBirth: Option[String],
                             emailValidationStatus: Option[Boolean],
                             emailHardBounceValue: Option[Boolean],
                             unsubscribeStatus: Option[Boolean],
                             accountCreationDate: Option[String],
                             accountCreationSource: Option[String],
                             accountCreationOperation: Option[String],
                             accountCreationCountry: Option[String],
                             countriesActivity: Option[String],
                             lastCountryActivity: Option[String],
                             lastLanguageActivity: Option[String],
                             totalProposals: Option[Int],
                             totalVotes: Option[Int],
                             firstContributionDate: Option[String],
                             lastContributionDate: Option[String],
                             operationActivity: Option[String],
                             activeCore: Option[Boolean],
                             daysOfActivity: Option[Int],
                             daysOfActivity30: Option[Int],
                             numberOfThemes: Option[Int],
                             userType: Option[String]) {
  def toContactPropertySeq: Seq[ContactProperty[_]] = {
    Seq(
      ContactProperty("UserId", userId.map(_.value)),
      ContactProperty("Firstname", firstName),
      ContactProperty("Zipcode", postalCode),
      ContactProperty("Date_Of_Birth", dateOfBirth),
      ContactProperty("Email_Validation_Status", emailValidationStatus),
      ContactProperty("Email_Hardbounce_Status", emailHardBounceValue),
      ContactProperty("Unsubscribe_Status", unsubscribeStatus),
      ContactProperty("Account_Creation_Date", accountCreationDate),
      ContactProperty("Account_creation_source", accountCreationSource),
      ContactProperty("Account_Creation_Operation", accountCreationOperation),
      ContactProperty("Account_Creation_Country", accountCreationCountry),
      ContactProperty("Countries_activity", countriesActivity),
      ContactProperty("Last_country_activity", lastCountryActivity),
      ContactProperty("Last_language_activity", lastLanguageActivity),
      ContactProperty("Total_Number_Proposals", totalProposals),
      ContactProperty("Total_number_votes", totalVotes),
      ContactProperty("First_Contribution_Date", firstContributionDate),
      ContactProperty("Last_Contribution_Date", lastContributionDate),
      ContactProperty("Operation_activity", operationActivity),
      ContactProperty("Active_core", activeCore),
      ContactProperty("Days_of_Activity", daysOfActivity),
      ContactProperty("Days_of_Activity_30d", daysOfActivity30),
      ContactProperty("Number_of_themes", numberOfThemes),
      ContactProperty("User_type", userType)
    )
  }
}

object ContactProperties {
  implicit val encoder: Encoder[ContactProperties] =
    (contactProperties: ContactProperties) => {
      Json.obj(
        ("UserId", contactProperties.userId.map(_.value).asJson),
        ("Firstname", contactProperties.firstName.asJson),
        ("Zipcode", contactProperties.postalCode.asJson),
        ("Date_Of_Birth", contactProperties.dateOfBirth.asJson),
        ("Email_Validation_Status", contactProperties.emailValidationStatus.asJson),
        ("Email_Hardbounce_Status", contactProperties.emailHardBounceValue.asJson),
        ("Unsubscribe_Status", contactProperties.unsubscribeStatus.asJson),
        ("Account_Creation_Date", contactProperties.accountCreationDate.asJson),
        ("Account_creation_source", contactProperties.accountCreationSource.asJson),
        ("Account_Creation_Operation", contactProperties.accountCreationOperation.asJson),
        ("Account_Creation_Country", contactProperties.accountCreationCountry.asJson),
        ("Countries_activity", contactProperties.countriesActivity.asJson),
        ("Last_country_activity", contactProperties.lastCountryActivity.asJson),
        ("Last_language_activity", contactProperties.lastLanguageActivity.asJson),
        ("Total_Number_Proposals", contactProperties.totalProposals.asJson),
        ("Total_number_votes", contactProperties.totalVotes.asJson),
        ("First_Contribution_Date", contactProperties.firstContributionDate.asJson),
        ("Last_Contribution_Date", contactProperties.lastContributionDate.asJson),
        ("Operation_activity", contactProperties.operationActivity.asJson),
        ("Active_core", contactProperties.activeCore.asJson),
        ("Days_of_Activity", contactProperties.daysOfActivity.asJson),
        ("Days_of_Activity_30d", contactProperties.daysOfActivity30.asJson),
        ("Number_of_themes", contactProperties.numberOfThemes.asJson),
        ("User_type", contactProperties.userType.asJson)
      )
    }
}
