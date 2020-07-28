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

package org.make.core.partner

import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe.{Decoder, Encoder, Json}
import org.make.core.StringValue
import org.make.core.question.QuestionId
import org.make.core.user.UserId
import spray.json.{JsString, JsValue, JsonFormat}

case class Partner(
  partnerId: PartnerId,
  name: String,
  logo: Option[String],
  link: Option[String],
  organisationId: Option[UserId],
  partnerKind: PartnerKind,
  questionId: QuestionId,
  weight: Float
)

case class PartnerId(value: String) extends StringValue

object PartnerId {
  implicit lazy val partnerIdEncoder: Encoder[PartnerId] =
    (a: PartnerId) => Json.fromString(a.value)
  implicit lazy val partnerIdDecoder: Decoder[PartnerId] =
    Decoder.decodeString.map(PartnerId(_))

  implicit val partnerIdFormatter: JsonFormat[PartnerId] = new JsonFormat[PartnerId] {
    override def read(json: JsValue): PartnerId = json match {
      case JsString(s) => PartnerId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: PartnerId): JsValue = {
      JsString(obj.value)
    }
  }
}

sealed abstract class PartnerKind(val value: String) extends StringEnumEntry

object PartnerKind extends StringEnum[PartnerKind] with StringCirceEnum[PartnerKind] {

  case object Media extends PartnerKind("MEDIA")
  case object ActionPartner extends PartnerKind("ACTION_PARTNER")
  case object Founder extends PartnerKind("FOUNDER")
  case object Actor extends PartnerKind("ACTOR")

  override def values: IndexedSeq[PartnerKind] = findValues

}
