package org.make.core

import io.circe.generic.semiauto._
import io.circe.{Decoder, ObjectEncoder}
import org.make.core.operation.OperationId
import org.make.core.reference.ThemeId
import org.make.core.session.{SessionId, VisitorId}
import org.make.core.user.UserId
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

final case class RequestContext(currentTheme: Option[ThemeId],
                                userId: Option[UserId] = None,
                                requestId: String,
                                sessionId: SessionId,
                                visitorId: Option[VisitorId] = None,
                                externalId: String,
                                country: Option[String],
                                detectedCountry: Option[String] = None,
                                language: Option[String],
                                operationId: Option[OperationId] = None,
                                source: Option[String],
                                location: Option[String],
                                question: Option[String],
                                hostname: Option[String] = None,
                                ipAddress: Option[String] = None,
                                getParameters: Option[Map[String, String]] = None,
                                userAgent: Option[String] = None)

object RequestContext {
  implicit val encoder: ObjectEncoder[RequestContext] = deriveEncoder[RequestContext]
  implicit val decoder: Decoder[RequestContext] = deriveDecoder[RequestContext]

  val empty: RequestContext =
    RequestContext(
      currentTheme = None,
      userId = None,
      requestId = "",
      sessionId = SessionId(""),
      visitorId = None,
      externalId = "",
      country = None,
      detectedCountry = None,
      language = None,
      operationId = None,
      source = None,
      location = None,
      question = None,
      hostname = None,
      ipAddress = None,
      getParameters = None,
      userAgent = None
    )

  implicit val requestContextFormatter: RootJsonFormat[RequestContext] =
    DefaultJsonProtocol.jsonFormat17(RequestContext.apply)

}
