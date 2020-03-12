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

import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField._
import java.time.{LocalDate, ZonedDateTime}

import io.circe._

import scala.util.{Failure, Success, Try}

trait CirceFormatters {

  protected val dateFormatter: DateTimeFormatter = new DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE)
    .appendLiteral("T")
    .appendValue(HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(MINUTE_OF_HOUR, 2)
    .optionalStart
    .appendLiteral(':')
    .appendValue(SECOND_OF_MINUTE, 2)
    .optionalStart
    .appendFraction(NANO_OF_SECOND, 3, 3, true)
    .appendOffsetId()
    .toFormatter()

  implicit lazy val zonedDateTimeEncoder: Encoder[ZonedDateTime] =
    (a: ZonedDateTime) => Json.fromString(dateFormatter.format(a))
  implicit lazy val zonedDateTimeDecoder: Decoder[ZonedDateTime] =
    Decoder.decodeString.emap { date =>
      Try(ZonedDateTime.from(dateFormatter.parse(date))) match {
        case Success(parsed) => Right(parsed)
        case Failure(_)      => Left(s"$date is not a valid date, it should match yyyy-MM-ddThh:mm:ss.SSSZ")
      }
    }

  implicit lazy val localDateEncoder: Encoder[LocalDate] = (a: LocalDate) => Json.fromString(a.toString)
  implicit lazy val localDateDecoder: Decoder[LocalDate] =
    Decoder.decodeString.emap { date =>
      Try(LocalDate.parse(date)) match {
        case Success(parsed) => Right(parsed)
        case Failure(_)      => Left(s"$date is not a valid date, it should match yyyy-MM-dd")
      }
    }

  implicit def h[A, B](implicit a: Decoder[A], b: Decoder[B]): Decoder[Either[A, B]] = {
    val l: Decoder[Either[A, B]] = a.map(Left.apply)
    val r: Decoder[Either[A, B]] = b.map(Right.apply)
    l.or(r)
  }

  implicit def stringValueEncoder[A <: StringValue]: Encoder[A] = a => Json.fromString(a.value)
}

// object CirceFormatters extends CirceFormatters
