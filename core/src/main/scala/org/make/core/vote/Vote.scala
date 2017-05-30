package org.make.core.vote

import java.time.ZonedDateTime

import io.circe.{Decoder, Encoder, Json}
import org.make.core.StringValue
import org.make.core.citizen.CitizenId
import org.make.core.proposition.PropositionId
import org.make.core.vote.VoteStatus.VoteStatus

object VoteStatus extends Enumeration {
  type VoteStatus = Value
  val AGREE, DISAGREE, UNSURE = Value
  implicit lazy val voteStatusEncoder: Encoder[VoteStatus] = (a: VoteStatus) => Json.fromString(a.toString)
  implicit lazy val voteStatusDecoder: Decoder[VoteStatus] =
    Decoder.decodeString.map(VoteStatus.withName)
}

case class Vote(voteId: VoteId,
                citizenId: CitizenId,
                propositionId: PropositionId,
                createdAt: ZonedDateTime,
                status: VoteStatus)
    extends VoteSerializable

case class VoteId(value: String) extends StringValue

object VoteId {
  implicit lazy val voteIdEncoder: Encoder[VoteId] = (a: VoteId) => Json.fromString(a.value)
  implicit lazy val voteIdDecoder: Decoder[VoteId] =
    Decoder.decodeString.map(VoteId(_))
}
