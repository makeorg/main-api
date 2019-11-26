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

import io.circe.{Decoder, Encoder, Json}
import org.make.core.StringValue
import org.make.core.question.QuestionId
import org.make.core.user.UserId
import spray.json.{JsString, JsValue, JsonFormat}

case class Personality(personalityId: PersonalityId,
                       userId: UserId,
                       questionId: QuestionId,
                       personalityRole: PersonalityRole)

case class PersonalityId(value: String) extends StringValue

object PersonalityId {
  implicit lazy val personalityIdEncoder: Encoder[PersonalityId] =
    (a: PersonalityId) => Json.fromString(a.value)
  implicit lazy val personalityIdDecoder: Decoder[PersonalityId] =
    Decoder.decodeString.map(PersonalityId(_))

  implicit val personalityIdFormatter: JsonFormat[PersonalityId] = new JsonFormat[PersonalityId] {
    override def read(json: JsValue): PersonalityId = json match {
      case JsString(s) => PersonalityId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: PersonalityId): JsValue = {
      JsString(obj.value)
    }
  }
}

sealed trait PersonalityRole { def shortName: String }

object PersonalityRole {
  val roleMap: Map[String, PersonalityRole] =
    Map(Candidate.shortName -> Candidate)

  implicit lazy val personalityRoleEncoder: Encoder[PersonalityRole] = (role: PersonalityRole) =>
    Json.fromString(role.shortName)

  implicit lazy val personalityRoleDecoder: Decoder[PersonalityRole] =
    Decoder.decodeString.emap { value: String =>
      roleMap.get(value) match {
        case Some(kind) => Right(kind)
        case None       => Left(s"$value is not a operation kind")
      }
    }

  implicit val personalityRoleFormatted: JsonFormat[PersonalityRole] = new JsonFormat[PersonalityRole] {
    override def read(json: JsValue): PersonalityRole = json match {
      case JsString(s) => PersonalityRole.roleMap(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: PersonalityRole): JsValue = {
      JsString(obj.shortName)
    }
  }
}

case object Candidate extends PersonalityRole { override val shortName: String = "CANDIDATE" }
