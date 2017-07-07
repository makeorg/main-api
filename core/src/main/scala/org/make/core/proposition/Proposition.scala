package org.make.core.proposition

import java.time.ZonedDateTime

import io.circe.{Decoder, Encoder, Json}
import org.make.core.user.UserId
import org.make.core.{MakeSerializable, StringValue, Timestamped}

case class Proposition(propositionId: PropositionId,
                       userId: UserId,
                       content: String,
                       override val createdAt: Option[ZonedDateTime] = None,
                       override val updatedAt: Option[ZonedDateTime] = None)
    extends MakeSerializable with Timestamped

case class PropositionId(value: String) extends StringValue

object PropositionId {
  UserId
  implicit lazy val propositionIdEncoder: Encoder[PropositionId] =
    (a: PropositionId) => Json.fromString(a.value)
  implicit lazy val propositionIdDecoder: Decoder[PropositionId] =
    Decoder.decodeString.map(PropositionId(_))
}
