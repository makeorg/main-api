/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.core.demographics

import enumeratum.{CirceEnum, Enum, EnumEntry}
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser._
import io.circe.{Codec, Decoder, Encoder, Json}
import org.make.core.StringValue
import org.make.core.demographics.DemographicsCard.Layout
import org.make.core.reference.Language

import java.time.ZonedDateTime

final case class DemographicsCardId(value: String) extends StringValue

object DemographicsCardId {
  implicit val codec: Codec[DemographicsCardId] =
    Codec.from(Decoder[String].map(DemographicsCardId.apply), Encoder[String].contramap(_.value))
}

final case class DemographicsCard(
  id: DemographicsCardId,
  name: String,
  layout: Layout,
  dataType: String,
  language: Language,
  title: String,
  parameters: String,
  createdAt: ZonedDateTime,
  updatedAt: ZonedDateTime
)

object DemographicsCard {

  sealed abstract class Layout extends EnumEntry

  object Layout extends Enum[Layout] with CirceEnum[Layout] {
    case object Select extends Layout
    case object OneColumnRadio extends Layout
    case object ThreeColumnsRadio extends Layout

    override def values: IndexedSeq[Layout] = findValues
  }

  def parseParameters(parameters: String): Json = parse(parameters) match {
    case Left(_)     => Json.Null
    case Right(json) => json
  }

  val SKIPPED = "SKIPPED"
}

final case class LabelValue(label: String, value: String)

object LabelValue {
  implicit val codec: Codec[LabelValue] = deriveCodec
}
