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

import java.net.URL
import java.time.{LocalDate, ZonedDateTime}
import io.circe._

import scala.util.{Failure, Success, Try}

trait CirceFormatters {

  implicit lazy val zonedDateTimeEncoder: Encoder[ZonedDateTime] =
    (a: ZonedDateTime) => Json.fromString(DateFormatters.default.format(a))
  implicit lazy val zonedDateTimeDecoder: Decoder[ZonedDateTime] =
    Decoder.decodeString.emap { date =>
      Try(ZonedDateTime.from(DateFormatters.default.parse(date))) match {
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

  implicit val urlEncoder: Encoder[URL] = Encoder[String].contramap(_.toString)
  implicit val urlDecoder: Decoder[URL] = Decoder[String].emap { url =>
    Try(new URL(url)) match {
      case Success(parsed) => Right(parsed)
      case Failure(_)      => Left(s"Not a valid URL: $url")
    }
  }
}
