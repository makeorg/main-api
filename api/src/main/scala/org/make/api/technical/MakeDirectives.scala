package org.make.api.technical

import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives
import de.knutwalker.akka.http.support.CirceHttpSupport
import kamon.akka.http.KamonTraceDirectives
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.core.proposal.ThemeId
import org.make.core.{CirceFormatters, RequestContext}

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import org.make.api.Predef._

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

  def makeTrace(name: String, tags: Map[String, String] = Map.empty): Directive1[RequestContext] = {
    for {
      maybeCookie    <- optionalCookie(sessionIdKey)
      requestId      <- requestId
      startTime      <- startTime
      sessionId      <- sessionId
      externalId     <- optionalHeaderValueByName(ExternalIdHeader.name).map(_.getOrElse(requestId))
      maybeTheme     <- optionalHeaderValueByName(ThemeIdHeader.name)
      maybeOperation <- optionalHeaderValueByName(OperationHeader.name)
      maybeSource    <- optionalHeaderValueByName(SourceHeader.name)
      maybeLocation  <- optionalHeaderValueByName(LocationHeader.name)
      maybeQuestion  <- optionalHeaderValueByName(QuestionHeader.name)
      _ <- traceName(
        name,
        tags ++ Map(
          "id" -> requestId,
          sessionIdKey -> sessionId,
          "external-id" -> externalId,
          "start-time" -> startTime.toString,
          "route-name" -> name
        )
      )
      _ <- addMakeHeaders(requestId, name, sessionId, startTime, maybeCookie.isEmpty, externalId)
    } yield
      RequestContext(
        currentTheme = maybeTheme.map(ThemeId.apply),
        requestId = requestId,
        sessionId = sessionId,
        externalId = externalId,
        operation = maybeOperation,
        source = maybeSource,
        location = maybeLocation,
        question = maybeQuestion
      )
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

final case class ThemeIdHeader(override val value: String) extends ModeledCustomHeader[ThemeIdHeader] {
  override def companion: ModeledCustomHeaderCompanion[ThemeIdHeader] = ThemeIdHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object ThemeIdHeader extends ModeledCustomHeaderCompanion[ThemeIdHeader] {
  override val name: String = "x-make-theme-id"
  override def parse(value: String): Try[ThemeIdHeader] = Success(new ThemeIdHeader(value))
}

final case class OperationHeader(override val value: String) extends ModeledCustomHeader[OperationHeader] {
  override def companion: ModeledCustomHeaderCompanion[OperationHeader] = OperationHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object OperationHeader extends ModeledCustomHeaderCompanion[OperationHeader] {
  override val name: String = "x-make-operation"
  override def parse(value: String): Try[OperationHeader] = Success(new OperationHeader(value))
}

final case class SourceHeader(override val value: String) extends ModeledCustomHeader[SourceHeader] {
  override def companion: ModeledCustomHeaderCompanion[SourceHeader] = SourceHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object SourceHeader extends ModeledCustomHeaderCompanion[SourceHeader] {
  override val name: String = "x-make-source"
  override def parse(value: String): Try[SourceHeader] = Success(new SourceHeader(value))
}

final case class LocationHeader(override val value: String) extends ModeledCustomHeader[LocationHeader] {
  override def companion: ModeledCustomHeaderCompanion[LocationHeader] = LocationHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object LocationHeader extends ModeledCustomHeaderCompanion[LocationHeader] {
  override val name: String = "x-make-location"
  override def parse(value: String): Try[LocationHeader] = Success(new LocationHeader(value))
}

final case class QuestionHeader(override val value: String) extends ModeledCustomHeader[QuestionHeader] {
  override def companion: ModeledCustomHeaderCompanion[QuestionHeader] = QuestionHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object QuestionHeader extends ModeledCustomHeaderCompanion[QuestionHeader] {
  override val name: String = "x-make-question"
  override def parse(value: String): Try[QuestionHeader] = Success(new QuestionHeader(value))
}
