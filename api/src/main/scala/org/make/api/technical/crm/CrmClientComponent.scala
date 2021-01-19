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

import java.net.URL
import java.nio.file.Path

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers.stringUnmarshaller
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorAttributes, OverflowStrategy, QueueOfferResult}
import grizzled.slf4j.Logging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.syntax._
import io.circe.{Decoder, Printer}
import org.make.api
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MailJetConfigurationComponent
import org.make.api.technical.crm.BasicCrmResponse._
import org.make.api.technical.security.SecurityHelper
import org.make.core.Order

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

trait CrmClient {

  def manageContactList(manageContactList: ManageManyContacts)(
    implicit executionContext: ExecutionContext
  ): Future[ManageManyContactsResponse]

  def manageContactListWithCsv(csvImport: CsvImport)(
    implicit executionContext: ExecutionContext
  ): Future[ManageContactsWithCsvResponse]

  def sendCsv(listId: String, csv: Path)(implicit executionContext: ExecutionContext): Future[SendCsvResponse]

  def monitorCsvImport(jobId: Long)(implicit executionContext: ExecutionContext): Future[ManageContactsWithCsvResponse]

  def sendEmail(message: SendMessages)(implicit executionContext: ExecutionContext): Future[SendEmailResponse]

  def getUsersInformationMailFromList(
    listId: Option[String] = None,
    sort: Option[String] = None,
    order: Option[Order] = None,
    countOnly: Option[Boolean] = None,
    limit: Int,
    offset: Int = 0
  )(implicit executionContext: ExecutionContext): Future[ContactsResponse]
  def manageContactListJobDetails(jobId: String)(
    implicit executionContext: ExecutionContext
  ): Future[ManageManyContactsJobDetailsResponse]
  def getContactsProperties(offset: Int, limit: Int)(
    implicit executionContext: ExecutionContext
  ): Future[GetMailjetContactProperties]
  def deleteContactByEmail(email: String)(implicit executionContext: ExecutionContext): Future[Boolean]
  def deleteContactById(contactId: String)(implicit executionContext: ExecutionContext): Future[Unit]
}

trait CrmClientComponent {
  def crmClient: CrmClient
}

trait DefaultCrmClientComponent extends CrmClientComponent with ErrorAccumulatingCirceSupport with Logging {
  self: MailJetConfigurationComponent with ActorSystemComponent =>

  override lazy val crmClient: CrmClient = new DefaultCrmClient

  class DefaultCrmClient extends CrmClient {

    lazy val printer: Printer = Printer.noSpaces.copy(dropNullValues = false)
    lazy val url = new URL(mailJetConfiguration.url)
    val httpPort: Int = 443

    lazy val httpFlow: Flow[
      (HttpRequest, Promise[HttpResponse]),
      (Try[HttpResponse], Promise[HttpResponse]),
      Http.HostConnectionPool
    ] =
      Http(actorSystem).cachedHostConnectionPoolHttps[Promise[HttpResponse]](host = url.getHost, port = httpPort)

    private lazy val bufferSize = mailJetConfiguration.httpBufferSize

    lazy val queue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] = Source
      .queue[(HttpRequest, Promise[HttpResponse])](bufferSize = bufferSize, OverflowStrategy.backpressure)
      .via(httpFlow)
      .withAttributes(ActorAttributes.dispatcher(api.mailJetDispatcher))
      .toMat(Sink.foreach {
        case (Success(resp), p) => p.success(resp)
        case (Failure(e), p)    => p.failure(e)
      })(Keep.left)
      .run()

    private lazy val campaignApiAuthorization = Authorization(
      BasicHttpCredentials(mailJetConfiguration.campaignApiKey, mailJetConfiguration.campaignSecretKey)
    )

    private lazy val transactionalApiAuthorization = Authorization(
      BasicHttpCredentials(mailJetConfiguration.apiKey, mailJetConfiguration.secretKey)
    )

    override def manageContactList(
      manageContactList: ManageManyContacts
    )(implicit executionContext: ExecutionContext): Future[ManageManyContactsResponse] = {
      val request =
        HttpRequest(
          method = HttpMethods.POST,
          uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contact/managemanycontacts"),
          headers = immutable.Seq(campaignApiAuthorization),
          entity = HttpEntity(ContentTypes.`application/json`, printer.print(manageContactList.asJson))
        )
      doHttpCall(request).flatMap {
        case HttpResponse(code, _, responseEntity, _) if code.isSuccess() =>
          Unmarshal(responseEntity).to[ManageManyContactsResponse]
        case HttpResponse(StatusCodes.TooManyRequests, _, responseEntity, _) =>
          Unmarshal(responseEntity).to[String].flatMap { response =>
            Future.failed(QuotaExceeded("manageContactList", response))
          }
        case HttpResponse(code, _, entity, _) =>
          Unmarshal(entity).to[String].flatMap { response =>
            Future.failed(CrmClientException.RequestException.ManageContactListException(code, response))
          }
      }
    }

    override def sendCsv(listId: String, csv: Path)(
      implicit executionContext: ExecutionContext
    ): Future[SendCsvResponse] = {
      val request: HttpRequest = HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(s"${mailJetConfiguration.url}/v3/DATA/contactslist/$listId/CSVData/application:octet-stream"),
        headers = immutable.Seq(campaignApiAuthorization),
        entity = HttpEntity(contentType = ContentTypes.`application/octet-stream`, data = FileIO.fromPath(csv))
      )
      doHttpCall(request).flatMap {
        case HttpResponse(code, _, responseEntity, _) if code.isSuccess() =>
          Unmarshal(responseEntity).to[SendCsvResponse]
        case HttpResponse(StatusCodes.TooManyRequests, _, responseEntity, _) =>
          Unmarshal(responseEntity).to[String].flatMap { response =>
            Future.failed(QuotaExceeded("sendCsv", response))
          }
        case HttpResponse(code, _, entity, _) =>
          Unmarshal(entity).to[String].flatMap { response =>
            Future.failed(CrmClientException.RequestException.SendCsvException(code, response))
          }
      }
    }

    override def manageContactListWithCsv(
      csvImport: CsvImport
    )(implicit executionContext: ExecutionContext): Future[ManageContactsWithCsvResponse] = {
      val request: HttpRequest =
        HttpRequest(
          method = HttpMethods.POST,
          uri = Uri(s"${mailJetConfiguration.url}/v3/REST/csvimport"),
          headers = immutable.Seq(campaignApiAuthorization),
          entity = HttpEntity(ContentTypes.`application/json`, printer.print(csvImport.asJson))
        )
      doHttpCall(request).flatMap {
        case HttpResponse(code, _, responseEntity, _) if code.isSuccess() =>
          Unmarshal(responseEntity).to[ManageContactsWithCsvResponse]
        case HttpResponse(StatusCodes.TooManyRequests, _, responseEntity, _) =>
          Unmarshal(responseEntity).to[String].flatMap { response =>
            Future.failed(QuotaExceeded("manageContactListWithCsv", response))
          }
        case HttpResponse(code, _, entity, _) =>
          Unmarshal(entity).to[String].flatMap { response =>
            Future.failed(CrmClientException.RequestException.CsvImportException(code, response))
          }
      }
    }

    override def monitorCsvImport(
      jobId: Long
    )(implicit executionContext: ExecutionContext): Future[ManageContactsWithCsvResponse] = {
      val request: HttpRequest =
        HttpRequest(
          method = HttpMethods.GET,
          uri = Uri(s"${mailJetConfiguration.url}/v3/REST/csvimport/$jobId"),
          headers = immutable.Seq(campaignApiAuthorization)
        )
      doHttpCall(request).flatMap {
        case HttpResponse(code, _, responseEntity, _) if code.isSuccess() =>
          Unmarshal(responseEntity).to[ManageContactsWithCsvResponse]
        case HttpResponse(StatusCodes.TooManyRequests, _, responseEntity, _) =>
          Unmarshal(responseEntity).to[String].flatMap { response =>
            Future.failed(QuotaExceeded("monitorCsvImport", response))
          }
        case HttpResponse(code, _, entity, _) =>
          Unmarshal(entity).to[String].flatMap { response =>
            Future.failed(CrmClientException.RequestException.MonitorCsvImportException(code, response))
          }
      }
    }

    override def sendEmail(
      message: SendMessages
    )(implicit executionContext: ExecutionContext): Future[SendEmailResponse] = {
      val messagesWithErrorHandling = message.copy(messages = message.messages.map(
        _.copy(templateErrorReporting = Some(
          TemplateErrorReporting(
            mailJetConfiguration.errorReportingRecipient,
            Some(mailJetConfiguration.errorReportingRecipientName)
          )
        )
        )
      )
      )

      val request: HttpRequest = HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(s"${mailJetConfiguration.url}/v3.1/send"),
        headers = immutable.Seq(transactionalApiAuthorization),
        entity = HttpEntity(ContentTypes.`application/json`, printer.print(messagesWithErrorHandling.asJson))
      )
      doHttpCall(request).flatMap {
        case HttpResponse(code, _, responseEntity, _) if code.isSuccess() =>
          Unmarshal(responseEntity).to[SendEmailResponse]
        case HttpResponse(StatusCodes.TooManyRequests, _, responseEntity, _) =>
          Unmarshal(responseEntity).to[String].flatMap { response =>
            Future.failed(QuotaExceeded("sendEmail", response))
          }
        case HttpResponse(code, _, entity, _) =>
          Unmarshal(entity).to[String].flatMap { response =>
            Future.failed(CrmClientException.RequestException.SendEmailException(code, response))
          }
      }
    }

    override def getUsersInformationMailFromList(
      listId: Option[String] = None,
      sort: Option[String] = None,
      order: Option[Order] = None,
      countOnly: Option[Boolean] = None,
      limit: Int,
      offset: Int = 0
    )(implicit executionContext: ExecutionContext): Future[ContactsResponse] = {
      val sortQuery: Option[String] = (sort, order) match {
        case (Some(s), Some(o)) => Some(s"$s+$o")
        case (Some(s), _)       => Some(s)
        case _                  => None
      }
      val params: Map[String, Option[String]] = Map(
        "ContactsList" -> listId,
        "Sort" -> sortQuery,
        "Limit" -> Some(limit.toString),
        "Offset" -> Some(offset.toString),
        "countOnly" -> countOnly.map(bool => if (bool) "1" else "0")
      )
      val paramsQuery = params.collect { case (k, Some(v)) => s"$k=$v" }.mkString("&")
      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contact?$paramsQuery"),
        headers = immutable.Seq(campaignApiAuthorization)
      )
      doHttpCall(request).flatMap {
        case HttpResponse(code, _, responseEntity, _) if code.isSuccess() =>
          Unmarshal(responseEntity).to[ContactsResponse]
        case HttpResponse(StatusCodes.TooManyRequests, _, responseEntity, _) =>
          Unmarshal(responseEntity).to[String].flatMap { response =>
            Future.failed(QuotaExceeded("getUsersInformationMailFromList", response))
          }
        case HttpResponse(code, _, entity, _) =>
          Unmarshal(entity).to[String].flatMap { response =>
            Future.failed(CrmClientException.RequestException.GetUsersInformationMailFromListException(code, response))
          }
      }
    }

    override def getContactsProperties(offset: Int, limit: Int)(
      implicit executionContext: ExecutionContext
    ): Future[GetMailjetContactProperties] = {
      val paramsQuery = s"Limit=$limit&Offset=$offset"
      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contactdata?$paramsQuery"),
        headers = immutable.Seq(campaignApiAuthorization)
      )
      doHttpCall(request).flatMap {
        case HttpResponse(code, _, entity, _) if code.isSuccess() =>
          Unmarshal(entity).to[GetMailjetContactProperties]
        case HttpResponse(StatusCodes.TooManyRequests, _, responseEntity, _) =>
          Unmarshal(responseEntity).to[String].flatMap { response =>
            Future.failed(QuotaExceeded("getContactsProperties", response))
          }
        case HttpResponse(code, _, entity, _) =>
          Unmarshal(entity).to[String].flatMap { response =>
            Future.failed(CrmClientException.RequestException.GetContactsPropertiesException(code, response))
          }
      }
    }

    override def manageContactListJobDetails(
      jobId: String
    )(implicit executionContext: ExecutionContext): Future[ManageManyContactsJobDetailsResponse] = {
      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contact/managemanycontacts/$jobId"),
        headers = immutable.Seq(campaignApiAuthorization)
      )
      doHttpCall(request).flatMap {
        case HttpResponse(code, _, responseEntity, _) if code.isSuccess() =>
          Unmarshal(responseEntity).to[ManageManyContactsJobDetailsResponse]
        case HttpResponse(StatusCodes.TooManyRequests, _, responseEntity, _) =>
          Unmarshal(responseEntity).to[String].flatMap { response =>
            Future.failed(QuotaExceeded("manageContactListJobDetails", response))
          }
        case HttpResponse(code, _, entity, _) =>
          Unmarshal(entity).to[String].flatMap { response =>
            Future.failed(CrmClientException.RequestException.ManageContactListJobDetailsException(code, response))
          }
      }
    }

    private def getContactByMailOrContactId(
      identifier: String
    )(implicit executionContext: ExecutionContext): Future[ContactsResponse] = {
      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contact/$identifier"),
        headers = immutable.Seq(campaignApiAuthorization)
      )
      doHttpCall(request).flatMap {
        case HttpResponse(code, _, entity, _) if code.isSuccess() =>
          Unmarshal(entity).to[ContactsResponse]
        case HttpResponse(StatusCodes.TooManyRequests, _, responseEntity, _) =>
          Unmarshal(responseEntity).to[String].flatMap { response =>
            Future.failed(QuotaExceeded("getContactByMailOrContactId", response))
          }
        case HttpResponse(code, _, entity, _) =>
          Unmarshal(entity).to[String].flatMap { response =>
            Future.failed(CrmClientException.RequestException.GetUsersMailFromListException(code, response))
          }
      }
    }

    override def deleteContactById(contactId: String)(implicit executionContext: ExecutionContext): Future[Unit] = {
      val request = HttpRequest(
        method = HttpMethods.DELETE,
        uri = Uri(s"${mailJetConfiguration.url}/v4/contacts/$contactId"),
        headers = immutable.Seq(campaignApiAuthorization)
      )
      doHttpCall(request).flatMap {
        case HttpResponse(code, _, entity, _) if code.isSuccess() =>
          entity.discardBytes()
          Future.unit
        case HttpResponse(StatusCodes.TooManyRequests, _, responseEntity, _) =>
          Unmarshal(responseEntity).to[String].flatMap { response =>
            Future.failed(QuotaExceeded("deleteContactById", response))
          }
        case HttpResponse(code, _, entity, _) =>
          Unmarshal(entity).to[String].flatMap { response =>
            Future.failed(CrmClientException.RequestException.DeleteUserException(code, response))
          }
      }
    }

    override def deleteContactByEmail(email: String)(implicit executionContext: ExecutionContext): Future[Boolean] = {
      getContactByMailOrContactId(email).flatMap {
        case BasicCrmResponse(_, _, head +: _) =>
          val id = head.id.toString
          val foundEmail = head.email
          deleteContactById(id).flatMap { _ =>
            getContactByMailOrContactId(id).map(anon => anon.data.headOption.forall(_.email != foundEmail))
          }
        case _ => Future.successful(false)
      }.recoverWith {
        case e: CrmClientException =>
          logger.error(e.message)
          Future.successful(false)
        case e => Future.failed(e)
      }
    }

    private def doHttpCall(request: HttpRequest)(implicit executionContext: ExecutionContext): Future[HttpResponse] = {
      val promise = Promise[HttpResponse]()
      queue.offer((request, promise)).flatMap {
        case QueueOfferResult.Enqueued    => promise.future
        case QueueOfferResult.Dropped     => Future.failed(CrmClientException.QueueException.QueueOverflowed)
        case QueueOfferResult.Failure(ex) => Future.failed(ex)
        case QueueOfferResult.QueueClosed => Future.failed(CrmClientException.QueueException.QueueClosed)
      }
    }
  }
}

final case class QuotaExceeded(method: String, message: String) extends Exception(message)

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
  override def toString =
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
  override def toString =
    s"SendMessageError: (errorIdentifier = $errorIdentifier, errorCode = $errorCode, statusCode = $statusCode, errorMessage = $errorMessage, errorRelatedTo = $errorRelatedTo)"
}

object SendMessageError {
  implicit val decoder: Decoder[SendMessageError] =
    Decoder.forProduct5("ErrorIdentifier", "ErrorCode", "StatusCode", "ErrorMessage", "ErrorRelatedTo")(
      SendMessageError.apply
    )
}

final case class MessageDetails(email: String, messageUuid: String, messageId: Long, messageHref: String) {
  override def toString =
    s"MessageDetails: (email = ${SecurityHelper.anonymizeEmail(email)}, messageUuid = $messageUuid, messageId = $messageId, messageHref = $messageHref)"
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
  type ManageManyContactsJobDetailsResponse = BasicCrmResponse[JobDetailsResponse]
  type GetMailjetContactProperties = BasicCrmResponse[MailjetContactProperties]
  type ManageContactsWithCsvResponse = BasicCrmResponse[CsvImportResponse]

  implicit val contactsResponseDecoder: Decoder[ContactsResponse] = createDecoder[ContactDataResponse]
  implicit val manageManyContactsResponseDecoder: Decoder[ManageManyContactsResponse] = createDecoder[JobId]
  implicit val manageManyContactsJobDetailsResponseDecoder: Decoder[ManageManyContactsJobDetailsResponse] =
    createDecoder[JobDetailsResponse]
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

final case class JobDetailsResponse(
  contactsLists: Seq[ContactListAndAction],
  count: Int,
  error: String,
  errorFile: String,
  jobEnd: String,
  jobStart: String,
  status: String
)

object JobDetailsResponse {
  implicit val decoder: Decoder[JobDetailsResponse] =
    Decoder.forProduct7("ContactsLists", "Count", "Error", "ErrorFile", "JobEnd", "JobStart", "Status")(
      JobDetailsResponse.apply
    )
}

final case class ContactListAndAction(listId: Long, action: String)

object ContactListAndAction {
  implicit val decoder: Decoder[ContactListAndAction] =
    Decoder.forProduct2("ListID", "Action")(ContactListAndAction.apply)
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

sealed abstract class CrmClientException(val message: String) extends Exception(message) with Product with Serializable

object CrmClientException {

  sealed abstract class QueueException(message: String) extends CrmClientException(message)

  sealed abstract class RequestException(action: String, code: StatusCode, response: String)
      extends CrmClientException(s"$action failed with status $code: $response")

  object QueueException {

    case object QueueOverflowed extends QueueException("Queue overflowed. Try again later.")

    case object QueueClosed
        extends QueueException("Queue was closed (pool shut down) while running the request. Try again later.")

  }

  object RequestException {

    final case class ManageContactListException(code: StatusCode, response: String)
        extends RequestException("manageContactList", code, response)

    final case class SendCsvException(code: StatusCode, response: String)
        extends RequestException("send csv", code, response)

    final case class CsvImportException(code: StatusCode, response: String)
        extends RequestException("csv import", code, response)

    final case class MonitorCsvImportException(code: StatusCode, response: String)
        extends RequestException("monitor csv import", code, response)

    final case class SendEmailException(code: StatusCode, response: String)
        extends RequestException("send email", code, response)

    final case class GetUsersInformationMailFromListException(code: StatusCode, response: String)
        extends RequestException("getUsersInformationMailFromList", code, response)

    final case class GetContactsPropertiesException(code: StatusCode, response: String)
        extends RequestException("getContactsProperties", code, response)

    final case class ManageContactListJobDetailsException(code: StatusCode, response: String)
        extends RequestException("manageContactListJobDetails", code, response)

    final case class GetUsersMailFromListException(code: StatusCode, response: String)
        extends RequestException("getUsersMailFromList", code, response)

    final case class DeleteUserException(code: StatusCode, response: String)
        extends RequestException("manageContactList", code, response)

  }

}
