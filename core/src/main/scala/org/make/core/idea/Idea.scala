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
import org.make.core.SprayJsonFormatters._
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.{CirceFormatters, MakeSerializable, StringValue, Timestamped}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

final case class Idea(
  ideaId: IdeaId,
  name: String,
  language: Option[Language] = None,
  country: Option[Country] = None,
  question: Option[String] = None,
  operationId: Option[OperationId] = None,
  questionId: Option[QuestionId] = None,
  status: IdeaStatus = IdeaStatus.Activated,
  override val createdAt: Option[ZonedDateTime],
  override val updatedAt: Option[ZonedDateTime]
) extends MakeSerializable
    with Timestamped

object Idea extends CirceFormatters {

  implicit val ideaFormatter: RootJsonFormat[Idea] =
    DefaultJsonProtocol.jsonFormat10(Idea.apply)

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
