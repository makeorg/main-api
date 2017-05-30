package org.make.api.vote

import org.make.api.technical.SprayJsonFormatters
import org.make.core.vote.VoteEvent.{VoteViewed, VotedAgree, VotedDisagree, VotedUnsure}
import org.make.core.vote.{Vote, VoteStatus}
import org.make.core.vote.VoteStatus.VoteStatus
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}
import stamina.json._
import stamina.{StaminaAkkaSerializer, V1}

class VoteSerializers
    extends StaminaAkkaSerializer(
      VoteSerializers.votedAgreeSerializer,
      VoteSerializers.votedDisagreeSerializer,
      VoteSerializers.votedUnsureSerializer,
      VoteSerializers.votedViewedSerializer,
      VoteSerializers.voteSerializer
    )

object VoteSerializers extends SprayJsonFormatters {

  implicit val voteStatusFormatter: JsonFormat[VoteStatus] = new JsonFormat[VoteStatus] {
    override def read(json: JsValue): VoteStatus = json match {
      case JsString(s) => VoteStatus.withName(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: VoteStatus): JsValue = JsString(obj.toString)
  }

  implicit val votedAgreeFormatter: RootJsonFormat[VotedAgree] =
    DefaultJsonProtocol.jsonFormat5(VotedAgree)

  implicit val votedDisagreeFormatter: RootJsonFormat[VotedDisagree] =
    DefaultJsonProtocol.jsonFormat5(VotedDisagree)

  implicit val votedUnsureFormatter: RootJsonFormat[VotedUnsure] =
    DefaultJsonProtocol.jsonFormat5(VotedUnsure)

  implicit val viewedVoteFormatter: RootJsonFormat[VoteViewed] =
    DefaultJsonProtocol.jsonFormat2(VoteViewed)

  implicit val voteFormatter: RootJsonFormat[Vote] =
    DefaultJsonProtocol.jsonFormat5(Vote)

  val votedAgreeSerializer: JsonPersister[VotedAgree, V1] =
    persister[VotedAgree]("vote-agree")

  val votedDisagreeSerializer: JsonPersister[VotedDisagree, V1] =
    persister[VotedDisagree]("vote-disagree")

  val votedUnsureSerializer: JsonPersister[VotedUnsure, V1] =
    persister[VotedUnsure]("vote-unsure")

  val votedViewedSerializer: JsonPersister[VoteViewed, V1] =
    persister[VoteViewed]("vote-viewed")

  val voteSerializer: JsonPersister[Vote, V1] =
    persister[Vote]("vote")

}
