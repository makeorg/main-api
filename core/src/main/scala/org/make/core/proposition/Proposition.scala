package org.make.core.proposition

import java.time.ZonedDateTime

import io.circe.{Decoder, Encoder, Json}
import org.make.core.user.UserId
import org.make.core.{MakeSerializable, StringValue}

case class Proposition(propositionId: PropositionId,
                       userId: UserId,
                       createdAt: ZonedDateTime,
                       updatedAt: ZonedDateTime,
                       content: String)
    extends MakeSerializable

case class PropositionId(value: String) extends StringValue

object PropositionId {
  UserId
  implicit lazy val propositionIdEncoder: Encoder[PropositionId] =
    (a: PropositionId) => Json.fromString(a.value)
  implicit lazy val propositionIdDecoder: Decoder[PropositionId] =
    Decoder.decodeString.map(PropositionId(_))
}
