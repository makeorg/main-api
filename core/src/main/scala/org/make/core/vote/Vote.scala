package org.make.core.vote

import java.time.ZonedDateTime

import io.circe.{Decoder, Encoder, Json}
import org.make.core.user.UserId
import org.make.core.proposition.PropositionId
import org.make.core.vote.VoteStatus.VoteStatus
import org.make.core.{MakeSerializable, StringValue}

//TODO: refactor Enum to sealed trait & case objects
object VoteStatus extends Enumeration {
  type VoteStatus = Value
  val AGREE, DISAGREE, UNSURE = Value
  implicit lazy val voteStatusEncoder: Encoder[VoteStatus] = (a: VoteStatus) => Json.fromString(a.toString)
  implicit lazy val voteStatusDecoder: Decoder[VoteStatus] =
    Decoder.decodeString.map(VoteStatus.withName)
}

case class Vote(voteId: VoteId,
                userId: UserId,
                propositionId: PropositionId,
                createdAt: ZonedDateTime,
                status: VoteStatus)
    extends MakeSerializable

case class VoteId(value: String) extends StringValue

object VoteId {
  implicit lazy val voteIdEncoder: Encoder[VoteId] = (a: VoteId) => Json.fromString(a.value)
  implicit lazy val voteIdDecoder: Decoder[VoteId] =
    Decoder.decodeString.map(VoteId(_))
}
