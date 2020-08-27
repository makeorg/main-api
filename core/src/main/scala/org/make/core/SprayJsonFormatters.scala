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

import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID

import com.sksamuel.elastic4s.searches.sort.SortOrder
import enumeratum.values.{StringEnum, StringEnumEntry}
import spray.json._

@SuppressWarnings(Array("org.wartremover.warts.Throw"))
trait SprayJsonFormatters {

  implicit val localDateFormatter: JsonFormat[LocalDate] = new JsonFormat[LocalDate] {
    override def read(json: JsValue): LocalDate = json match {
      case JsString(s) => LocalDate.parse(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: LocalDate): JsValue = {
      JsString(obj.toString)
    }
  }

  implicit val zonedDateTimeFormatter: JsonFormat[ZonedDateTime] = new JsonFormat[ZonedDateTime] {
    override def read(json: JsValue): ZonedDateTime = json match {
      case JsString(s) => ZonedDateTime.parse(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: ZonedDateTime): JsValue = {
      JsString(obj.toString)
    }
  }

  implicit val uuidFormatter: JsonFormat[UUID] = new JsonFormat[UUID] {
    override def read(json: JsValue): UUID = json match {
      case JsString(s) => UUID.fromString(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: UUID): JsValue = {
      JsString(obj.toString)
    }
  }

  implicit val sortOrderFormatted: JsonFormat[SortOrder] = new JsonFormat[SortOrder] {
    override def read(json: JsValue): SortOrder = json match {
      case JsString(asc) if asc.toLowerCase == "asc"    => SortOrder.Asc
      case JsString(desc) if desc.toLowerCase == "desc" => SortOrder.Desc
      case other                                        => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: SortOrder): JsValue = {
      obj match {
        case SortOrder.Asc  => JsString("asc")
        case SortOrder.Desc => JsString("desc")
      }
    }
  }

  implicit def stringEnumFormatter[A <: StringEnumEntry](implicit enum: StringEnum[A]): JsonFormat[A] =
    new JsonFormat[A] {
      override def read(json: JsValue): A = json match {
        case JsString(s) => enum.withValueOpt(s).getOrElse(throw new IllegalArgumentException(s"Unable to convert $s"))
        case other       => throw new IllegalArgumentException(s"Unable to convert $other")
      }

      override def write(a: A): JsValue = JsString(a.value)
    }

}

object SprayJsonFormatters extends SprayJsonFormatters {

  def forStringValue[T <: StringValue](f: String => T): JsonFormat[T] = new JsonFormat[T] {
    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    override def read(json: JsValue): T = json match {
      case JsString(s) => f(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: T): JsValue = {
      JsString(obj.value)
    }
  }

  object syntax {

    implicit class JsFieldSyntax(val key: String) extends AnyVal {
      def :=[A: JsonWriter](a: A): JsField = key -> a.toJson
    }

    implicit class JsonReaderSyntax(val json: JsValue) extends AnyVal {
      def as[A](implicit reader: JsonReader[A]): A = reader.read(json)
    }

  }

}
