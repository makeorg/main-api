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

package org.make.core.feature

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.make.core.StringValue
import org.make.core.question.QuestionId

final case class ActiveFeature(
  activeFeatureId: ActiveFeatureId,
  featureId: FeatureId,
  maybeQuestionId: Option[QuestionId]
)

object ActiveFeature {
  implicit val encoder: Encoder[ActiveFeature] = deriveEncoder[ActiveFeature]
  implicit val decoder: Decoder[ActiveFeature] = deriveDecoder[ActiveFeature]
}

final case class ActiveFeatureId(value: String) extends StringValue

object ActiveFeatureId {
  implicit lazy val activeFeatureIdEncoder: Encoder[ActiveFeatureId] =
    (a: ActiveFeatureId) => Json.fromString(a.value)
  implicit lazy val activeFeatureIdDecoder: Decoder[ActiveFeatureId] =
    Decoder.decodeString.map(ActiveFeatureId(_))

}
