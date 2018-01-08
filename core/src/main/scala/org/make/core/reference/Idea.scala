package org.make.core.reference

import java.util.UUID

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import org.make.core.{MakeSerializable, StringValue}
import spray.json.{JsString, JsValue, JsonFormat}

final case class Idea(ideaId: IdeaId,
                      name: String,
                      language: Option[String] = None,
                      country: Option[String] = None,
                      operation: Option[String] = None,
                      question: Option[String] = None)
    extends MakeSerializable

object Idea {
  implicit val encoder: ObjectEncoder[Idea] = deriveEncoder[Idea]
  implicit val decoder: Decoder[Idea] = deriveDecoder[Idea]

  def apply(name: String,
            language: Option[String],
            country: Option[String],
            operation: Option[String],
            question: Option[String]): Idea =
    Idea(
      ideaId = IdeaId(UUID.randomUUID().toString),
      name = name,
      language = language,
      country = country,
      operation = operation,
      question = question
    )
}

final case class IdeaId(value: String) extends StringValue

object IdeaId {
  implicit lazy val ideaIdEncoder: Encoder[IdeaId] =
    (a: IdeaId) => Json.fromString(a.value)
  implicit lazy val ideaIdDecoder: Decoder[IdeaId] =
    Decoder.decodeString.map(IdeaId(_))

  implicit val ideaIdFormatter: JsonFormat[IdeaId] = new JsonFormat[IdeaId] {
    override def read(json: JsValue): IdeaId = json match {
      case JsString(value) => IdeaId(value)
      case other           => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: IdeaId): JsValue = {
      JsString(obj.value)
    }
  }
}