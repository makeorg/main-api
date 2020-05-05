/*
 *  Make.org Core API
 *  Copyright (C) 2019 Make.org
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

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
import org.make.core.StringValue
import org.make.core.question.QuestionId
import spray.json.{JsString, JsValue, JsonFormat}

final case class TopIdeaId(value: String) extends StringValue

object TopIdeaId {
  implicit lazy val topIdeaIdEncoder: Encoder[TopIdeaId] =
    (a: TopIdeaId) => Json.fromString(a.value)
  implicit lazy val topIdeaIdDecoder: Decoder[TopIdeaId] =
    Decoder.decodeString.map(TopIdeaId(_))

  implicit val topIdeaIdFormatter: JsonFormat[TopIdeaId] = new JsonFormat[TopIdeaId] {
    override def read(json: JsValue): TopIdeaId = json match {
      case JsString(value) => TopIdeaId(value)
      case other           => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: TopIdeaId): JsValue = {
      JsString(obj.value)
    }
  }
}

final case class TopIdea(topIdeaId: TopIdeaId,
                         ideaId: IdeaId,
                         questionId: QuestionId,
                         name: String,
                         label: String,
                         scores: TopIdeaScores,
                         weight: Float)

object TopIdea {
  implicit val encoder: Encoder[TopIdea] = deriveEncoder[TopIdea]
  implicit val decoder: Decoder[TopIdea] = deriveDecoder[TopIdea]
}

final case class TopIdeaScores(totalProposalsRatio: Float, agreementRatio: Float, likeItRatio: Float)

object TopIdeaScores {
  implicit val encoder: Encoder[TopIdeaScores] = deriveEncoder[TopIdeaScores]
  implicit val decoder: Decoder[TopIdeaScores] = deriveDecoder[TopIdeaScores]
}
