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

import java.time.ZonedDateTime

import com.sksamuel.avro4s
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import io.swagger.annotations.ApiModelProperty
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.session.{SessionId, VisitorId}
import org.make.core.user.UserId
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.annotation.meta.field

sealed abstract class ApplicationName(val value: String) extends StringEnumEntry

object ApplicationName extends StringEnum[ApplicationName] with StringCirceEnum[ApplicationName] {

  case object Backoffice extends ApplicationName("backoffice")
  case object BiBatchs extends ApplicationName("bi-batchs")
  case object ConcertationBeta extends ApplicationName("concertation-beta")
  case object Dial extends ApplicationName("dial")
  case object DialBatchs extends ApplicationName("dial-batchs")
  case object Infrastructure extends ApplicationName("infra")
  case object LegacyFrontend extends ApplicationName("legacy-front")
  case object MainFrontend extends ApplicationName("main-front")
  case object OldBackoffice extends ApplicationName("bo")
  case object Widget extends ApplicationName("widget")
  case object WidgetManager extends ApplicationName("widget-manager")

  override def values: IndexedSeq[ApplicationName] = findValues

}

final case class RequestContext(
  @(ApiModelProperty @field)(dataType = "string", example = "9aff4846-3cb8-4737-aea0-2c4a608f30fd")
  currentTheme: Option[ThemeId],
  @(ApiModelProperty @field)(dataType = "string", example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55")
  userId: Option[UserId] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "40ab2307-8ebf-4da6-8eb5-9e23b7c4deb0")
  requestId: String,
  @(ApiModelProperty @field)(dataType = "string", example = "af938667-a15a-482b-bd0f-681f09c83e51")
  sessionId: SessionId,
  @(ApiModelProperty @field)(dataType = "string", example = "e52d2ac3-a929-43ec-acfa-fb1f486a8c75")
  visitorId: Option[VisitorId] = None,
  @(ApiModelProperty @field)(dataType = "dateTime")
  visitorCreatedAt: Option[ZonedDateTime] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "cc3b8732-b8c6-4bf8-9f4f-5b2ba8e4e8c4")
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
  @(ApiModelProperty @field)(dataType = "string", example = "0.0.0.0")
  ipAddress: Option[String] = None,
  getParameters: Option[Map[String, String]] = None,
  userAgent: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "2d791a66-3cd5-4a2e-a117-9daa68bd3a33")
  questionId: Option[QuestionId] = None,
  @(ApiModelProperty @field)(
    dataType = "string",
    example = "main-front",
    allowableValues = "main-front,legacy-front,backoffice,widget,widget-manager,dial,bi-batchs,dial-batchs,infra"
  )
  applicationName: Option[ApplicationName] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "main-front")
  referrer: Option[String] = None,
  customData: Map[String, String] = Map.empty
)

object RequestContext extends CirceFormatters with SprayJsonFormatters with AvroSerializers {
  lazy val schemaFor: SchemaFor[RequestContext] = SchemaFor.gen[RequestContext]
  implicit lazy val fromRecord: avro4s.Decoder[RequestContext] = avro4s.Decoder.gen[RequestContext]
  implicit lazy val toRecord: avro4s.Encoder[RequestContext] = avro4s.Encoder.gen[RequestContext]
  lazy val recordFormat: RecordFormat[RequestContext] = RecordFormat[RequestContext]

  implicit val encoder: Encoder[RequestContext] = deriveEncoder[RequestContext]
  implicit val decoder: Decoder[RequestContext] = deriveDecoder[RequestContext]

  val empty: RequestContext =
    RequestContext(
      currentTheme = None,
      userId = None,
      requestId = "",
      sessionId = SessionId(""),
      visitorId = None,
      visitorCreatedAt = None,
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
      applicationName = None,
      referrer = None,
      customData = Map.empty
    )

  implicit val requestContextFormatter: RootJsonFormat[RequestContext] =
    DefaultJsonProtocol.jsonFormat22(RequestContext.apply)

}
