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

package org.make.core.personality

import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe.{Decoder, Encoder, Json}
import org.make.core.{SprayJsonFormatters, StringValue}
import org.make.core.question.QuestionId
import org.make.core.user.UserId
import spray.json.JsonFormat

final case class Personality(
  personalityId: PersonalityId,
  userId: UserId,
  questionId: QuestionId,
  personalityRoleId: PersonalityRoleId
)

final case class PersonalityId(value: String) extends StringValue

object PersonalityId {
  implicit lazy val personalityIdEncoder: Encoder[PersonalityId] =
    (a: PersonalityId) => Json.fromString(a.value)
  implicit lazy val personalityIdDecoder: Decoder[PersonalityId] =
    Decoder.decodeString.map(PersonalityId(_))

  implicit val personalityIdFormatter: JsonFormat[PersonalityId] =
    SprayJsonFormatters.forStringValue(PersonalityId.apply)
}

final case class PersonalityRole(personalityRoleId: PersonalityRoleId, name: String)

final case class PersonalityRoleField(
  personalityRoleFieldId: PersonalityRoleFieldId,
  personalityRoleId: PersonalityRoleId,
  name: String,
  fieldType: FieldType,
  required: Boolean
)

final case class PersonalityRoleFieldId(value: String) extends StringValue

object PersonalityRoleFieldId {
  implicit lazy val personalityRoleFieldIdEncoder: Encoder[PersonalityRoleFieldId] =
    (a: PersonalityRoleFieldId) => Json.fromString(a.value)
  implicit lazy val personalityRoleFieldIdDecoder: Decoder[PersonalityRoleFieldId] =
    Decoder.decodeString.map(PersonalityRoleFieldId(_))

  implicit val personalityRoleFieldIdFormatter: JsonFormat[PersonalityRoleFieldId] =
    SprayJsonFormatters.forStringValue(PersonalityRoleFieldId.apply)
}

final case class PersonalityRoleId(value: String) extends StringValue

object PersonalityRoleId {
  implicit lazy val personalityRoleIdEncoder: Encoder[PersonalityRoleId] =
    (a: PersonalityRoleId) => Json.fromString(a.value)
  implicit lazy val personalityRoleIdDecoder: Decoder[PersonalityRoleId] =
    Decoder.decodeString.map(PersonalityRoleId(_))

  implicit val personalityRoleIdFormatter: JsonFormat[PersonalityRoleId] =
    SprayJsonFormatters.forStringValue(PersonalityRoleId.apply)
}

sealed abstract class FieldType(val value: String) extends StringEnumEntry

object FieldType extends StringEnum[FieldType] with StringCirceEnum[FieldType] {

  val defaultFieldType: FieldType = StringType

  case object StringType extends FieldType("STRING")
  case object IntType extends FieldType("INT")
  case object BooleanType extends FieldType("BOOLEAN")

  override def values: IndexedSeq[FieldType] = findValues

}
