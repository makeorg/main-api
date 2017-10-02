package org.make.core.proposal.indexed

import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder, Json}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

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

  implicit val voteKeyFormatter: JsonFormat[VoteKey] = new JsonFormat[VoteKey] {
    override def read(json: JsValue): VoteKey = json match {
      case JsString(s) => VoteKey.voteKeys.getOrElse(s, throw new IllegalArgumentException(s"Unable to convert $s"))
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: VoteKey): JsValue = {
      JsString(obj.shortName)
    }
  }

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

final case class Vote(key: VoteKey, count: Int = 0, qualifications: Seq[Qualification], hasVoted: Boolean = false)

object Vote {
  implicit val voteFormatter: RootJsonFormat[Vote] =
    DefaultJsonProtocol.jsonFormat4(Vote.apply)

}

final case class IndexedVote(key: VoteKey, count: Int = 0, qualifications: Seq[IndexedQualification])

object IndexedVote {
  def apply(vote: Vote): IndexedVote =
    IndexedVote(
      key = vote.key,
      count = vote.count,
      qualifications = vote.qualifications.map(IndexedQualification.apply)
    )
}
