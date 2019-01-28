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

package org.make.core.idea

import java.time.ZonedDateTime

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json}
import io.swagger.annotations.ApiModelProperty
import org.make.core.SprayJsonFormatters._
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.{CirceFormatters, MakeSerializable, StringValue, Timestamped}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

import scala.annotation.meta.field

final case class Idea(
  @(ApiModelProperty @field)(dataType = "string", example = "a10086bb-4312-4486-8f57-91b5e92b3eb9") ideaId: IdeaId,
  name: String,
  @(ApiModelProperty @field)(dataType = "string", example = "fr") language: Option[Language] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Option[Country] = None,
  question: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "f4767b7b-06c1-479d-8bc1-6e2a2de97f22") operationId: Option[
    OperationId
  ] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "57b1d160-2593-46bd-b7ad-f5e99ba3aa0d") questionId: Option[
    QuestionId
  ] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "e65fb52e-6438-4074-a79f-adb38fdee544") themeId: Option[
    ThemeId
  ] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "Activated") status: IdeaStatus = IdeaStatus.Activated,
  @(ApiModelProperty @field)(dataType = "string", example = "2019-01-23T12:12:12.012Z") override val createdAt: Option[
    ZonedDateTime
  ],
  @(ApiModelProperty @field)(dataType = "string", example = "2019-01-23T12:12:12.012Z") override val updatedAt: Option[
    ZonedDateTime
  ]
) extends MakeSerializable
    with Timestamped

object Idea extends CirceFormatters {

  implicit val ideaFormatter: RootJsonFormat[Idea] =
    DefaultJsonProtocol.jsonFormat11(Idea.apply)

  implicit val encoder: Encoder[Idea] = deriveEncoder[Idea]
  implicit val decoder: Decoder[Idea] = deriveDecoder[Idea]
}

final case class IdeaId(value: String) extends StringValue

object IdeaId {
  implicit lazy val ideaIdEncoder: Encoder[IdeaId] =
    (a: IdeaId) => Json.fromString(a.value)
  implicit lazy val ideaIdDecoder: Decoder[IdeaId] =
    Decoder.decodeString.map(IdeaId(_))

  implicit val ideaIdFormatter: JsonFormat[IdeaId] = new JsonFormat[IdeaId] {
    override def read(json: JsValue): IdeaId = json match {
      case JsString(value) => IdeaId(value)
      case other           => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: IdeaId): JsValue = {
      JsString(obj.value)
    }
  }
}

sealed trait IdeaStatus {
  def shortName: String
}

object IdeaStatus {
  val statusMap: Map[String, IdeaStatus] =
    Map(Activated.shortName -> Activated, Archived.shortName -> Archived)

  implicit lazy val ideaStatusEncoder: Encoder[IdeaStatus] = (status: IdeaStatus) => Json.fromString(status.shortName)
  implicit lazy val ideaStatusDecoder: Decoder[IdeaStatus] =
    Decoder.decodeString.emap { value: String =>
      statusMap.get(value) match {
        case Some(status) => Right(status)
        case None         => Left(s"$value is not an idea status")
      }
    }

  implicit val ideaStatusFormatted: JsonFormat[IdeaStatus] = new JsonFormat[IdeaStatus] {
    override def read(json: JsValue): IdeaStatus = json match {
      case JsString(s) => IdeaStatus.statusMap(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: IdeaStatus): JsValue = {
      JsString(obj.shortName)
    }
  }

  case object Archived extends IdeaStatus {
    override val shortName = "Archived"
  }

  case object Activated extends IdeaStatus {
    override val shortName = "Activated"
  }
}
