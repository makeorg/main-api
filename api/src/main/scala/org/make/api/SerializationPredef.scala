package org.make.api

import java.time.{LocalDate, ZonedDateTime}

import com.twitter.util.Try
import io.circe.{Decoder, Encoder, Json}
import io.finch.{Decode, DecodeEntity}
import org.make.core.citizen.CitizenId

object SerializationPredef {



  implicit val decodeCitizenId: DecodeEntity[CitizenId] = DecodeEntity.instance{ s => Try(CitizenId(s)) }
  implicit val encodeCitizenId: Encoder[CitizenId] = Encoder.instance[CitizenId] { citizenId => Json.fromString(citizenId.value) }

  implicit val encodeLocalDate: Encoder[LocalDate] = Encoder.instance[LocalDate] { date => Json.fromString(date.toString)}
  implicit val decodeLocalDate: Decoder[LocalDate] = Decoder.decodeString.map(LocalDate.parse)
  implicit val decodeEntityLocalDate: DecodeEntity[LocalDate] = DecodeEntity.instance { s => Try(LocalDate.parse(s)) }

  implicit val encodeZonedDateTime: Encoder[ZonedDateTime] = Encoder.instance[ZonedDateTime] { date => Json.fromString(date.toString)}
  implicit val decodeZonedDateTime: DecodeEntity[ZonedDateTime] = DecodeEntity.instance { s => Try(ZonedDateTime.parse(s)) }

}
