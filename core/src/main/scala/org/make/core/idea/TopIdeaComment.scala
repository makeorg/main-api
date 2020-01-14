package org.make.core.idea

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
import org.make.core.StringValue
import org.make.core.proposal.{QualificationKey, VoteKey}
import org.make.core.user.UserId
import spray.json.{JsString, JsValue, JsonFormat}

final case class TopIdeaCommentId(value: String) extends StringValue

object TopIdeaCommentId {
  implicit lazy val topIdeaCommentIdEncoder: Encoder[TopIdeaCommentId] =
    (a: TopIdeaCommentId) => Json.fromString(a.value)
  implicit lazy val topIdeaCommentIdDecoder: Decoder[TopIdeaCommentId] =
    Decoder.decodeString.map(TopIdeaCommentId(_))

  implicit val topIdeaCommentIdFormatter: JsonFormat[TopIdeaCommentId] = new JsonFormat[TopIdeaCommentId] {
    override def read(json: JsValue): TopIdeaCommentId = json match {
      case JsString(value) => TopIdeaCommentId(value)
      case other           => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: TopIdeaCommentId): JsValue = {
      JsString(obj.value)
    }
  }
}

final case class TopIdeaComment(topIdeaCommentId: TopIdeaCommentId,
                                topIdeaId: TopIdeaId,
                                personalityId: UserId,
                                comment1: Option[String],
                                comment2: Option[String],
                                comment3: Option[String],
                                vote: Option[VoteKey],
                                qualification: Option[QualificationKey])

object TopIdeaComment {
  implicit val encoder: Encoder[TopIdeaComment] = deriveEncoder[TopIdeaComment]
  implicit val decoder: Decoder[TopIdeaComment] = deriveDecoder[TopIdeaComment]
}
