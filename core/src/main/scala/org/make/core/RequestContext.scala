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

package org.make.core

import io.circe.generic.semiauto._
import io.circe.{Decoder, ObjectEncoder}
import org.make.core.operation.OperationId
import org.make.core.reference.{Country, Language, ThemeId}
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
                                country: Option[Country],
                                detectedCountry: Option[Country] = None,
                                language: Option[Language],
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
