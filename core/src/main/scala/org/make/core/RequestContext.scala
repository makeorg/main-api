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
import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import io.swagger.annotations.ApiModelProperty
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.session.{SessionId, VisitorId}
import org.make.core.user.UserId
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

import scala.annotation.meta.field

sealed trait ApplicationName {
  def shortName: String
}

object ApplicationName {
  case object MainFrontend extends ApplicationName {
    val shortName: String = "main-front"
  }
  case object LegacyFrontend extends ApplicationName {
    val shortName: String = "legacy-front"
  }
  case object Backoffice extends ApplicationName {
    val shortName: String = "backoffice"
  }
  case object Widget extends ApplicationName {
    val shortName: String = "widget"
  }
  case object Dial extends ApplicationName {
    val shortName: String = "dial"
  }
  case object BiBatchs extends ApplicationName {
    val shortName: String = "bi-batchs"
  }
  case object DialBatchs extends ApplicationName {
    val shortName: String = "dial-batchs"
  }
  val applicationMap: Map[String, ApplicationName] =
    Map(
      MainFrontend.shortName -> MainFrontend,
      LegacyFrontend.shortName -> LegacyFrontend,
      Backoffice.shortName -> Backoffice,
      Widget.shortName -> Widget,
      Dial.shortName -> Dial,
      BiBatchs.shortName -> BiBatchs,
      DialBatchs.shortName -> DialBatchs
    )

  implicit lazy val encoder: Encoder[ApplicationName] = (applicationName: ApplicationName) =>
    Json.fromString(applicationName.shortName)
  implicit lazy val decoder: Decoder[ApplicationName] =
    Decoder.decodeString.emap { value: String =>
      applicationMap.get(value) match {
        case Some(application) => Right(application)
        case None              => Left(s"$value is not an application name")
      }
    }

  implicit val applicationNameFormatter: JsonFormat[ApplicationName] = new JsonFormat[ApplicationName] {
    override def read(json: JsValue): ApplicationName = json match {
      case JsString(s) => applicationMap(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: ApplicationName): JsValue = {
      JsString(obj.shortName)
    }
  }
}

final case class RequestContext(
  @(ApiModelProperty @field)(dataType = "string", example = "9aff4846-3cb8-4737-aea0-2c4a608f30fd")
  currentTheme: Option[ThemeId],
  @(ApiModelProperty @field)(dataType = "string", example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55")
  userId: Option[UserId] = None,
  requestId: String,
  @(ApiModelProperty @field)(dataType = "string", example = "af938667-a15a-482b-bd0f-681f09c83e51")
  sessionId: SessionId,
  @(ApiModelProperty @field)(dataType = "string", example = "e52d2ac3-a929-43ec-acfa-fb1f486a8c75")
  visitorId: Option[VisitorId] = None,
  externalId: String,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Option[Country],
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  detectedCountry: Option[Country] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Option[Language],
  @(ApiModelProperty @field)(dataType = "string", example = "3a9cd696-7e0b-4758-952c-04ae6798039a")
  operationId: Option[OperationId] = None,
  source: Option[String],
  location: Option[String],
  question: Option[String],
  hostname: Option[String] = None,
  ipAddress: Option[String] = None,
  getParameters: Option[Map[String, String]] = None,
  userAgent: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "2d791a66-3cd5-4a2e-a117-9daa68bd3a33")
  questionId: Option[QuestionId] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "main-front")
  applicationName: Option[ApplicationName] = None
)

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
      userAgent = None,
      questionId = None,
      applicationName = None
    )

  implicit val requestContextFormatter: RootJsonFormat[RequestContext] =
    DefaultJsonProtocol.jsonFormat19(RequestContext.apply)

}
