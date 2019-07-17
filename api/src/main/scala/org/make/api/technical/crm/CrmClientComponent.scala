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

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorAttributes, ActorMaterializer, OverflowStrategy, QueueOfferResult}
import io.circe._
import io.circe.syntax._
import org.make.api
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MailJetConfigurationComponent
import org.make.api.technical.crm.BasicCrmResponse.{
  ContactsResponse,
  ManageManyContactsJobDetailsResponse,
  ManageManyContactsResponse
}
import org.mdedetrich.akka.http.support.CirceHttpSupport

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

trait CrmClient {

  def manageContactList(manageContactList: ManageManyContacts)(
    implicit executionContext: ExecutionContext
  ): Future[ManageManyContactsResponse]

  def sendEmail(message: SendMessages)(implicit executionContext: ExecutionContext): Future[SendEmailResponse]

  def getUsersInformationMailFromList(listId: String, limit: Int, offset: Int)(
    implicit executionContext: ExecutionContext
  ): Future[ContactsResponse]

  def manageContactListJobDetails(jobId: String)(
    implicit executionContext: ExecutionContext
  ): Future[ManageManyContactsJobDetailsResponse]
}

trait CrmClientComponent {
  def crmClient: CrmClient
}

trait DefaultCrmClientComponent extends CrmClientComponent with CirceHttpSupport {
  self: MailJetConfigurationComponent with ActorSystemComponent =>

  override lazy val crmClient: CrmClient = new DefaultCrmClient

  class DefaultCrmClient extends CrmClient {

    lazy val printer: Printer = Printer.noSpaces.copy(dropNullValues = false)
    lazy val url = new URL(mailJetConfiguration.url)
    val httpPort: Int = 443

    lazy val httpFlow: Flow[(HttpRequest, Promise[HttpResponse]),
                            (Try[HttpResponse], Promise[HttpResponse]),
                            Http.HostConnectionPool] =
      Http(actorSystem).cachedHostConnectionPoolHttps[Promise[HttpResponse]](host = url.getHost, port = httpPort)

    private lazy val bufferSize = mailJetConfiguration.httpBufferSize

    private implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
    lazy val queue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] = Source
      .queue[(HttpRequest, Promise[HttpResponse])](bufferSize = bufferSize, OverflowStrategy.backpressure)
      .via(httpFlow)
      .withAttributes(ActorAttributes.dispatcher(api.mailJetDispatcher))
      .toMat(Sink.foreach {
        case (Success(resp), p) => p.success(resp)
        case (Failure(e), p)    => p.failure(e)
      })(Keep.left)
      .run()(materializer)

    private lazy val authorization = Authorization(
      BasicHttpCredentials(mailJetConfiguration.campaignApiKey, mailJetConfiguration.campaignSecretKey)
    )

    override def manageContactList(
      manageContactList: ManageManyContacts
    )(implicit executionContext: ExecutionContext): Future[ManageManyContactsResponse] = {
      val request =
        HttpRequest(
          method = HttpMethods.POST,
          uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contact/managemanycontacts"),
          headers = immutable.Seq(authorization),
          entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(manageContactList.asJson))
        )
      doHttpCall(request).flatMap {
        case HttpResponse(StatusCodes.Created, _, responseEntity, _) =>
          Unmarshal(responseEntity).to[ManageManyContactsResponse]
        case error => Future.failed(CrmClientException(s"Error when synchronizing contacts: $error"))
      }
    }

    override def sendEmail(
      message: SendMessages
    )(implicit executionContext: ExecutionContext): Future[SendEmailResponse] = {
      val request: HttpRequest = HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(s"${mailJetConfiguration.url}/v3.1/send"),
        headers = immutable
          .Seq(Authorization(BasicHttpCredentials(mailJetConfiguration.apiKey, mailJetConfiguration.secretKey))),
        entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(message.asJson))
      )
      doHttpCall(request).flatMap {
        case HttpResponse(StatusCodes.OK, _, responseEntity, _) =>
          Unmarshal(responseEntity).to[SendEmailResponse]
        case error => Future.failed(CrmClientException(s"Error when sending email: $error"))
      }
    }

    override def getUsersInformationMailFromList(listId: String, limit: Int, offset: Int)(
      implicit executionContext: ExecutionContext
    ): Future[ContactsResponse] = {
      val params = s"ContactsList=$listId&Limit=$limit&Offset=$offset"
      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contact?$params"),
        headers = immutable.Seq(authorization)
      )
      doHttpCall(request).flatMap {
        case HttpResponse(StatusCodes.OK, _, responseEntity, _) =>
          Unmarshal(responseEntity).to[ContactsResponse]
        case error => Future.failed(CrmClientException(s"Error when retrieving contacts: $error"))
      }
    }

    override def manageContactListJobDetails(
      jobId: String
    )(implicit executionContext: ExecutionContext): Future[ManageManyContactsJobDetailsResponse] = {
      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contact/managemanycontacts/$jobId"),
        headers = immutable.Seq(authorization)
      )
      doHttpCall(request).flatMap {
        case HttpResponse(StatusCodes.OK, _, responseEntity, _) =>
          Unmarshal(responseEntity).to[ManageManyContactsJobDetailsResponse]
        case error => Future.failed(CrmClientException(s"Error when retrieving job details: $error"))
      }
    }

    private def doHttpCall(request: HttpRequest)(implicit executionContext: ExecutionContext): Future[HttpResponse] = {
      val promise = Promise[HttpResponse]()
      queue.offer((request, promise)).flatMap {
        case QueueOfferResult.Enqueued    => promise.future
        case QueueOfferResult.Dropped     => Future.failed(CrmClientException("Queue overflowed. Try again later."))
        case QueueOfferResult.Failure(ex) => Future.failed(ex)
        case QueueOfferResult.QueueClosed =>
          Future
            .failed(CrmClientException("Queue was closed (pool shut down) while running the request. Try again later."))
      }
    }
  }
}

case class SendEmailResponse(sent: Seq[SentEmail])

object SendEmailResponse {
  implicit val decoder: Decoder[SendEmailResponse] =
    Decoder.forProduct1("Sent")(SendEmailResponse.apply)
}

case class SentEmail(email: String, messageId: String, messageUuid: String)

object SentEmail {
  implicit val decoder: Decoder[SentEmail] =
    Decoder.forProduct3("Email", "MessageID", "MessageUUID")(SentEmail.apply)
}

case class BasicCrmResponse[T](count: Int, total: Int, data: Seq[T])

object BasicCrmResponse {
  def createDecoder[T](implicit tDecoder: Decoder[T]): Decoder[BasicCrmResponse[T]] =
    Decoder.forProduct3("Count", "Total", "Data")(BasicCrmResponse.apply)

  type ContactsResponse = BasicCrmResponse[ContactDataResponse]
  type ManageManyContactsResponse = BasicCrmResponse[JobId]
  type ManageManyContactsJobDetailsResponse = BasicCrmResponse[JobDetailsResponse]

  implicit val contactsResponseDecoder: Decoder[ContactsResponse] = createDecoder[ContactDataResponse]
  implicit val manageManyContactsResponseDecoder: Decoder[ManageManyContactsResponse] = createDecoder[JobId]
  implicit val manageManyContactsJobDetailsResponseDecoder: Decoder[ManageManyContactsJobDetailsResponse] =
    createDecoder[JobDetailsResponse]

}

case class ContactDataResponse(isExcludedFromCampaigns: Boolean,
                               name: String,
                               createdAt: String,
                               deliveredCount: Int,
                               email: String,
                               exclusionFromCampaignsUpdatedAt: String,
                               id: Long,
                               isOptInPending: Boolean,
                               isSpamComplaining: Boolean,
                               lastActivityAt: String,
                               lastUpdateAt: String)

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

case class JobId(jobId: Long)

object JobId {
  implicit val decoder: Decoder[JobId] =
    Decoder.forProduct1("JobID")(JobId.apply)
}

case class JobDetailsResponse(contactsLists: Seq[ContactListAndAction],
                              count: Int,
                              error: String,
                              errorFile: String,
                              jobEnd: String,
                              jobStart: String,
                              status: String)

object JobDetailsResponse {
  implicit val decoder: Decoder[JobDetailsResponse] =
    Decoder.forProduct7("ContactsLists", "Count", "Error", "ErrorFile", "JobEnd", "JobStart", "Status")(
      JobDetailsResponse.apply
    )
}

case class ContactListAndAction(listId: Long, action: String)

object ContactListAndAction {
  implicit val decoder: Decoder[ContactListAndAction] =
    Decoder.forProduct2("ListID", "Action")(ContactListAndAction.apply)
}

case class CrmClientException(message: String) extends Exception(message)
