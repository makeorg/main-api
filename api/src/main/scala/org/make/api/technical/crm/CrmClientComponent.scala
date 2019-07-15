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

import java.net.{URL, URLEncoder}

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorAttributes, ActorMaterializer, OverflowStrategy, QueueOfferResult}
import io.circe.Printer
import io.circe.syntax._
import org.make.api
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MailJetConfigurationComponent

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

trait CrmClient {
  def manageContactMailJetRequest(listId: String, manageContact: ManageContact)(
    implicit executionContext: ExecutionContext
  ): Future[HttpResponse]
  def manageContactListMailJetRequest(manageContactList: ManageManyContacts)(
    implicit executionContext: ExecutionContext
  ): Future[HttpResponse]
  def updateContactProperties(contactData: ContactData, email: String)(
    implicit executionContext: ExecutionContext
  ): Future[HttpResponse]
  def sendEmailMailJetRequest(message: SendMessages)(implicit executionContext: ExecutionContext): Future[HttpResponse]
  def getUsersMailFromList(listId: String, limit: Int, offset: Int)(
    implicit executionContext: ExecutionContext
  ): Future[HttpResponse]
}

trait CrmClientComponent {
  def crmClient: CrmClient
}

trait DefaultCrmClientComponent extends CrmClientComponent {
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

    lazy val queue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] = Source
      .queue[(HttpRequest, Promise[HttpResponse])](bufferSize = bufferSize, OverflowStrategy.backpressure)
      .via(httpFlow)
      .withAttributes(ActorAttributes.dispatcher(api.mailJetDispatcher))
      .toMat(Sink.foreach {
        case (Success(resp), p) => p.success(resp)
        case (Failure(e), p)    => p.failure(e)
      })(Keep.left)
      .run()(ActorMaterializer()(actorSystem))

    private lazy val authorization = Authorization(
      BasicHttpCredentials(mailJetConfiguration.campaignApiKey, mailJetConfiguration.campaignSecretKey)
    )

    override def manageContactMailJetRequest(listId: String, manageContact: ManageContact)(
      implicit executionContext: ExecutionContext
    ): Future[HttpResponse] = {
      val request = HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contactslist/$listId/managecontact"),
        headers = immutable.Seq(authorization),
        entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(manageContact.asJson))
      )
      doHttpCall(request)
    }

    override def manageContactListMailJetRequest(
      manageContactList: ManageManyContacts
    )(implicit executionContext: ExecutionContext): Future[HttpResponse] = {
      val request =
        HttpRequest(
          method = HttpMethods.POST,
          uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contact/managemanycontacts"),
          headers = immutable.Seq(authorization),
          entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(manageContactList.asJson))
        )
      doHttpCall(request)
    }

    override def updateContactProperties(contactData: ContactData, email: String)(
      implicit executionContext: ExecutionContext
    ): Future[HttpResponse] = {
      val encodedEmail: String = URLEncoder.encode(email, "UTF-8")
      val request =
        HttpRequest(
          method = HttpMethods.PUT,
          uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contactdata/$encodedEmail"),
          headers = immutable.Seq(authorization),
          entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(contactData.asJson))
        )
      doHttpCall(request)
    }

    override def sendEmailMailJetRequest(
      message: SendMessages
    )(implicit executionContext: ExecutionContext): Future[HttpResponse] = {
      val request: HttpRequest = HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(s"${mailJetConfiguration.url}/v3.1/send"),
        headers = immutable
          .Seq(Authorization(BasicHttpCredentials(mailJetConfiguration.apiKey, mailJetConfiguration.secretKey))),
        entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(message.asJson))
      )
      doHttpCall(request)
    }

    override def getUsersMailFromList(listId: String, limit: Int, offset: Int)(
      implicit executionContext: ExecutionContext
    ): Future[HttpResponse] = {
      val params = s"ContactsList=$listId&Limit=$limit&Offset=$offset"
      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contact?$params"),
        headers = immutable.Seq(authorization)
      )
      doHttpCall(request)
    }

    private def doHttpCall(request: HttpRequest)(implicit executionContext: ExecutionContext): Future[HttpResponse] = {
      val promise = Promise[HttpResponse]()
      queue.offer((request, promise)).flatMap {
        case QueueOfferResult.Enqueued    => promise.future
        case QueueOfferResult.Dropped     => Future.failed(new RuntimeException("Queue overflowed. Try again later."))
        case QueueOfferResult.Failure(ex) => Future.failed(ex)
        case QueueOfferResult.QueueClosed =>
          Future
            .failed(
              new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later.")
            )
      }
    }
  }
}
