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

package org.make.api.technical

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives
import kamon.akka.http.KamonTraceDirectives.operationName
import org.make.api.MakeApi
import org.make.api.Predef._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.core.auth.UserRights
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, ThemeId}
import org.make.core.session.{SessionId, VisitorId}
import org.make.core.user.Role.{RoleAdmin, RoleModerator}
import org.make.core.{reference, ApplicationName, CirceFormatters, RequestContext, SlugHelper}
import org.mdedetrich.akka.http.support.CirceHttpSupport
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

trait MakeDirectives extends Directives with CirceHttpSupport with CirceFormatters {
  this: IdGeneratorComponent with MakeSettingsComponent with MakeAuthentication =>

  val sessionIdKey: String = "make-session-id"
  val visitorIdKey: String = "make-visitor-id"
  lazy val authorizedUris: Seq[String] = makeSettings.authorizedCorsUri
  lazy val sessionCookieName: String = makeSettings.SessionCookie.name

  def requestId: Directive1[String] = BasicDirectives.provide(idGenerator.nextId())

  def startTime: Directive1[Long] = BasicDirectives.provide(System.currentTimeMillis())

  /**
    * sessionId is set in cookie and header
    * for web app and native app respectively
    */
  def sessionId: Directive1[String] =
    for {
      maybeCookieSessionId <- optionalCookie(sessionIdKey)
      maybeSessionId       <- optionalHeaderValueByName(SessionIdHeader.name)
    } yield maybeCookieSessionId.map(_.value).orElse(maybeSessionId).getOrElse(idGenerator.nextId())

  /**
    * visitorId is set in cookie and header
    * for web app and native app respectively
    */
  def visitorId: Directive1[String] =
    for {
      maybeCookieVisitorId <- optionalCookie(visitorIdKey)
      maybeVisitorId       <- optionalHeaderValueByName(VisitorIdHeader.name)
    } yield maybeCookieVisitorId.map(_.value).orElse(maybeVisitorId).getOrElse(idGenerator.nextVisitorId().value)

  def addMakeHeaders(requestId: String,
                     routeName: String,
                     sessionId: String,
                     visitorId: String,
                     startTime: Long,
                     externalId: String,
                     origin: Option[String]): Directive0 = {
    respondWithDefaultHeaders {
      immutable.Seq(
        RequestTimeHeader(startTime),
        RequestIdHeader(requestId),
        RouteNameHeader(routeName),
        ExternalIdHeader(externalId),
        SessionIdHeader(sessionId),
        `Set-Cookie`(
          HttpCookie(
            name = sessionIdKey,
            value = sessionId,
            secure = makeSettings.SessionCookie.isSecure,
            httpOnly = true,
            maxAge = Some(makeSettings.SessionCookie.lifetime.toSeconds),
            path = Some("/"),
            domain = Some(makeSettings.SessionCookie.domain)
          )
        ),
        `Set-Cookie`(
          HttpCookie(
            name = visitorIdKey,
            value = visitorId,
            secure = makeSettings.VisitorCookie.isSecure,
            httpOnly = true,
            maxAge = Some(365.days.toSeconds),
            path = Some("/"),
            domain = Some(makeSettings.VisitorCookie.domain)
          )
        )
      ) ++ defaultCorsHeaders(origin)
    }
  }

  def makeOperation(name: String): Directive1[RequestContext] = {
    val slugifiedName: String = SlugHelper(name)

    for {
      _                    <- encodeResponse
      _                    <- operationName(slugifiedName)
      requestId            <- requestId
      startTime            <- startTime
      sessionId            <- sessionId
      visitorId            <- visitorId
      externalId           <- optionalHeaderValueByName(ExternalIdHeader.name).map(_.getOrElse(requestId))
      origin               <- optionalHeaderValueByName(Origin.name)
      _                    <- makeAuthCookieHandlers()
      _                    <- addMakeHeaders(requestId, slugifiedName, sessionId, visitorId, startTime, externalId, origin)
      _                    <- handleExceptions(MakeApi.exceptionHandler(slugifiedName, requestId))
      _                    <- handleRejections(MakeApi.rejectionHandler)
      maybeTheme           <- optionalHeaderValueByName(ThemeIdHeader.name)
      maybeOperation       <- optionalHeaderValueByName(OperationHeader.name)
      maybeSource          <- optionalHeaderValueByName(SourceHeader.name)
      maybeLocation        <- optionalHeaderValueByName(LocationHeader.name)
      maybeQuestion        <- optionalHeaderValueByName(QuestionHeader.name)
      maybeCountry         <- optionalHeaderValueByName(CountryHeader.name)
      maybeDetectedCountry <- optionalHeaderValueByName(DetectedCountryHeader.name)
      maybeLanguage        <- optionalHeaderValueByName(LanguageHeader.name)
      maybeHostName        <- optionalHeaderValueByName(HostNameHeader.name)
      maybeIpAddress       <- extractClientIP
      maybeGetParameters   <- optionalHeaderValueByName(GetParametersHeader.name)
      maybeUserAgent       <- optionalHeaderValueByName(`User-Agent`.name)
      maybeUser            <- optionalMakeOAuth2
      maybeQuestionId      <- optionalHeaderValueByName(QuestionIdHeader.name)
      maybeApplicationName <- optionalHeaderValueByName(ApplicationNameHeader.name)
    } yield {
      RequestContext(
        currentTheme = maybeTheme.map(ThemeId.apply),
        userId = maybeUser.map(_.user.userId),
        requestId = requestId,
        sessionId = SessionId(sessionId),
        visitorId = Some(VisitorId(visitorId)),
        externalId = externalId,
        operationId = maybeOperation.map(OperationId(_)),
        source = maybeSource,
        location = maybeLocation,
        question = maybeQuestion,
        language = maybeLanguage.map(reference.Language(_)),
        country = maybeCountry.map(Country(_)),
        detectedCountry = maybeDetectedCountry.map(Country(_)),
        hostname = maybeHostName,
        ipAddress = maybeIpAddress.toOption.map(_.getHostAddress),
        getParameters = maybeGetParameters.map(
          _.split("&")
            .map(_.split("=", 2))
            .map {
              case Array(key, value) => key -> value
              case Array(key)        => key -> ""
            }
            .toMap
        ),
        userAgent = maybeUserAgent.map(_.toString),
        questionId = maybeQuestionId.map(QuestionId.apply),
        applicationName = maybeApplicationName.flatMap(ApplicationName.applicationMap.get)
      )
    }
  }

  def provideAsync[T](provider: ⇒ Future[T]): Directive1[T] =
    extractExecutionContext.flatMap { implicit ec ⇒
      extract(_ => provider).flatMap { fa ⇒
        onComplete(fa).flatMap {
          case Success(value) ⇒ provide(value)
          case Failure(e) ⇒ failWith(e)
        }
      }
    }

  def provideAsyncOrNotFound[T](provider: ⇒ Future[Option[T]]): Directive1[T] =
    extractExecutionContext.flatMap { implicit ec ⇒
      extract(_ => provider).flatMap { fa ⇒
        onComplete(fa).flatMap {
          case Success(Some(value)) ⇒ provide(value)
          case Success(None) ⇒ complete(StatusCodes.NotFound)
          case Failure(e) ⇒ failWith(e)
        }
      }
    }

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
      `Access-Control-Allow-Credentials`(true),
      `Access-Control-Expose-Headers`(
        immutable.Seq(
          RequestIdHeader.name,
          RouteNameHeader.name,
          RequestTimeHeader.name,
          ExternalIdHeader.name,
          SessionIdHeader.name,
          TotalCountHeader.name
        )
      )
    ) ++ mayBeOriginValue.map { httpOrigin =>
      `Access-Control-Allow-Origin`(origin = httpOrigin)
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
      optionalHeaderValueByName(Origin.name) { mayBeOriginHeaderValue =>
        respondWithDefaultHeaders(defaultCorsHeaders(mayBeOriginHeaderValue)) {
          optionalHeaderValueByType[`Access-Control-Request-Headers`](()) {
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

  def requireRightsOnQuestion(user: UserRights, maybeQuestionId: Option[QuestionId]): Directive0 = {
    authorize(
      user.roles.contains(RoleAdmin) || user.availableQuestions
        .exists(questionId => maybeQuestionId.contains(questionId))
    )
  }

  def requireAdminRole(user: UserRights): Directive0 = {
    authorize(user.roles.contains(RoleAdmin))
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

final case class DetectedCountryHeader(override val value: String) extends ModeledCustomHeader[DetectedCountryHeader] {
  override def companion: ModeledCustomHeaderCompanion[DetectedCountryHeader] = DetectedCountryHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object DetectedCountryHeader extends ModeledCustomHeaderCompanion[DetectedCountryHeader] {
  override val name: String = "x-detected-country"
  override def parse(value: String): Try[DetectedCountryHeader] = Success(new DetectedCountryHeader(value))
}

final case class HostNameHeader(override val value: String) extends ModeledCustomHeader[HostNameHeader] {
  override def companion: ModeledCustomHeaderCompanion[HostNameHeader] = HostNameHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object HostNameHeader extends ModeledCustomHeaderCompanion[HostNameHeader] {
  override val name: String = "x-hostname"
  override def parse(value: String): Try[HostNameHeader] = Success(new HostNameHeader(value))
}

final case class GetParametersHeader(override val value: String) extends ModeledCustomHeader[GetParametersHeader] {
  override def companion: ModeledCustomHeaderCompanion[GetParametersHeader] = GetParametersHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object GetParametersHeader extends ModeledCustomHeaderCompanion[GetParametersHeader] {
  override val name: String = "x-get-parameters"
  override def parse(value: String): Try[GetParametersHeader] = Success(new GetParametersHeader(value))
}

final case class SessionIdHeader(override val value: String) extends ModeledCustomHeader[SessionIdHeader] {
  override def companion: ModeledCustomHeaderCompanion[SessionIdHeader] = SessionIdHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = true
}

object SessionIdHeader extends ModeledCustomHeaderCompanion[SessionIdHeader] {
  override val name: String = "x-session-id"
  override def parse(value: String): Try[SessionIdHeader] = Success(new SessionIdHeader(value))
}

final case class TotalCountHeader(override val value: String) extends ModeledCustomHeader[TotalCountHeader] {
  override def companion: ModeledCustomHeaderCompanion[TotalCountHeader] = TotalCountHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = true
}

object TotalCountHeader extends ModeledCustomHeaderCompanion[TotalCountHeader] {
  val name: String = "x-total-count"
  def parse(value: String): Try[TotalCountHeader] = Success(new TotalCountHeader(value))
}

final case class VisitorIdHeader(override val value: String) extends ModeledCustomHeader[VisitorIdHeader] {
  override def companion: ModeledCustomHeaderCompanion[VisitorIdHeader] = VisitorIdHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = true
}

object VisitorIdHeader extends ModeledCustomHeaderCompanion[VisitorIdHeader] {
  override val name: String = "x-visitor-id"
  override def parse(value: String): Try[VisitorIdHeader] = Success(new VisitorIdHeader(value))
}

final case class QuestionIdHeader(override val value: String) extends ModeledCustomHeader[QuestionIdHeader] {
  override def companion: ModeledCustomHeaderCompanion[QuestionIdHeader] = QuestionIdHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object QuestionIdHeader extends ModeledCustomHeaderCompanion[QuestionIdHeader] {
  override val name: String = "x-make-question-id"
  override def parse(value: String): Try[QuestionIdHeader] = Success(new QuestionIdHeader(value))
}

final case class ApplicationNameHeader(override val value: String) extends ModeledCustomHeader[ApplicationNameHeader] {
  override def companion: ModeledCustomHeaderCompanion[ApplicationNameHeader] = ApplicationNameHeader
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object ApplicationNameHeader extends ModeledCustomHeaderCompanion[ApplicationNameHeader] {
  override val name: String = "x-make-app-name"
  override def parse(value: String): Try[ApplicationNameHeader] = Success(new ApplicationNameHeader(value))
}
