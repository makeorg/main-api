package org.make.core.idea

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
import org.make.core.StringValue
import org.make.core.question.QuestionId
import spray.json.{JsString, JsValue, JsonFormat}

final case class TopIdeaId(value: String) extends StringValue

object TopIdeaId {
  implicit lazy val topIdeaIdEncoder: Encoder[TopIdeaId] =
    (a: TopIdeaId) => Json.fromString(a.value)
  implicit lazy val topIdeaIdDecoder: Decoder[TopIdeaId] =
    Decoder.decodeString.map(TopIdeaId(_))

  implicit val topIdeaIdFormatter: JsonFormat[TopIdeaId] = new JsonFormat[TopIdeaId] {
    override def read(json: JsValue): TopIdeaId = json match {
      case JsString(value) => TopIdeaId(value)
      case other           => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: TopIdeaId): JsValue = {
      JsString(obj.value)
    }
  }
}

final case class TopIdea(topIdeaId: TopIdeaId,
                         ideaId: IdeaId,
                         questionId: QuestionId,
                         name: String,
                         label: String,
                         scores: TopIdeaScores,
                         weight: Float)

object TopIdea {
  implicit val encoder: Encoder[TopIdea] = deriveEncoder[TopIdea]
  implicit val decoder: Decoder[TopIdea] = deriveDecoder[TopIdea]
}

final case class TopIdeaScores(totalProposalsRatio: Float, agreementRatio: Float, likeItRatio: Float)

object TopIdeaScores {
  implicit val encoder: Encoder[TopIdeaScores] = deriveEncoder[TopIdeaScores]
  implicit val decoder: Decoder[TopIdeaScores] = deriveDecoder[TopIdeaScores]
}
