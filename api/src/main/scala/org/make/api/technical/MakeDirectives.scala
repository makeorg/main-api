package org.make.api.technical

import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives
import de.knutwalker.akka.http.support.CirceHttpSupport
import kamon.akka.http.KamonTraceDirectives
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.core.CirceFormatters

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

trait MakeDirectives extends Directives with KamonTraceDirectives with CirceHttpSupport with CirceFormatters {
  this: IdGeneratorComponent =>

  val sessionIdKey: String = "make-session-id"

  def requestId: Directive1[String] = BasicDirectives.provide(idGenerator.nextId())
  def startTime: Directive1[Long] = BasicDirectives.provide(System.currentTimeMillis())
  def sessionId: Directive1[String] =
    optionalCookie(sessionIdKey).map(_.map(_.value).getOrElse(idGenerator.nextId()))

  def addMakeHeaders(requestId: String,
                     routeName: String,
                     sessionId: String,
                     startTime: Long,
                     addCookie: Boolean,
                     externalId: String): Directive0 = {
    mapResponseHeaders { (headers: immutable.Seq[HttpHeader]) =>
      val mandatoryHeaders: immutable.Seq[HttpHeader] = headers ++ Seq(
        RequestTimeHeader(startTime),
        RequestIdHeader(requestId),
        RouteNameHeader(routeName),
        ExternalIdHeader(externalId)
      )

      if (addCookie) {
        mandatoryHeaders ++ Seq(
          `Set-Cookie`(
            HttpCookie(
              name = sessionIdKey,
              value = sessionId,
              secure = true,
              httpOnly = true,
              maxAge = Some(20.minutes.toMillis)
            )
          )
        )
      } else {
        mandatoryHeaders
      }
    }
  }

  def makeTrace(name: String, tags: Map[String, String] = Map.empty): Directive0 = {
    val extract: Directive[(Option[HttpCookiePair], String, Long, String, String)] = for {
      maybeCookie <- optionalCookie(sessionIdKey)
      requestId   <- requestId
      startTime   <- startTime
      sessionId   <- sessionId
      externalId  <- optionalHeaderValueByName(ExternalIdHeader.name).map(_.getOrElse(requestId))
    } yield (maybeCookie, requestId, startTime, sessionId, externalId)

    extract.tflatMap {
      case (maybeCookie, requestId, startTime, sessionId, externalId) =>
        val resolvedTags: Map[String, String] = tags ++ Map(
          "id" -> requestId,
          sessionIdKey -> sessionId,
          "external-id" -> externalId,
          "start-time" -> startTime.toString,
          "route-name" -> name
        )
        traceName(name, resolvedTags).tflatMap { _ =>
          addMakeHeaders(requestId, name, sessionId, startTime, maybeCookie.isEmpty, externalId)
        }
    }
  }

  def provideAsync[T](provider: ⇒ Future[T]): Directive1[T] =
    extractExecutionContext.flatMap { implicit ec ⇒
      extract(_ => provider).flatMap { fa ⇒
        onComplete(fa).flatMap {
          case Success(value) ⇒ provide(value)
          case Failure(e) ⇒ throw e
        }
      }
    }

  def provideAsyncOrNotFound[T](provider: ⇒ Future[Option[T]]): Directive1[T] =
    extractExecutionContext.flatMap { implicit ec ⇒
      extract(_ => provider).flatMap { fa ⇒
        onComplete(fa).flatMap {
          case Success(Some(value)) ⇒ provide(value)
          case Success(None) ⇒ complete(StatusCodes.NotFound)
          case Failure(e) ⇒ throw e
        }
      }
    }
}

final case class RequestIdHeader(override val value: String) extends ModeledCustomHeader[RequestIdHeader] {
  override def companion: ModeledCustomHeaderCompanion[RequestIdHeader] = RequestIdHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = true
}

object RequestIdHeader extends ModeledCustomHeaderCompanion[RequestIdHeader] {
  override val name: String = "x-request-id"
  override def parse(value: String): Try[RequestIdHeader] = Success(new RequestIdHeader(value))
}

final case class RouteNameHeader(override val value: String) extends ModeledCustomHeader[RouteNameHeader] {
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = true
  override def companion: ModeledCustomHeaderCompanion[RouteNameHeader] = RouteNameHeader
}

object RouteNameHeader extends ModeledCustomHeaderCompanion[RouteNameHeader] {
  override val name: String = "x-route-name"
  override def parse(value: String): Try[RouteNameHeader] = Success(new RouteNameHeader(value))
}

final case class RequestTimeHeader(override val value: String) extends ModeledCustomHeader[RequestTimeHeader] {
  override def companion: ModeledCustomHeaderCompanion[RequestTimeHeader] = RequestTimeHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = true
}

object RequestTimeHeader extends ModeledCustomHeaderCompanion[RequestTimeHeader] {
  override val name: String = "x-route-time"
  override def parse(value: String): Try[RequestTimeHeader] =
    Failure(new NotImplementedError("Please use the apply from long instead"))

  def apply(startTimeMillis: Long): RequestTimeHeader =
    new RequestTimeHeader((System.currentTimeMillis() - startTimeMillis).toString)
}

final case class ExternalIdHeader(override val value: String) extends ModeledCustomHeader[ExternalIdHeader] {
  override def companion: ModeledCustomHeaderCompanion[ExternalIdHeader] = ExternalIdHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = true
}

object ExternalIdHeader extends ModeledCustomHeaderCompanion[ExternalIdHeader] {
  override val name: String = "x-make-external-id"
  override def parse(value: String): Try[ExternalIdHeader] = Success(new ExternalIdHeader(value))
}

trait MakeAuthenticationDirectives extends MakeDirectives with MakeAuthentication {
  this: MakeDataHandlerComponent with IdGeneratorComponent =>
}
