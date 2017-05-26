package org.make.core.proposition

import java.time.ZonedDateTime

import io.circe.{Decoder, Encoder, Json}
import org.make.core.StringValue
import org.make.core.citizen.CitizenId

case class Proposition(propositionId: PropositionId,
                       citizenId: CitizenId,
                       createdAt: ZonedDateTime,
                       updatedAt: ZonedDateTime,
                       content: String)

case class PropositionId(value: String) extends StringValue

object PropositionId {
  CitizenId
  implicit lazy val propositionIdEncoder: Encoder[PropositionId] =
    (a: PropositionId) => Json.fromString(a.value)
  implicit lazy val propositionIdDecoder: Decoder[PropositionId] =
    Decoder.decodeString.map(PropositionId(_))
}
