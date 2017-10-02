package org.make.core

import org.make.core.reference.ThemeId
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import spray.json.DefaultJsonProtocol._

final case class RequestContext(currentTheme: Option[ThemeId],
                                requestId: String,
                                sessionId: String,
                                externalId: String,
                                country: Option[String],
                                language: Option[String],
                                operation: Option[String],
                                source: Option[String],
                                location: Option[String],
                                question: Option[String])

object RequestContext {
  val empty: RequestContext = RequestContext(None, "", "", "", None, None, None, None, None, None)

  implicit val requestContextFormatter: RootJsonFormat[RequestContext] =
    DefaultJsonProtocol.jsonFormat10(RequestContext.apply)

}
