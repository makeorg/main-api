package org.make.core.proposal.indexed

import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder, Json}
import org.make.core.user.UserId

sealed trait VoteKey { val shortName: String }

object VoteKey extends StrictLogging {
  val voteKeys: Map[String, VoteKey] =
    Map(Agree.shortName -> Agree, Disagree.shortName -> Disagree, Neutral.shortName -> Neutral)

  implicit lazy val voteKeyEncoder: Encoder[VoteKey] =
    (voteKey: VoteKey) => Json.fromString(voteKey.shortName)
  implicit lazy val voteKeyDecoder: Decoder[VoteKey] =
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

final case class Vote(key: VoteKey,
                      count: Int = 0,
                      qualifications: Seq[Qualification],
                      userIds: Seq[UserId] = Seq(),
                      sessionIds: Seq[String] = Seq())

final case class IndexedVote(key: VoteKey, count: Int = 0, qualifications: Seq[Qualification])

object IndexedVote {
  def apply(vote: Vote): IndexedVote =
    IndexedVote(key = vote.key, count = vote.count, qualifications = vote.qualifications)
}

final case class VoteResponse(key: VoteKey, count: Int = 0, qualifications: Seq[Qualification], hasVoted: Boolean)

object VoteResponse {
  def parseVote(vote: Vote, maybeUserId: Option[UserId], sessionId: String): VoteResponse =
    VoteResponse(
      key = vote.key,
      count = vote.count,
      qualifications = vote.qualifications,
      hasVoted = vote.userIds.contains(maybeUserId.getOrElse(UserId(""))) || vote.sessionIds.contains(sessionId)
    )
}
