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

package org.make.core.sequence

import java.time.ZonedDateTime

import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json}
import org.make.core.SprayJsonFormatters._
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalId
import org.make.core.reference.Language
import org.make.core.user.UserId
import org.make.core.{MakeSerializable, RequestContext, StringValue, Timestamped}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

final case class SequenceTranslation(slug: String, title: String, language: Language) extends MakeSerializable

object SequenceTranslation {
  implicit val encoder: Encoder[SequenceTranslation] = deriveEncoder[SequenceTranslation]
  implicit val decoder: Decoder[SequenceTranslation] = deriveDecoder[SequenceTranslation]

  implicit val sequenceTranslationFormatter: RootJsonFormat[SequenceTranslation] =
    DefaultJsonProtocol.jsonFormat3(SequenceTranslation.apply)

}
final case class SequenceAction(date: ZonedDateTime, user: UserId, actionType: String, arguments: Map[String, String])

object SequenceAction {
  implicit val sequenceActionFormatter: RootJsonFormat[SequenceAction] =
    DefaultJsonProtocol.jsonFormat4(SequenceAction.apply)
}

case class Sequence(
  sequenceId: SequenceId,
  title: String,
  slug: String,
  proposalIds: Seq[ProposalId] = Seq.empty,
  operationId: Option[OperationId] = None,
  override val createdAt: Option[ZonedDateTime],
  override val updatedAt: Option[ZonedDateTime],
  status: SequenceStatus = SequenceStatus.Unpublished,
  creationContext: RequestContext,
  sequenceTranslation: Seq[SequenceTranslation] = Seq.empty,
  events: List[SequenceAction],
  searchable: Boolean
) extends MakeSerializable
    with Timestamped

object Sequence {
  implicit val sequenceFormatter: RootJsonFormat[Sequence] =
    DefaultJsonProtocol.jsonFormat12(Sequence.apply)
}

final case class SequenceId(value: String) extends StringValue

object SequenceId {
  implicit lazy val sequenceIdEncoder: Encoder[SequenceId] =
    (a: SequenceId) => Json.fromString(a.value)
  implicit lazy val sequenceIdDecoder: Decoder[SequenceId] =
    Decoder.decodeString.map(SequenceId(_))

  implicit val sequenceIdFormatter: JsonFormat[SequenceId] = new JsonFormat[SequenceId] {
    override def read(json: JsValue): SequenceId = json match {
      case JsString(s) => SequenceId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: SequenceId): JsValue = {
      JsString(obj.value)
    }
  }
}

sealed abstract class SequenceStatus(val value: String) extends StringEnumEntry

object SequenceStatus extends StringEnum[SequenceStatus] with StringCirceEnum[SequenceStatus] {

  case object Unpublished extends SequenceStatus("Unpublished")
  case object Published extends SequenceStatus("Published")

  override def values: IndexedSeq[SequenceStatus] = findValues

}
