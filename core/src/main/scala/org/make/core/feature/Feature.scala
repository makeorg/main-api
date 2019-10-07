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

final case class Feature(featureId: FeatureId, name: String, slug: String)

object Feature {
  implicit val encoder: Encoder[Feature] = deriveEncoder[Feature]
  implicit val decoder: Decoder[Feature] = deriveDecoder[Feature]
}

final case class FeatureId(value: String) extends StringValue

object FeatureId {
  implicit lazy val tagIdEncoder: Encoder[FeatureId] =
    (a: FeatureId) => Json.fromString(a.value)
  implicit lazy val tagIdDecoder: Decoder[FeatureId] =
    Decoder.decodeString.map(FeatureId(_))

}
