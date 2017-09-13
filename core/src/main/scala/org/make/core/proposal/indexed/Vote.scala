package org.make.core.proposal.indexed

import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder, Json}

sealed trait VoteKey { val shortName: String }

object VoteKey extends StrictLogging {
  private val voteKeys: Map[String, VoteKey] =
    Map(Agree.shortName -> Agree, Disagree.shortName -> Disagree, Neutral.shortName -> Neutral)

  implicit val voteKeyEncoder: Encoder[VoteKey] =
    (voteKey: VoteKey) => Json.fromString(voteKey.shortName)
  implicit val voteKeyDecoder: Decoder[VoteKey] =
    Decoder.decodeString.map(
      voteKey =>
        VoteKey.matchVoteKey(voteKey).getOrElse(throw new IllegalArgumentException(s"$voteKey is not a VoteKey"))
    )

  def matchVoteKey(voteKey: String): Option[VoteKey] = {
    val maybeVoteKey = voteKeys.get(voteKey)
    if (maybeVoteKey.isEmpty) {
      logger.warn(s"$voteKey is not a voteKey")
    }
    maybeVoteKey
  }

  case object Agree extends VoteKey { override val shortName: String = "agree" }
  case object Disagree extends VoteKey { override val shortName: String = "disagree" }
  case object Neutral extends VoteKey { override val shortName: String = "neutral" }
}

final case class Vote(key: VoteKey, count: Int = 0, qualifications: Seq[Qualification])
