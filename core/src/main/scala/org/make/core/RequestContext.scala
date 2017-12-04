package org.make.core

import io.circe.{Decoder, ObjectEncoder}
import org.make.core.reference.ThemeId
import org.make.core.session.SessionId
import io.circe.generic.semiauto._
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

final case class RequestContext(currentTheme: Option[ThemeId],
                                requestId: String,
                                sessionId: SessionId,
                                externalId: String,
                                country: Option[String],
                                language: Option[String],
                                operation: Option[String],
                                source: Option[String],
                                location: Option[String],
                                question: Option[String],
                                hostname: Option[String] = None,
                                ipAddress: Option[String] = None,
                                getParameters: Option[Map[String, String]] = None)

object RequestContext {
  implicit val encoder: ObjectEncoder[RequestContext] = deriveEncoder[RequestContext]
  implicit val decoder: Decoder[RequestContext] = deriveDecoder[RequestContext]

  val empty: RequestContext =
    RequestContext(None, "", SessionId(""), "", None, None, None, None, None, None, None, None, None)

  implicit val requestContextFormatter: RootJsonFormat[RequestContext] =
    DefaultJsonProtocol.jsonFormat13(RequestContext.apply)

}
