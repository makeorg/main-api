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

import java.nio.file.Path
import io.circe.Decoder
import org.make.api.technical.crm.BasicCrmResponse._
import org.make.api.technical.crm.CrmClient.{Account, Marketing, Transactional}
import org.make.api.technical.security.SecurityHelper
import org.make.core.Order

import scala.concurrent.{ExecutionContext, Future}

trait CrmClient {

  def manageContactList(manageContactList: ManageManyContacts, account: Account = Marketing)(
    implicit executionContext: ExecutionContext
  ): Future[ManageManyContactsResponse]

  def manageContactListWithCsv(csvImport: CsvImport, account: Account = Marketing)(
    implicit executionContext: ExecutionContext
  ): Future[ManageContactsWithCsvResponse]

  def sendCsv(listId: String, csv: Path, account: Account = Marketing)(
    implicit executionContext: ExecutionContext
  ): Future[SendCsvResponse]

  def monitorCsvImport(jobId: Long, account: Account = Marketing)(
    implicit executionContext: ExecutionContext
  ): Future[ManageContactsWithCsvResponse]

  def sendEmail(message: SendMessages, account: Account = Transactional)(
    implicit executionContext: ExecutionContext
  ): Future[SendEmailResponse]

  def getUsersInformationMailFromList(
    listId: Option[String] = None,
    sort: Option[String] = None,
    order: Option[Order] = None,
    countOnly: Option[Boolean] = None,
    limit: Int,
    offset: Int = 0,
    account: Account = Marketing
  )(implicit executionContext: ExecutionContext): Future[ContactsResponse]

  def getContactsProperties(offset: Int, limit: Int, account: Account = Marketing)(
    implicit executionContext: ExecutionContext
  ): Future[GetMailjetContactProperties]

  def deleteContactByEmail(email: String, account: Account)(
    implicit executionContext: ExecutionContext
  ): Future[Boolean]

  def deleteContactById(contactId: String, account: Account)(implicit executionContext: ExecutionContext): Future[Unit]
}

object CrmClient {
  sealed trait Account

  case object Marketing extends Account
  case object Transactional extends Account
}

trait CrmClientComponent {
  def crmClient: CrmClient
}

final case class SendEmailResponse(messages: Seq[SentEmail])

object SendEmailResponse {
  implicit val decoder: Decoder[SendEmailResponse] =
    Decoder.forProduct1("Messages")(SendEmailResponse.apply)
}

final case class SendCsvResponse(csvId: Long)

object SendCsvResponse {
  implicit val decoder: Decoder[SendCsvResponse] =
    Decoder.forProduct1("ID")(SendCsvResponse.apply)
}

final case class SentEmail(
  status: String,
  errors: Option[Seq[SendMessageError]],
  customId: String,
  to: Seq[MessageDetails],
  cc: Seq[MessageDetails],
  bcc: Seq[MessageDetails]
) {
  override def toString: String =
    s"SentEmail: (status = $status, errors = ${errors.map(_.mkString("[", ",", "]"))}, customId = $customId, to = ${to
      .mkString("[", ",", "]")}, cc = ${cc.mkString("[", ",", "]")}, bcc = ${bcc.mkString("[", ",", "]")})"
}

object SentEmail {
  implicit val decoder: Decoder[SentEmail] =
    Decoder.forProduct6("Status", "Errors", "CustomID", "To", "Cc", "Bcc")(SentEmail.apply)
}

final case class SendMessageError(
  errorIdentifier: String,
  errorCode: String,
  statusCode: Int,
  errorMessage: String,
  errorRelatedTo: String
) {
  override def toString: String =
    s"SendMessageError: (errorIdentifier = $errorIdentifier, errorCode = $errorCode, " +
      s"statusCode = $statusCode, errorMessage = $errorMessage, errorRelatedTo = $errorRelatedTo)"
}

object SendMessageError {
  implicit val decoder: Decoder[SendMessageError] =
    Decoder.forProduct5("ErrorIdentifier", "ErrorCode", "StatusCode", "ErrorMessage", "ErrorRelatedTo")(
      SendMessageError.apply
    )
}

final case class MessageDetails(email: String, messageUuid: String, messageId: Long, messageHref: String) {
  override def toString: String =
    s"MessageDetails: (email = ${SecurityHelper.anonymizeEmail(email)}, messageUuid = $messageUuid, " +
      s"messageId = $messageId, messageHref = $messageHref)"
}

object MessageDetails {
  implicit val decoder: Decoder[MessageDetails] =
    Decoder.forProduct4("Email", "MessageUUID", "MessageID", "MessageHref")(MessageDetails.apply)
}

final case class BasicCrmResponse[T](count: Int, total: Int, data: Seq[T])

object BasicCrmResponse {
  def createDecoder[T](implicit tDecoder: Decoder[T]): Decoder[BasicCrmResponse[T]] =
    Decoder.forProduct3("Count", "Total", "Data")(BasicCrmResponse.apply)

  type ContactsResponse = BasicCrmResponse[ContactDataResponse]
  type ManageManyContactsResponse = BasicCrmResponse[JobId]
  type GetMailjetContactProperties = BasicCrmResponse[MailjetContactProperties]
  type ManageContactsWithCsvResponse = BasicCrmResponse[CsvImportResponse]

  implicit val contactsResponseDecoder: Decoder[ContactsResponse] = createDecoder[ContactDataResponse]
  implicit val manageManyContactsResponseDecoder: Decoder[ManageManyContactsResponse] = createDecoder[JobId]
  implicit val getMailjetContactPropertiesResponseDecoder: Decoder[GetMailjetContactProperties] =
    createDecoder[MailjetContactProperties]
  implicit val manageContactsWithCsvResponseDecoder: Decoder[ManageContactsWithCsvResponse] =
    createDecoder[CsvImportResponse]

}

final case class CsvImportResponse(jobId: Long, dataId: Long, errorCount: Int, status: String)

object CsvImportResponse {
  implicit val decoder: Decoder[CsvImportResponse] =
    Decoder.forProduct4("ID", "DataID", "Errcount", "Status")(CsvImportResponse.apply)
}

final case class ContactDataResponse(
  isExcludedFromCampaigns: Boolean,
  name: String,
  createdAt: String,
  deliveredCount: Int,
  email: String,
  exclusionFromCampaignsUpdatedAt: String,
  id: Long,
  isOptInPending: Boolean,
  isSpamComplaining: Boolean,
  lastActivityAt: String,
  lastUpdateAt: String
)

object ContactDataResponse {
  implicit val decoder: Decoder[ContactDataResponse] =
    Decoder.forProduct11(
      "IsExcludedFromCampaigns",
      "Name",
      "CreatedAt",
      "DeliveredCount",
      "Email",
      "ExclusionFromCampaignsUpdatedAt",
      "ID",
      "IsOptInPending",
      "IsSpamComplaining",
      "LastActivityAt",
      "LastUpdateAt"
    )(ContactDataResponse.apply)
}

final case class JobId(jobId: Long)

object JobId {
  implicit val decoder: Decoder[JobId] =
    Decoder.forProduct1("JobID")(JobId.apply)
}

final case class MailjetContactProperties(properties: Seq[MailjetProperty], contactId: Long)
object MailjetContactProperties {
  implicit val decoder: Decoder[MailjetContactProperties] =
    Decoder.forProduct2("Data", "ID")(MailjetContactProperties.apply)
}

final case class MailjetProperty(name: String, value: String)
object MailjetProperty {
  implicit val decoder: Decoder[MailjetProperty] = Decoder.forProduct2("Name", "Value")(MailjetProperty.apply)
}
