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

package org.make.api.technical.webflow

import java.time.ZonedDateTime

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorAttributes, OverflowStrategy, QueueOfferResult}
import grizzled.slf4j.Logging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import enumeratum.{Enum, EnumEntry}
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric._
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, Printer}
import org.make.api.ActorSystemComponent
import org.make.api.technical.webflow.WebflowClient.UpToOneHundred
import org.make.api.technical.webflow.WebflowError.{WebflowErrorName, WebflowErrorResponse}
import org.make.api.technical.webflow.WebflowItem._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}
import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers.stringUnmarshaller

trait WebflowClient {
  def getPosts(limit: UpToOneHundred, offset: Int): Future[Seq[WebflowPost]]
}

object WebflowClient {
  type UpToOneHundredRefinement = Interval.Closed[W.`0`.T, W.`100`.T]
  type UpToOneHundred = Int Refined UpToOneHundredRefinement
}

trait WebflowClientComponent {
  def webflowClient: WebflowClient
}

trait DefaultWebflowClientComponent extends WebflowClientComponent with ErrorAccumulatingCirceSupport with Logging {
  self: WebflowConfigurationComponent with ActorSystemComponent =>

  override lazy val webflowClient: WebflowClient = new DefaultWebflowClient

  class DefaultWebflowClient extends WebflowClient {

    lazy val printer: Printer = Printer.noSpaces.copy(dropNullValues = false)
    val httpPort: Int = 443

    lazy val httpFlow: Flow[
      (HttpRequest, Promise[HttpResponse]),
      (Try[HttpResponse], Promise[HttpResponse]),
      Http.HostConnectionPool
    ] =
      Http(actorSystem).cachedHostConnectionPoolHttps[Promise[HttpResponse]](
        host = webflowConfiguration.apiUrl.getHost,
        port = httpPort
      )

    private lazy val bufferSize = webflowConfiguration.httpBufferSize

    lazy val queue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] = Source
      .queue[(HttpRequest, Promise[HttpResponse])](bufferSize = bufferSize, OverflowStrategy.backpressure)
      .throttle(webflowConfiguration.rateLimitPerMinute, 1.minute)
      .via(httpFlow)
      .withAttributes(ActorAttributes.dispatcher("make-api.webflow.dispatcher"))
      .toMat(Sink.foreach {
        case (Success(resp), p) => p.success(resp)
        case (Failure(e), p)    => p.failure(e)
      })(Keep.left)
      .run()

    private lazy val authorization: HttpHeader = Authorization(OAuth2BearerToken(webflowConfiguration.token))
    private val acceptVersion: HttpHeader = `Accept-Version`("1.0.0")

    private val postsCollectionId = webflowConfiguration.collectionsIds("posts")

    private val defaultHeaders = Seq(authorization, acceptVersion)

    override def getPosts(limit: UpToOneHundred, offset: Int): Future[Seq[WebflowPost]] = {
      val paramsQuery = s"limit=${limit.value}&offset=$offset"
      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = Uri(s"${webflowConfiguration.apiUrl.toString}/collections/$postsCollectionId/items?$paramsQuery"),
        headers = defaultHeaders
      )
      logger.debug(s"WebflowRequest: ${request.toString.replace('\n', ' ')}")
      doHttpCall(request).flatMap {
        case HttpResponse(code, headers, entity, _) if code.isFailure() =>
          handleErrors(code, headers, entity, "getItemsFromCollection [posts]")
        case HttpResponse(code, _, entity, _) if code.isSuccess() =>
          Unmarshal(entity.withoutSizeLimit).to[WebflowItems[WebflowPost]].map(_.items.map(_.item))
        case HttpResponse(code, _, entity, _) =>
          Unmarshal(entity).to[String].flatMap { response =>
            Future.failed(
              new IllegalArgumentException(
                s"Unexpected response from Webflow with status code $code: $response. Caused by request ${request.toString}"
              )
            )
          }
      }
    }

    private def handleErrors(
      code: StatusCode,
      headers: Seq[HttpHeader],
      entity: ResponseEntity,
      action: String
    ): Future[Seq[WebflowPost]] = {
      Unmarshal(entity).to[WebflowErrorResponse].flatMap { errorResponse =>
        errorResponse.name match {
          case WebflowErrorName.RateLimit =>
            val maxLimit: String = headers.find(_.is("x-ratelimit-limit")).map(_.value()).getOrElse("unknown")
            val remaining: String = headers.find(_.is("x-ratelimit-remaining")).map(_.value()).getOrElse("unknown")
            logger.error(
              s"Webflow error $code ${errorResponse.name}: ${errorResponse.msg}. Max limit per minute: $maxLimit. Remaining: $remaining"
            )
            Future.failed(WebflowClientException.RateLimitException(maxLimit, errorResponse.toString))
          case _ =>
            logger.error(
              s"Webflow error $code ${errorResponse.name}: ${errorResponse.msg}. Caused by requested path ${errorResponse.path}"
            )
            Future.failed(WebflowClientException.RequestException(action, code, errorResponse.toString))
        }
      }
    }

    private def doHttpCall(request: HttpRequest): Future[HttpResponse] = {
      val promise = Promise[HttpResponse]()
      queue.offer((request, promise)).flatMap {
        case QueueOfferResult.Enqueued    => promise.future
        case QueueOfferResult.Dropped     => Future.failed(WebflowClientException.QueueException.QueueOverflowed)
        case QueueOfferResult.Failure(ex) => Future.failed(ex)
        case QueueOfferResult.QueueClosed => Future.failed(WebflowClientException.QueueException.QueueClosed)
      }
    }
  }
}

object WebflowError {

  sealed trait WebflowErrorName extends EnumEntry
  object WebflowErrorName extends Enum[WebflowErrorName] {
    case object SyntaxError extends WebflowErrorName
    case object InvalidAPIVersion extends WebflowErrorName
    case object UnsupportedVersion extends WebflowErrorName
    case object NotImplemented extends WebflowErrorName
    case object ValidationError extends WebflowErrorName
    case object Conflict extends WebflowErrorName
    case object Unauthorized extends WebflowErrorName
    case object NotFound extends WebflowErrorName
    case object RateLimit extends WebflowErrorName
    case object ServerError extends WebflowErrorName
    case object UnknownError extends WebflowErrorName

    override def values: IndexedSeq[WebflowErrorName] = findValues
    implicit val decoder: Decoder[WebflowErrorName] =
      Decoder[String].map(withNameInsensitiveOption(_).getOrElse(UnknownError))
  }

  final case class WebflowErrorResponse(msg: String, code: Int, name: WebflowErrorName, path: String, err: String)
  object WebflowErrorResponse {
    implicit val decoder: Decoder[WebflowErrorResponse] = deriveDecoder[WebflowErrorResponse]
  }
}

sealed abstract class WebflowClientException(val message: String) extends Exception(message)

object WebflowClientException {

  sealed abstract class QueueException(message: String) extends WebflowClientException(message)

  object QueueException {
    case object QueueOverflowed extends QueueException("Queue overflowed. Try again later.")

    case object QueueClosed
        extends QueueException("Queue was closed (pool shut down) while running the request. Try again later.")
  }

  final case class RequestException(action: String, code: StatusCode, response: String)
      extends WebflowClientException(s"$action failed with status $code: $response")
  final case class RateLimitException(maxLimit: String, response: String)
      extends WebflowClientException(s"Rate limit reached ($maxLimit per minute): $response")
}

final case class `Accept-Version`(override val value: String) extends ModeledCustomHeader[`Accept-Version`] {
  override def companion: ModeledCustomHeaderCompanion[`Accept-Version`] = `Accept-Version`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = true
}

object `Accept-Version` extends ModeledCustomHeaderCompanion[`Accept-Version`] {
  override val name: String = "Accept-Version"
  override def parse(value: String): Try[`Accept-Version`] = Success(new `Accept-Version`(value))
}

final case class WebflowItems[T](items: Seq[WebflowItem[T]], count: Int, limit: Int, offset: Int, total: Int)

object WebflowItems {
  implicit def createDecoder[T: Decoder]: Decoder[WebflowItems[T]] =
    Decoder.forProduct5("items", "count", "limit", "offset", "total")(WebflowItems.apply)

  type PostWebflowItemResponse = WebflowItems[WebflowPost]
}

final case class WebflowItemMetas(
  archived: Boolean,
  draft: Boolean,
  id: String,
  cid: String,
  name: String,
  slug: String,
  updatedOn: Option[ZonedDateTime],
  createdOn: Option[ZonedDateTime],
  publishedOn: Option[ZonedDateTime],
  updatedBy: Option[String],
  createdBy: Option[String],
  publishedBy: Option[String]
)

object WebflowItemMetas {
  implicit val decoder: Decoder[WebflowItemMetas] =
    Decoder.forProduct12(
      "_archived",
      "_draft",
      "_id",
      "_cid",
      "name",
      "slug",
      "updated-on",
      "created-on",
      "published-on",
      "updated-by",
      "created-by",
      "published-by"
    )(WebflowItemMetas.apply)

}

final case class WebflowItem[Item](item: Item, metas: WebflowItemMetas)

object WebflowItem {
  implicit def decoder[Item: Decoder]: Decoder[WebflowItem[Item]] =
    WebflowItemMetas.decoder.product(Decoder[Item]).map {
      case (metas, item) => WebflowItem(item, metas)
    }

  final case class WebflowImageRef(url: String, alt: Option[String])
  object WebflowImageRef {
    implicit val webflowImageRefDecoder: Decoder[WebflowImageRef] = deriveDecoder[WebflowImageRef]
  }

  final case class WebflowPost(
    id: String,
    archived: Boolean,
    draft: Boolean,
    name: String,
    slug: String,
    displayHome: Option[Boolean],
    postDate: Option[ZonedDateTime],
    thumbnailImage: Option[WebflowImageRef],
    summary: Option[String]
  )

  implicit val webflowPostDecoder: Decoder[WebflowPost] =
    Decoder.forProduct9(
      "_id",
      "_archived",
      "_draft",
      "name",
      "slug",
      "afficher-cet-article-sur-le-site-de-make-org",
      "post-date",
      "thumbnail-image",
      "post-summary"
    )(WebflowPost.apply)

}
