/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

package org.make.core.technical.enumeratum

import enumeratum.values.{StringEnumEntry, ValueEnum, ValueEnumEntry}
import io.circe.{Decoder, Encoder}

trait FallbackingCirceEnum[A, B <: ValueEnumEntry[A]] { self: ValueEnum[A, B] =>

  def default(value: A): B

  def apply(value: A): B = withValueOpt(value).getOrElse(default(value))

  implicit def decoder(implicit d: Decoder[A]): Decoder[B] = Decoder[A].map(apply)
  implicit def encoder(implicit e: Encoder[A]): Encoder[B] = Encoder[A].contramap(_.value)

}

object FallbackingCirceEnum {

  type FallbackingStringCirceEnum[A <: StringEnumEntry] = FallbackingCirceEnum[String, A]

}
