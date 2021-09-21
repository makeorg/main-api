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
import io.circe.Printer
import org.make.api.extensions.MailJetConfigurationComponent
import org.make.api.technical.ActorSystemComponent
import org.make.api.technical.crm.BasicCrmResponse._
import org.make.api.technical.crm.CrmClient.{Account, Marketing, Transactional}
import org.make.core.Order

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

trait DefaultCrmClientComponent extends CrmClientComponent with ErrorAccumulatingCirceSupport with Logging {
  self: MailJetConfigurationComponent with ActorSystemComponent =>

  override lazy val crmClient: CrmClient = new DefaultCrmClient

  class DefaultCrmClient extends CrmClient {

    lazy val printer: Printer = Printer.noSpaces.copy(dropNullValues = false)
    lazy val url = new URL(mailJetConfiguration.url)
    val httpPort: Int = 443
    type Ctx = Promise[HttpResponse]

    lazy val httpFlow: Flow[(HttpRequest, Ctx), (Try[HttpResponse], Ctx), Http.HostConnectionPool] =
      Http(actorSystem).cachedHostConnectionPoolHttps[Ctx](host = url.getHost, port = httpPort)

    private lazy val bufferSize = mailJetConfiguration.httpBufferSize

    lazy val queue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] = Source
      .queue[(HttpRequest, Promise[HttpResponse])](bufferSize = bufferSize, OverflowStrategy.backpressure)
      .via(httpFlow)
      .withAttributes(ActorAttributes.dispatcher("make-api.mail-jet.dispatcher"))
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

    private def choseAccount(account: Account): Authorization = {
      account match {
        case Marketing     => campaignApiAuthorization
        case Transactional => transactionalApiAuthorization
      }
    }

    override def manageContactList(manageContactList: ManageManyContacts, account: Account)(
      implicit executionContext: ExecutionContext
    ): Future[ManageManyContactsResponse] = {
      val request =
        HttpRequest(
          method = HttpMethods.POST,
          uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contact/managemanycontacts"),
          headers = immutable.Seq(choseAccount(account)),
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

    override def sendCsv(listId: String, csv: Path, account: Account)(
      implicit executionContext: ExecutionContext
    ): Future[SendCsvResponse] = {
      val request: HttpRequest = HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(s"${mailJetConfiguration.url}/v3/DATA/contactslist/$listId/CSVData/application:octet-stream"),
        headers = immutable.Seq(choseAccount(account)),
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

    override def manageContactListWithCsv(csvImport: CsvImport, account: Account)(
      implicit executionContext: ExecutionContext
    ): Future[ManageContactsWithCsvResponse] = {
      val request: HttpRequest =
        HttpRequest(
          method = HttpMethods.POST,
          uri = Uri(s"${mailJetConfiguration.url}/v3/REST/csvimport"),
          headers = immutable.Seq(choseAccount(account)),
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

    override def monitorCsvImport(jobId: Long, account: Account)(
      implicit executionContext: ExecutionContext
    ): Future[ManageContactsWithCsvResponse] = {
      val request: HttpRequest =
        HttpRequest(
          method = HttpMethods.GET,
          uri = Uri(s"${mailJetConfiguration.url}/v3/REST/csvimport/$jobId"),
          headers = immutable.Seq(choseAccount(account))
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

    override def sendEmail(message: SendMessages, account: Account)(
      implicit executionContext: ExecutionContext
    ): Future[SendEmailResponse] = {
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
        headers = immutable.Seq(choseAccount(account)),
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
      offset: Int = 0,
      account: Account
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
        headers = immutable.Seq(choseAccount(account))
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

    override def getContactsProperties(offset: Int, limit: Int, account: Account)(
      implicit executionContext: ExecutionContext
    ): Future[GetMailjetContactProperties] = {
      val paramsQuery = s"Limit=$limit&Offset=$offset"
      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contactdata?$paramsQuery"),
        headers = immutable.Seq(choseAccount(account))
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

    private def getContactByMailOrContactId(identifier: String, account: Account)(
      implicit executionContext: ExecutionContext
    ): Future[ContactsResponse] = {
      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contact/$identifier"),
        headers = immutable.Seq(choseAccount(account))
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

    override def deleteContactById(contactId: String, account: Account)(
      implicit executionContext: ExecutionContext
    ): Future[Unit] = {
      val request = HttpRequest(
        method = HttpMethods.DELETE,
        uri = Uri(s"${mailJetConfiguration.url}/v4/contacts/$contactId"),
        headers = immutable.Seq(choseAccount(account))
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

    override def deleteContactByEmail(email: String, account: Account)(
      implicit executionContext: ExecutionContext
    ): Future[Boolean] = {
      getContactByMailOrContactId(email, account).flatMap {
        case BasicCrmResponse(_, _, head +: _) =>
          val id = head.id.toString
          val foundEmail = head.email
          deleteContactById(id, account).flatMap { _ =>
            getContactByMailOrContactId(id, account).map(anon => anon.data.headOption.forall(_.email != foundEmail))
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
      val defaultLoops = 5

      @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
      def loop(request: HttpRequest, retries: Int): Future[HttpResponse] = {
        val promise = Promise[HttpResponse]()
        queue.offer((request, promise)).flatMap {
          case QueueOfferResult.Enqueued =>
            promise.future.flatMap {
              case HttpResponse(StatusCodes.InternalServerError, _, entity, _) if retries > 0 =>
                Unmarshal(entity).to[String].flatMap { response =>
                  logger.warn(s"Error '$response' from Mailjet, $retries retries left")
                  loop(request, retries - 1)
                }
              case other => Future.successful(other)
            }
          case QueueOfferResult.Dropped     => Future.failed(CrmClientException.QueueException.QueueOverflowed)
          case QueueOfferResult.Failure(ex) => Future.failed(ex)
          case QueueOfferResult.QueueClosed => Future.failed(CrmClientException.QueueException.QueueClosed)
        }
      }
      loop(request, defaultLoops)
    }
  }
}

final case class QuotaExceeded(method: String, message: String) extends Exception(message)

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
        extends RequestException("deleteContactById", code, response)

  }

}
