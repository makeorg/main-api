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

import java.io.File
import java.net.URLDecoder
import java.nio.file.Files
import java.time.ZonedDateTime

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.AuthenticationFailedRejection.{CredentialsMissing, CredentialsRejected}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import kamon.Kamon
import kamon.instrumentation.akka.http.TracingDirectives
import kamon.tag.TagSet
import org.make.api.MakeApi
import org.make.api.Predef._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.api.technical.monitoring.MonitoringMessageHelper
import org.make.api.technical.storage.Content
import org.make.api.technical.storage.Content.FileContent
import org.make.api.technical.tracing.Tracing
import org.make.core.Validation.validateField
import org.make.core.auth.UserRights
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.session.{SessionId, VisitorId}
import org.make.core.user.Role.{RoleAdmin, RoleModerator}
import org.make.core.user.UserId
import org.make.core.{RequestContext, _}
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

trait MakeDirectives
    extends Directives
    with ErrorAccumulatingCirceSupport
    with CirceFormatters
    with MakeDataHandlerComponent
    with TracingDirectives {
  this: IdGeneratorComponent
    with MakeSettingsComponent
    with MakeAuthentication
    with SessionHistoryCoordinatorServiceComponent =>

  lazy val authorizedUris: Seq[String] = makeSettings.authorizedCorsUri
  lazy val mandatoryConnection: Boolean = makeSettings.mandatoryConnection

  def requestId: Directive1[String] = BasicDirectives.provide(idGenerator.nextId())

  def startTime: Directive1[Long] = BasicDirectives.provide(System.currentTimeMillis())

  /**
    * sessionId is set in cookie and header
    * for web app and native app respectively
    */
  def sessionId: Directive1[String] =
    for {
      maybeCookieSessionId <- optionalCookie(makeSettings.SessionCookie.name)
      maybeSessionId       <- optionalHeaderValueByName(`X-Session-Id`.name)
    } yield maybeCookieSessionId.map(_.value).orElse(maybeSessionId).getOrElse(idGenerator.nextId())

  /**
    * visitorId is set in cookie and header
    * for web app and native app respectively
    */
  def visitorId: Directive1[String] =
    for {
      maybeCookieVisitorId <- optionalCookie(makeSettings.VisitorCookie.name)
      maybeVisitorId       <- optionalHeaderValueByName(`X-Visitor-Id`.name)
    } yield maybeCookieVisitorId.map(_.value).orElse(maybeVisitorId).getOrElse(idGenerator.nextVisitorId().value)

  def visitorCreatedAt: Directive1[ZonedDateTime] =
    for {
      maybeCookieValue <- optionalCookie(makeSettings.VisitorCookie.createdAtName)
      maybeHeaderValue <- optionalHeaderValueByName(`X-Visitor-CreatedAt`.name)
    } yield {
      maybeCookieValue
        .map(_.value)
        .orElse(maybeHeaderValue)
        .flatMap(creation => Try(ZonedDateTime.parse(creation)).toOption)
        .getOrElse(DateHelper.now())
    }

  def checkEndpointAccess(userRights: Option[UserRights], endpointType: EndpointType): Directive0 = {
    if (mandatoryConnection) {
      checkMandatoryConnectionEndpointAccess(userRights, endpointType)
    } else {
      pass
    }
  }

  def checkMandatoryConnectionEndpointAccess(userRights: Option[UserRights], endpointType: EndpointType): Directive0 = {
    endpointType match {
      case EndpointType.Public   => pass
      case EndpointType.CoreOnly => reject(AuthorizationFailedRejection)
      case EndpointType.Regular =>
        userRights match {
          case Some(UserRights(_, _, _, true)) => pass
          // Reject request if email is not verified
          case Some(UserRights(_, _, _, false)) => reject(EmailNotVerifiedRejection)
          case None =>
            reject(AuthenticationFailedRejection(cause = CredentialsMissing, challenge = HttpChallenges.oAuth2(realm)))
        }
    }
  }

  def addMaybeRefreshedSecureCookie(tokenRefreshed: Option[AccessToken]): Directive0 = {
    respondWithDefaultHeaders {
      Seq(
        tokenRefreshed.map(
          token =>
            `Set-Cookie`(
              HttpCookie(
                name = makeSettings.SecureCookie.name,
                value = token.token,
                secure = makeSettings.SecureCookie.isSecure,
                httpOnly = true,
                maxAge = Some(makeSettings.SecureCookie.lifetime.toSeconds),
                path = Some("/"),
                domain = Some(makeSettings.SecureCookie.domain)
              )
            )
        ),
        tokenRefreshed.map(
          _ =>
            `Set-Cookie`(
              HttpCookie(
                name = makeSettings.SecureCookie.expirationName,
                value = DateHelper.format(DateHelper.now().plusSeconds(makeSettings.Oauth.refreshTokenLifetime)),
                secure = makeSettings.SecureCookie.isSecure,
                httpOnly = false,
                maxAge = Some(365.days.toSeconds),
                path = Some("/"),
                domain = Some(makeSettings.SecureCookie.domain)
              )
            )
        )
      ).collect { case Some(setCookie) => setCookie }
    }
  }

  def addCorsHeaders(origin: Option[String]): Directive0 = {
    respondWithDefaultHeaders {
      defaultCorsHeaders(origin)
    }
  }

  def addMakeHeaders(
    requestId: String,
    routeName: String,
    sessionId: String,
    visitorId: String,
    visitorCreatedAt: ZonedDateTime,
    startTime: Long,
    externalId: String
  ): Directive0 = {
    respondWithDefaultHeaders {
      val sessionExpirationDate =
        DateHelper.format(DateHelper.now().plusSeconds(makeSettings.SessionCookie.lifetime.toSeconds))
      Seq(
        `X-Route-Time`(startTime),
        `X-Request-Id`(requestId),
        `X-Route-Name`(routeName),
        `X-Make-External-Id`(externalId),
        `X-Session-Id`(sessionId),
        `X-Session-Id-Expiration`(sessionExpirationDate),
        `X-Visitor-Id`(visitorId),
        `X-Visitor-CreatedAt`(DateHelper.format(visitorCreatedAt)),
        `Set-Cookie`(
          HttpCookie(
            name = makeSettings.SessionCookie.name,
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
            name = makeSettings.SessionCookie.expirationName,
            value = sessionExpirationDate,
            secure = makeSettings.SessionCookie.isSecure,
            httpOnly = false,
            maxAge = None,
            path = Some("/"),
            domain = Some(makeSettings.SessionCookie.domain)
          )
        ),
        `Set-Cookie`(
          HttpCookie(
            name = makeSettings.VisitorCookie.name,
            value = visitorId,
            secure = makeSettings.VisitorCookie.isSecure,
            httpOnly = true,
            maxAge = Some(365.days.toSeconds),
            path = Some("/"),
            domain = Some(makeSettings.VisitorCookie.domain)
          )
        ),
        `Set-Cookie`(
          HttpCookie(
            name = makeSettings.VisitorCookie.createdAtName,
            value = DateHelper.format(visitorCreatedAt),
            secure = makeSettings.VisitorCookie.isSecure,
            httpOnly = true,
            maxAge = Some(365.days.toSeconds),
            path = Some("/"),
            domain = Some(makeSettings.VisitorCookie.domain)
          )
        )
      )
    }
  }

  private def logRequest(operationName: String, context: RequestContext, origin: Option[String]): Unit = {
    Kamon
      .counter("api-requests")
      .withTags(
        TagSet.from(
          Map(
            "source" -> MonitoringMessageHelper.format(context.source.getOrElse("unknown")),
            "operation" -> operationName,
            "origin" -> MonitoringMessageHelper.format(origin.getOrElse("unknown")),
            "application" -> MonitoringMessageHelper
              .format(context.applicationName.map(_.shortName).getOrElse("unknown")),
            "location" -> MonitoringMessageHelper.format(context.location.getOrElse("unknown")),
            "question" -> MonitoringMessageHelper.format(context.questionId.map(_.value).getOrElse("unknown"))
          )
        )
      )
      .increment()
  }

  private def connectIfNecessary(
    sessionId: SessionId,
    maybeUserId: Option[UserId],
    requestContext: RequestContext
  ): Unit = {
    maybeUserId.foreach(userId => sessionHistoryCoordinatorService.convertSession(sessionId, userId, requestContext))
  }

  def checkTokenExpirationIfNoCookieIsDefined(maybeUser: Option[AuthInfo[UserRights]]): Directive0 = {
    mapInnerRoute { route =>
      maybeUser match {
        // If a user has been found, the token is valid and the route can continue
        case Some(_) => route
        case _ =>
          optionalCookie(makeSettings.SecureCookie.name) {
            // If the make-secure cookie is used, then rely on the cookie and auto-refresh mecanism
            case Some(_) => route
            case None =>
              optionalHeaderValueByType[Authorization](()) {
                // If there is a OAuth2 bearer token, then it is either not found or expired
                case Some(Authorization(OAuth2BearerToken(_))) =>
                  reject(AuthenticationFailedRejection(CredentialsRejected, HttpChallenges.oAuth2(realm)))
                // If no authentication is sent or it is not OAuth, continue
                case _ => route
              }
          }
      }
    }

  }

  def makeOperation(name: String, endpointType: EndpointType = EndpointType.Regular): Directive1[RequestContext] = {
    val slugifiedName: String = SlugHelper(name)
    Tracing.entrypoint(slugifiedName)

    for {

      requestId            <- requestId
      _                    <- operationName(slugifiedName)
      origin               <- optionalHeaderValueByName(Origin.name)
      _                    <- addCorsHeaders(origin)
      _                    <- encodeResponse
      startTime            <- startTime
      sessionId            <- sessionId
      visitorId            <- visitorId
      visitorCreatedAt     <- visitorCreatedAt
      externalId           <- optionalHeaderValueByName(`X-Make-External-Id`.name).map(_.getOrElse(requestId))
      _                    <- addMakeHeaders(requestId, slugifiedName, sessionId, visitorId, visitorCreatedAt, startTime, externalId)
      _                    <- handleExceptions(MakeApi.exceptionHandler(slugifiedName, requestId))
      _                    <- handleRejections(MakeApi.rejectionHandler)
      maybeTokenRefreshed  <- makeTriggerAuthRefreshFromCookie()
      _                    <- addMaybeRefreshedSecureCookie(maybeTokenRefreshed)
      _                    <- makeAuthCookieHandlers(maybeTokenRefreshed)
      maybeIpAddress       <- extractClientIP
      maybeUser            <- optionalMakeOAuth2
      _                    <- checkTokenExpirationIfNoCookieIsDefined(maybeUser)
      _                    <- checkEndpointAccess(maybeUser.map(_.user), endpointType)
      maybeUserAgent       <- optionalHeaderValueByName(`User-Agent`.name)
      maybeOperation       <- optionalHeaderValueByName(`X-Make-Operation`.name)
      maybeSource          <- optionalHeaderValueByName(`X-Make-Source`.name)
      maybeLocation        <- optionalHeaderValueByName(`X-Make-Location`.name)
      maybeQuestion        <- optionalHeaderValueByName(`X-Make-Question`.name)
      maybeCountry         <- optionalHeaderValueByName(`X-Make-Country`.name)
      maybeDetectedCountry <- optionalHeaderValueByName(`X-Detected-Country`.name)
      maybeLanguage        <- optionalHeaderValueByName(`X-Make-Language`.name)
      maybeHostName        <- optionalHeaderValueByName(`X-Hostname`.name)
      maybeGetParameters   <- optionalHeaderValueByName(`X-Get-Parameters`.name)
      maybeQuestionId      <- optionalHeaderValueByName(`X-Make-Question-Id`.name)
      maybeApplicationName <- optionalHeaderValueByName(`X-Make-App-Name`.name)
      maybeReferrer        <- optionalHeaderValueByName(`X-Make-Referrer`.name)
      maybeCustomData      <- optionalHeaderValueByName(`X-Make-Custom-Data`.name)
    } yield {
      val requestContext = RequestContext(
        currentTheme = None,
        userId = maybeUser.map(_.user.userId),
        requestId = requestId,
        sessionId = SessionId(sessionId),
        visitorId = Some(VisitorId(visitorId)),
        visitorCreatedAt = Some(visitorCreatedAt),
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
        userAgent = maybeUserAgent,
        questionId = maybeQuestionId.map(QuestionId.apply),
        applicationName = maybeApplicationName.flatMap(ApplicationName.applicationMap.get),
        referrer = maybeReferrer,
        customData = maybeCustomData
          .map(
            customData =>
              URLDecoder
                .decode(customData, "UTF-8")
                .split(",")
                .map(_.split("=", 2))
                .map {
                  case Array(key, value) => key.trim -> value.trim
                  case Array(key)        => key.trim -> ""
                }
                .toMap
          )
          .getOrElse(Map.empty)
      )
      logRequest(name, requestContext, origin)
      connectIfNecessary(SessionId(sessionId), maybeUser.map(_.user.userId), requestContext)
      requestContext
    }
  }

  def provideAsync[T](provider: => Future[T]): Directive1[T] =
    extract(_ => provider).flatMap { fa =>
      onComplete(fa).flatMap {
        case Success(value) => provide(value)
        case Failure(e)     => failWith(e)
      }
    }

  def provideAsyncOrNotFound[T](provider: => Future[Option[T]]): Directive1[T] =
    extract(_ => provider).flatMap { fa =>
      onComplete(fa).flatMap {
        case Success(Some(value)) => provide(value)
        case Success(None)        => complete(StatusCodes.NotFound)
        case Failure(e)           => failWith(e)
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
          `X-Request-Id`.name,
          `X-Route-Name`.name,
          `X-Route-Time`.name,
          `X-Make-External-Id`.name,
          `X-Session-Id`.name,
          `X-Total-Count`.name
        )
      )
    ) ++ mayBeOriginValue.map { httpOrigin =>
      `Access-Control-Allow-Origin`(origin = httpOrigin)
    }

  }

  def makeAuthCookieHandlers(maybeRefreshedToken: Option[AccessToken]): Directive0 =
    mapInnerRoute { route =>
      optionalCookie(makeSettings.SecureCookie.name) {
        case Some(secureCookie) =>
          val credentials: OAuth2BearerToken = maybeRefreshedToken match {
            case Some(refreshedToken) => OAuth2BearerToken(refreshedToken.token)
            case None                 => OAuth2BearerToken(secureCookie.value)
          }
          mapRequest((request: HttpRequest) => request.addCredentials(credentials)) {
            route
          }
        case None => route
      }
    }

  def makeTriggerAuthRefreshFromCookie(): Directive1[Option[AccessToken]] =
    optionalCookie(makeSettings.SecureCookie.name).flatMap {
      case Some(secureCookie) =>
        provideAsync[Option[AccessToken]](oauth2DataHandler.refreshIfTokenIsExpired(secureCookie.value))
      case None => provide[Option[AccessToken]](None)
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

  def uploadImageAsync(
    imageFieldName: String,
    uploadFile: (String, String, Content) => Future[String],
    sizeLimit: Option[Long]
  ): Directive[(String, File)] = {
    val sizeDirective = sizeLimit match {
      case Some(size) => withSizeLimit(size)
      case _          => withoutSizeLimit
    }
    sizeDirective.tflatMap { _ =>
      storeUploadedFile(
        imageFieldName,
        fileInfo => Files.createTempFile("makeapi", FileHelper.getExtension(fileInfo)).toFile
      ).tflatMap {
        case (info, file) =>
          file.deleteOnExit()
          val contentType = info.contentType
          Validation.validate(
            validateField(imageFieldName, "invalid_format", contentType.mediaType.isImage, "File must be an image")
          )
          val extension = FileHelper.getExtension(info)
          provideAsync(uploadFile(extension, contentType.value, FileContent(file))).map { path =>
            (path, file)
          }
      }
    }
  }

  def setMakeSecure(access_token: String, userId: UserId): Directive0 = {
    mapResponseHeaders { responseHeaders =>
      if (responseHeaders.exists {
            case `Set-Cookie`(cookie) => cookie.name == makeSettings.SecureCookie.name
            case _                    => false
          }) {
        responseHeaders
      } else {
        responseHeaders ++ Seq(
          `Set-Cookie`(
            HttpCookie(
              name = makeSettings.SecureCookie.name,
              value = access_token,
              secure = makeSettings.SecureCookie.isSecure,
              httpOnly = true,
              maxAge = Some(makeSettings.SecureCookie.lifetime.toSeconds),
              path = Some("/"),
              domain = Some(makeSettings.SecureCookie.domain)
            )
          ),
          `Set-Cookie`(
            HttpCookie(
              name = makeSettings.SecureCookie.expirationName,
              value = DateHelper
                .format(DateHelper.now().plusSeconds(makeSettings.SecureCookie.lifetime.toSeconds)),
              secure = makeSettings.SecureCookie.isSecure,
              httpOnly = false,
              maxAge = Some(365.days.toSeconds),
              path = Some("/"),
              domain = Some(makeSettings.SecureCookie.domain)
            )
          ),
          `Set-Cookie`(
            HttpCookie(
              name = makeSettings.UserIdCookie.name,
              value = userId.value,
              secure = makeSettings.UserIdCookie.isSecure,
              httpOnly = true,
              maxAge = Some(365.days.toSeconds),
              path = Some("/"),
              domain = Some(makeSettings.UserIdCookie.domain)
            )
          )
        )
      }
    }
  }
}

sealed trait EndpointType

object EndpointType {
  // represents an endpoint that can be called connected or unconnected
  case object Public extends EndpointType
  // Represents an endpoints that requires connection if mandatory-connection is activated in the configuration
  case object Regular extends EndpointType
  // Represents an endpoint forbidden if connection is mandatory
  case object CoreOnly extends EndpointType
}

final case class `X-Request-Id`(override val value: String) extends ModeledCustomHeader[`X-Request-Id`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Request-Id`] = `X-Request-Id`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = true
}

object `X-Request-Id` extends ModeledCustomHeaderCompanion[`X-Request-Id`] {
  override val name: String = "x-request-id"
  override def parse(value: String): Try[`X-Request-Id`] = Success(new `X-Request-Id`(value))
}

final case class `X-Route-Name`(override val value: String) extends ModeledCustomHeader[`X-Route-Name`] {
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = true
  override def companion: ModeledCustomHeaderCompanion[`X-Route-Name`] = `X-Route-Name`
}

object `X-Route-Name` extends ModeledCustomHeaderCompanion[`X-Route-Name`] {
  override val name: String = "x-route-name"
  override def parse(value: String): Try[`X-Route-Name`] = Success(new `X-Route-Name`(value))
}

final case class `X-Route-Time`(override val value: String) extends ModeledCustomHeader[`X-Route-Time`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Route-Time`] = `X-Route-Time`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = true
}

object `X-Route-Time` extends ModeledCustomHeaderCompanion[`X-Route-Time`] {
  override val name: String = "x-route-time"
  override def parse(value: String): Try[`X-Route-Time`] =
    Failure(new NotImplementedError("Please use the apply from long instead"))
  def apply(startTimeMillis: Long): `X-Route-Time` =
    new `X-Route-Time`((System.currentTimeMillis() - startTimeMillis).toString)
}

final case class `X-Make-External-Id`(override val value: String) extends ModeledCustomHeader[`X-Make-External-Id`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Make-External-Id`] = `X-Make-External-Id`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = true
}

object `X-Make-External-Id` extends ModeledCustomHeaderCompanion[`X-Make-External-Id`] {
  override val name: String = "x-make-external-id"

  override def parse(value: String): Try[`X-Make-External-Id`] = Success(new `X-Make-External-Id`(value))
}

trait MakeAuthenticationDirectives extends MakeAuthentication {
  this: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent =>

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

case object EmailNotVerifiedRejection extends Rejection

final case class `X-Make-Operation`(override val value: String) extends ModeledCustomHeader[`X-Make-Operation`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Make-Operation`] = `X-Make-Operation`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object `X-Make-Operation` extends ModeledCustomHeaderCompanion[`X-Make-Operation`] {
  override val name: String = "x-make-operation"
  override def parse(value: String): Try[`X-Make-Operation`] = Success(new `X-Make-Operation`(value))
}

final case class `X-Make-Source`(override val value: String) extends ModeledCustomHeader[`X-Make-Source`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Make-Source`] = `X-Make-Source`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object `X-Make-Source` extends ModeledCustomHeaderCompanion[`X-Make-Source`] {
  override val name: String = "x-make-source"
  override def parse(value: String): Try[`X-Make-Source`] = Success(new `X-Make-Source`(value))
}

final case class `X-Make-Location`(override val value: String) extends ModeledCustomHeader[`X-Make-Location`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Make-Location`] = `X-Make-Location`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object `X-Make-Location` extends ModeledCustomHeaderCompanion[`X-Make-Location`] {
  override val name: String = "x-make-location"
  override def parse(value: String): Try[`X-Make-Location`] = Success(new `X-Make-Location`(value))
}

final case class `X-Make-Question`(override val value: String) extends ModeledCustomHeader[`X-Make-Question`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Make-Question`] = `X-Make-Question`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object `X-Make-Question` extends ModeledCustomHeaderCompanion[`X-Make-Question`] {
  override val name: String = "x-make-question"
  override def parse(value: String): Try[`X-Make-Question`] = Success(new `X-Make-Question`(value))
}

final case class `X-Make-Language`(override val value: String) extends ModeledCustomHeader[`X-Make-Language`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Make-Language`] = `X-Make-Language`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object `X-Make-Language` extends ModeledCustomHeaderCompanion[`X-Make-Language`] {
  override val name: String = "x-make-language"
  override def parse(value: String): Try[`X-Make-Language`] = Success(new `X-Make-Language`(value))
}

final case class `X-Make-Country`(override val value: String) extends ModeledCustomHeader[`X-Make-Country`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Make-Country`] = `X-Make-Country`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object `X-Make-Country` extends ModeledCustomHeaderCompanion[`X-Make-Country`] {
  override val name: String = "x-make-country"
  override def parse(value: String): Try[`X-Make-Country`] = Success(new `X-Make-Country`(value))
}

final case class `X-Detected-Country`(override val value: String) extends ModeledCustomHeader[`X-Detected-Country`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Detected-Country`] = `X-Detected-Country`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object `X-Detected-Country` extends ModeledCustomHeaderCompanion[`X-Detected-Country`] {
  override val name: String = "x-detected-country"
  override def parse(value: String): Try[`X-Detected-Country`] = Success(new `X-Detected-Country`(value))
}

final case class `X-Hostname`(override val value: String) extends ModeledCustomHeader[`X-Hostname`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Hostname`] = `X-Hostname`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object `X-Hostname` extends ModeledCustomHeaderCompanion[`X-Hostname`] {
  override val name: String = "x-hostname"
  override def parse(value: String): Try[`X-Hostname`] = Success(new `X-Hostname`(value))
}

final case class `X-Get-Parameters`(override val value: String) extends ModeledCustomHeader[`X-Get-Parameters`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Get-Parameters`] = `X-Get-Parameters`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object `X-Get-Parameters` extends ModeledCustomHeaderCompanion[`X-Get-Parameters`] {
  override val name: String = "x-get-parameters"
  override def parse(value: String): Try[`X-Get-Parameters`] = Success(new `X-Get-Parameters`(value))
}

final case class `X-Session-Id`(override val value: String) extends ModeledCustomHeader[`X-Session-Id`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Session-Id`] = `X-Session-Id`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = true
}

object `X-Session-Id` extends ModeledCustomHeaderCompanion[`X-Session-Id`] {
  override val name: String = "x-session-id"
  override def parse(value: String): Try[`X-Session-Id`] = Success(new `X-Session-Id`(value))
}

final case class `X-Session-Id-Expiration`(override val value: String)
    extends ModeledCustomHeader[`X-Session-Id-Expiration`] {

  override def companion: ModeledCustomHeaderCompanion[`X-Session-Id-Expiration`] =
    `X-Session-Id-Expiration`
  override def renderInRequests: Boolean = false
  override def renderInResponses: Boolean = true
}

object `X-Session-Id-Expiration` extends ModeledCustomHeaderCompanion[`X-Session-Id-Expiration`] {
  override val name: String = "x-session-id-expiration"
  override def parse(value: String): Try[`X-Session-Id-Expiration`] = Success(new `X-Session-Id-Expiration`(value))
}

final case class `X-Total-Count`(override val value: String) extends ModeledCustomHeader[`X-Total-Count`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Total-Count`] = `X-Total-Count`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = true
}

object `X-Total-Count` extends ModeledCustomHeaderCompanion[`X-Total-Count`] {
  val name: String = "x-total-count"
  def parse(value: String): Try[`X-Total-Count`] = Success(new `X-Total-Count`(value))
}

final case class `X-Visitor-Id`(override val value: String) extends ModeledCustomHeader[`X-Visitor-Id`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Visitor-Id`] = `X-Visitor-Id`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = true
}

object `X-Visitor-Id` extends ModeledCustomHeaderCompanion[`X-Visitor-Id`] {
  override val name: String = "x-visitor-id"
  override def parse(value: String): Try[`X-Visitor-Id`] = Success(new `X-Visitor-Id`(value))
}

final case class `X-Visitor-CreatedAt`(override val value: String) extends ModeledCustomHeader[`X-Visitor-CreatedAt`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Visitor-CreatedAt`] = `X-Visitor-CreatedAt`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = true
}

object `X-Visitor-CreatedAt` extends ModeledCustomHeaderCompanion[`X-Visitor-CreatedAt`] {
  override val name: String = "x-visitor-created-at"
  override def parse(value: String): Try[`X-Visitor-CreatedAt`] = Success(new `X-Visitor-CreatedAt`(value))
}

final case class `X-Make-Question-Id`(override val value: String) extends ModeledCustomHeader[`X-Make-Question-Id`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Make-Question-Id`] = `X-Make-Question-Id`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object `X-Make-Question-Id` extends ModeledCustomHeaderCompanion[`X-Make-Question-Id`] {
  override val name: String = "x-make-question-id"
  override def parse(value: String): Try[`X-Make-Question-Id`] = Success(new `X-Make-Question-Id`(value))
}

final case class `X-Make-App-Name`(override val value: String) extends ModeledCustomHeader[`X-Make-App-Name`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Make-App-Name`] = `X-Make-App-Name`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object `X-Make-App-Name` extends ModeledCustomHeaderCompanion[`X-Make-App-Name`] {
  override val name: String = "x-make-app-name"
  override def parse(value: String): Try[`X-Make-App-Name`] = Success(new `X-Make-App-Name`(value))
}

final case class `X-Make-Referrer`(override val value: String) extends ModeledCustomHeader[`X-Make-Referrer`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Make-Referrer`] = `X-Make-Referrer`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object `X-Make-Referrer` extends ModeledCustomHeaderCompanion[`X-Make-Referrer`] {
  override val name: String = "x-make-referrer"
  override def parse(value: String): Try[`X-Make-Referrer`] = Success(new `X-Make-Referrer`(value))
}

final case class `X-Make-Custom-Data`(override val value: String) extends ModeledCustomHeader[`X-Make-Custom-Data`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Make-Custom-Data`] = `X-Make-Custom-Data`
  override def renderInRequests: Boolean = true
  override def renderInResponses: Boolean = false
}

object `X-Make-Custom-Data` extends ModeledCustomHeaderCompanion[`X-Make-Custom-Data`] {
  override val name: String = "x-make-custom-data"
  override def parse(value: String): Try[`X-Make-Custom-Data`] = Success(new `X-Make-Custom-Data`(value))
}
