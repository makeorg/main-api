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

import java.time.ZonedDateTime
import cats.data.{NonEmptyList => Nel}
import com.github.plokhotnyuk.jsoniter_scala.macros._
import com.github.plokhotnyuk.jsoniter_scala.core._
import org.make.core.DateFormatters

trait JsoniterCodecs {

  @SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.AsInstanceOf"))
  implicit def nonEmptyListCodec[T: JsonValueCodec]: JsonValueCodec[Nel[T]] =
    new JsonValueCodec[Nel[T]] {

      private val listCodec: JsonValueCodec[List[T]] =
        JsonCodecMaker.makeWithRequiredCollectionFields[List[T]]

      def decodeValue(in: JsonReader, default: Nel[T]): Nel[T] =
        Nel.fromListUnsafe(listCodec.decodeValue(in, null))

      def encodeValue(nel: Nel[T], out: JsonWriter): Unit =
        listCodec.encodeValue(nel.toList, out)

      def nullValue: Nel[T] = null.asInstanceOf[Nel[T]]
    }

  @SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.AsInstanceOf"))
  implicit val zonedDateTimeCodec: JsonValueCodec[ZonedDateTime] =
    new JsonValueCodec[ZonedDateTime] {

      def decodeValue(in: JsonReader, default: ZonedDateTime): ZonedDateTime =
        ZonedDateTime.from(DateFormatters.default.parse(in.readString(null)))

      def encodeValue(dateTime: ZonedDateTime, out: JsonWriter): Unit =
        out.writeVal(DateFormatters.default.format(dateTime))

      def nullValue: ZonedDateTime = null.asInstanceOf[ZonedDateTime]
    }
}
