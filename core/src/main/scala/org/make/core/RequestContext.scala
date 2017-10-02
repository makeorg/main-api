package org.make.core

import org.make.core.reference.ThemeId
import org.make.core.session.SessionId
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import spray.json.DefaultJsonProtocol._

final case class RequestContext(currentTheme: Option[ThemeId],
                                requestId: String,
                                sessionId: SessionId,
                                externalId: String,
                                country: Option[String],
                                language: Option[String],
                                operation: Option[String],
                                source: Option[String],
                                location: Option[String],
                                question: Option[String])

object RequestContext {
  val empty: RequestContext = RequestContext(None, "", SessionId(""), "", None, None, None, None, None, None)

  implicit val requestContextFormatter: RootJsonFormat[RequestContext] =
    DefaultJsonProtocol.jsonFormat10(RequestContext.apply)

}
