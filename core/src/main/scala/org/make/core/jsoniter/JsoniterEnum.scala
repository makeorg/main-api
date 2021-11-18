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

package org.make.core.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core._
import enumeratum.values.{StringEnum, StringEnumEntry}

import scala.collection.mutable.HashMap

@SuppressWarnings(Array("org.wartremover.warts.LeakingSealed"))
trait JsoniterEnum[T <: StringEnumEntry] extends StringEnum[T] {

  @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
  private val kvs: HashMap[String, T] = this.values.map(v => (v.value, v)).to(HashMap)

  @SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.AsInstanceOf"))
  implicit val codec: JsonValueCodec[T] =
    new JsonValueCodec[T] {

      def decodeValue(in: JsonReader, default: T): T =
        kvs(in.readString(null))

      def encodeValue(wrapper: T, out: JsonWriter): Unit =
        out.writeVal(wrapper.value)

      def nullValue: T = null.asInstanceOf[T]
    }
}
