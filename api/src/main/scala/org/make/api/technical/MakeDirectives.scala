package org.make.api.technical

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives
import de.knutwalker.akka.http.support.CirceHttpSupport
import kamon.akka.http.KamonTraceDirectives
import kamon.trace.Tracer
import org.make.api.MakeApi
import org.make.api.Predef._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.core.auth.UserRights
import org.make.core.reference.ThemeId
import org.make.core.session.SessionId
import org.make.core.user.Role.{RoleAdmin, RoleModerator}
import org.make.core.{CirceFormatters, RequestContext}

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait MakeDirectives extends Directives with KamonTraceDirectives with CirceHttpSupport with CirceFormatters {
  this: IdGeneratorComponent with MakeSettingsComponent =>

  val sessionIdKey: String = "make-session-id"
  lazy val authorizedUris: Seq[String] = makeSettings.authorizedCorsUri
  lazy val sessionCookieName: String = makeSettings.SessionCookie.name

  def startTimeFromTrace: Long = getFromTrace("start-time", "0").toLong
  def routeNameFromTrace: String = getFromTrace("route-name")
  def externalIdFromTrace: String = getFromTrace("external-id")
  def requestIdFromTrace: String = getFromTrace("id")

  def requestId: Directive1[String] = BasicDirectives.provide(idGenerator.nextId())
  def startTime: Directive1[Long] = BasicDirectives.provide(System.currentTimeMillis())
  def sessionId: Directive1[String] =
    optionalCookie(sessionIdKey).map(_.map(_.value).getOrElse(idGenerator.nextId()))

  private def getFromTrace(key: String, default: String = "<unknown>"): String =
    Tracer.currentContext.tags.getOrElse(key, default)

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
              secure = makeSettings.SessionCookie.isSecure,
              httpOnly = true,
              maxAge = Some(makeSettings.SessionCookie.lifetime.toMillis),
              path = Some("/")
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
      maybeCountry   <- optionalHeaderValueByName(CountryHeader.name)
      maybeLanguage  <- optionalHeaderValueByName(LanguageHeader.name)
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
    } yield {

      RequestContext(
        currentTheme = maybeTheme.map(ThemeId.apply),
        requestId = requestId,
        sessionId = SessionId(sessionId),
        externalId = externalId,
        operation = maybeOperation,
        source = maybeSource,
        location = maybeLocation,
        question = maybeQuestion,
        language = maybeLanguage,
        country = maybeCountry
      )
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

  def defaultHeadersFromTrace: immutable.Seq[HttpHeader] = immutable.Seq(
    RequestIdHeader(requestIdFromTrace),
    RequestTimeHeader(startTimeFromTrace),
    RouteNameHeader(routeNameFromTrace),
    ExternalIdHeader(externalIdFromTrace)
  )

  def getMakeHttpOrigin(mayBeOriginValue: Option[String]): Option[HttpOrigin] = {
    mayBeOriginValue.flatMap { origin =>
      if (authorizedUris.contains(origin)) {
        val originUri = Uri(origin)
        Some(HttpOrigin(originUri.scheme, Host(originUri.authority.host, originUri.authority.port)))
      } else {
        None
      }
    }
  }

  def defaultCorsHeaders(originValue: Option[String]): immutable.Seq[HttpHeader] = {

    val mayBeOriginValue: Option[HttpOrigin] = getMakeHttpOrigin(originValue)

    immutable.Seq(
      `Access-Control-Allow-Methods`(
        HttpMethods.POST,
        HttpMethods.GET,
        HttpMethods.PUT,
        HttpMethods.PATCH,
        HttpMethods.DELETE
      ),
      `Access-Control-Allow-Credentials`(true)
    ) ++ mayBeOriginValue.map { httpOrigin =>
      immutable.Seq(`Access-Control-Allow-Origin`(origin = httpOrigin))
    }.getOrElse(immutable.Seq())

  }

  private def getHeaderFromRequest(request: HttpRequest): Option[String] = {
    request.header[Origin].map { header =>
      header.value()
    }
  }

  def makeDefaultHeadersAndHandlers(): Directive0 =
    mapInnerRoute { route =>
      makeAuthCookieHandlers() {
        extractRequest { request =>
          val mayBeOriginHeaderValue: Option[String] = getHeaderFromRequest(request)
          makeAuthCookieHandlers() {
            respondWithDefaultHeaders(defaultHeadersFromTrace ++ defaultCorsHeaders(mayBeOriginHeaderValue)) {
              handleExceptions(MakeApi.exceptionHandler) {
                handleRejections(MakeApi.rejectionHandler) {
                  route
                }
              }
            }
          }
        }
      }
    }

  def makeAuthCookieHandlers(): Directive0 =
    mapInnerRoute { route =>
      optionalCookie(sessionCookieName) {
        case Some(secureCookie) =>
          mapRequest((request: HttpRequest) => request.addCredentials(OAuth2BearerToken(secureCookie.value))) {
            route
          }
        case None => route
      }
    }

  def corsHeaders(): Directive0 =
    mapInnerRoute { route =>
      extractRequest { request =>
        val mayBeOriginHeaderValue: Option[String] = getHeaderFromRequest(request)
        respondWithDefaultHeaders(defaultCorsHeaders(mayBeOriginHeaderValue)) {
          optionalHeaderValueByType[`Access-Control-Request-Headers`]() {
            case Some(requestHeader) =>
              respondWithDefaultHeaders(`Access-Control-Allow-Headers`(requestHeader.value)) {
                route
              }
            case None => route
          }
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

trait MakeAuthenticationDirectives extends MakeAuthentication {
  this: MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  def requireModerationRole(user: UserRights): Directive0 = {
    authorize(user.roles.contains(RoleModerator) || user.roles.contains(RoleAdmin))
  }
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

final case class LanguageHeader(override val value: String) extends ModeledCustomHeader[LanguageHeader] {
  override def companion: ModeledCustomHeaderCompanion[LanguageHeader] = LanguageHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object LanguageHeader extends ModeledCustomHeaderCompanion[LanguageHeader] {
  override val name: String = "x-make-language"
  override def parse(value: String): Try[LanguageHeader] = Success(new LanguageHeader(value))
}

final case class CountryHeader(override val value: String) extends ModeledCustomHeader[CountryHeader] {
  override def companion: ModeledCustomHeaderCompanion[CountryHeader] = CountryHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object CountryHeader extends ModeledCustomHeaderCompanion[CountryHeader] {
  override val name: String = "x-make-country"
  override def parse(value: String): Try[CountryHeader] = Success(new CountryHeader(value))
}
