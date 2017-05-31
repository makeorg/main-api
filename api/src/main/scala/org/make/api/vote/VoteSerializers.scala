package org.make.api.vote

import org.make.api.technical.SprayJsonFormatters
import org.make.core.vote.VoteEvent.{VoteViewed, VotedAgree, VotedDisagree, VotedUnsure}
import org.make.core.vote.VoteStatus.VoteStatus
import org.make.core.vote.{Vote, VoteStatus}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}
import stamina.V1
import stamina.json._

object VoteSerializers extends SprayJsonFormatters {

  implicit val voteStatusFormatter: JsonFormat[VoteStatus] = new JsonFormat[VoteStatus] {
    override def read(json: JsValue): VoteStatus = json match {
      case JsString(s) => VoteStatus.withName(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: VoteStatus): JsValue = JsString(obj.toString)
  }

  implicit private val votedAgreeFormatter: RootJsonFormat[VotedAgree] =
    DefaultJsonProtocol.jsonFormat5(VotedAgree)

  implicit private val votedDisagreeFormatter: RootJsonFormat[VotedDisagree] =
    DefaultJsonProtocol.jsonFormat5(VotedDisagree)

  implicit private val votedUnsureFormatter: RootJsonFormat[VotedUnsure] =
    DefaultJsonProtocol.jsonFormat5(VotedUnsure)

  implicit private val viewedVoteFormatter: RootJsonFormat[VoteViewed] =
    DefaultJsonProtocol.jsonFormat2(VoteViewed)

  implicit private val voteFormatter: RootJsonFormat[Vote] =
    DefaultJsonProtocol.jsonFormat5(Vote)

  private val votedAgreeSerializer: JsonPersister[VotedAgree, V1] =
    persister[VotedAgree]("vote-agree")

  private val votedDisagreeSerializer: JsonPersister[VotedDisagree, V1] =
    persister[VotedDisagree]("vote-disagree")

  private val votedUnsureSerializer: JsonPersister[VotedUnsure, V1] =
    persister[VotedUnsure]("vote-unsure")

  private val votedViewedSerializer: JsonPersister[VoteViewed, V1] =
    persister[VoteViewed]("vote-viewed")

  private val voteSerializer: JsonPersister[Vote, V1] =
    persister[Vote]("vote")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(votedAgreeSerializer, votedDisagreeSerializer, votedUnsureSerializer, votedViewedSerializer, voteSerializer)

}
