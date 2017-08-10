package org.make.core.proposal

import java.time.ZonedDateTime

import io.circe.{Decoder, Encoder, Json}
import org.make.core.user.UserId
import org.make.core.{MakeSerializable, StringValue, Timestamped}

case class Proposal(proposalId: ProposalId,
                    userId: UserId,
                    content: String,
                    override val createdAt: Option[ZonedDateTime] = None,
                    override val updatedAt: Option[ZonedDateTime] = None)
    extends MakeSerializable
    with Timestamped

case class ProposalId(value: String) extends StringValue

object ProposalId {
  implicit lazy val proposalIdEncoder: Encoder[ProposalId] =
    (a: ProposalId) => Json.fromString(a.value)
  implicit lazy val proposalIdDecoder: Decoder[ProposalId] =
    Decoder.decodeString.map(ProposalId(_))
}
