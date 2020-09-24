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

import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json}
import org.make.core.SprayJsonFormatters._
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.{CirceFormatters, MakeSerializable, SprayJsonFormatters, StringValue, Timestamped}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

final case class Idea(
  ideaId: IdeaId,
  name: String,
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
    DefaultJsonProtocol.jsonFormat8(Idea.apply)

  implicit val encoder: Encoder[Idea] = deriveEncoder[Idea]
  implicit val decoder: Decoder[Idea] = deriveDecoder[Idea]
}

final case class IdeaId(value: String) extends StringValue

object IdeaId {
  implicit lazy val ideaIdEncoder: Encoder[IdeaId] =
    (a: IdeaId) => Json.fromString(a.value)
  implicit lazy val ideaIdDecoder: Decoder[IdeaId] =
    Decoder.decodeString.map(IdeaId(_))

  implicit val ideaIdFormatter: JsonFormat[IdeaId] = SprayJsonFormatters.forStringValue(IdeaId.apply)
}

sealed abstract class IdeaStatus(val value: String) extends StringEnumEntry

object IdeaStatus extends StringEnum[IdeaStatus] with StringCirceEnum[IdeaStatus] {

  case object Archived extends IdeaStatus("Archived")
  case object Activated extends IdeaStatus("Activated")

  override def values: IndexedSeq[IdeaStatus] = findValues

}
