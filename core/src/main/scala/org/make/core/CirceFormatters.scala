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

}

// object CirceFormatters extends CirceFormatters
