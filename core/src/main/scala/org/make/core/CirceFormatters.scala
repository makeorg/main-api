package org.make.core

import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID

import io.circe._

import scala.util.{Failure, Success, Try}

trait CirceFormatters {

  implicit lazy val zonedDateTimeEncoder: Encoder[ZonedDateTime] =
    (a: ZonedDateTime) => Json.fromString(a.toString)
  implicit lazy val zonedDateTimeDecoder: Decoder[ZonedDateTime] =
    Decoder.decodeString.emap { date =>
      Try(ZonedDateTime.parse(date)) match {
        case Success(parsed) => Right(parsed)
        case Failure(_)      => Left(s"$date is not a valid date, it should match yyyy-MM-ddThh:mm:ssZ")
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

  implicit lazy val uuidEncoder: Encoder[UUID] = (a: UUID) => Json.fromString(a.toString)
  implicit lazy val uuidDecoder: Decoder[UUID] =
    Decoder.decodeString.emap { uuid =>
      Try(UUID.fromString(uuid)) match {
        case Success(parsed) => Right(parsed)
        case Failure(_)      => Left(s"$uuid is not a valid uuid")
      }
    }

}

object CirceFormatters extends CirceFormatters
