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

package org.make.api.technical.tracking

import io.circe.{Decoder, Encoder, Json}
import org.make.core.StringValue

final case class SectionId(value: String) extends StringValue

object SectionId {

  implicit lazy val sectionIdEncoder: Encoder[SectionId] =
    (a: SectionId) => Json.fromString(a.value)
  implicit lazy val sectionIdDecoder: Decoder[SectionId] =
    Decoder.decodeString.map(SectionId(_))

}
