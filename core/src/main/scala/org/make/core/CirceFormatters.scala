package org.make.core

import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID

import io.circe._

trait CirceFormatters {

  implicit lazy val zonedDateTimeEncoder: Encoder[ZonedDateTime] =
    (a: ZonedDateTime) => Json.fromString(a.toString)
  implicit lazy val zonedDateTimeDecoder: Decoder[ZonedDateTime] =
    Decoder.decodeString.map(ZonedDateTime.parse)

  implicit lazy val localDateEncoder: Encoder[LocalDate] = (a: LocalDate) => Json.fromString(a.toString)
  implicit lazy val localDateDecoder: Decoder[LocalDate] =
    Decoder.decodeString.map(LocalDate.parse)

  implicit lazy val uuidEncoder: Encoder[UUID] = (a: UUID) => Json.fromString(a.toString)
  implicit lazy val uuidDecoder: Decoder[UUID] =
    Decoder.decodeString.map(UUID.fromString)

}

object CirceFormatters extends CirceFormatters
