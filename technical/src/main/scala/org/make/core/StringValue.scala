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

package org.make.core

import cats.instances.string._
import com.github.plokhotnyuk.jsoniter_scala.core._

trait StringValue {
  def value: String
}

object StringValue {

  implicit def catsOrder[T <: StringValue]: cats.Order[T] = cats.Order.by(_.value)
  implicit def ordering[T <: StringValue]: Ordering[T] = Ordering.by(_.value)

  @SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.Throw"))
  def makeCodec[T <: StringValue](create: String => T): JsonValueCodec[T] =
    new JsonValueCodec[T] {

      def decodeValue(in: JsonReader, default: T): T =
        create(in.readString(null))

      def encodeValue(stringValue: T, out: JsonWriter): Unit =
        out.writeVal(stringValue.value)

      @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
      def nullValue: T = null.asInstanceOf[T]
    }
}
