package org.make.core.reference

import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import io.circe.generic.semiauto._
import org.make.core.{MakeSerializable, SlugHelper, StringValue}
import spray.json.{JsString, JsValue, JsonFormat}

final case class TagId(value: String) extends StringValue

object TagId {
  implicit lazy val tagIdEncoder: Encoder[TagId] =
    (a: TagId) => Json.fromString(a.value)
  implicit lazy val tagIdDecoder: Decoder[TagId] =
    Decoder.decodeString.map(TagId(_))

  implicit val tagIdFormatter: JsonFormat[TagId] = new JsonFormat[TagId] {
    override def read(json: JsValue): TagId = json match {
      case JsString(s) => TagId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: TagId): JsValue = {
      JsString(obj.value)
    }
  }

}

final case class Tag(tagId: TagId, label: String) extends MakeSerializable

object Tag {
  implicit val encoder: ObjectEncoder[Tag] = deriveEncoder[Tag]
  implicit val decoder: Decoder[Tag] = deriveDecoder[Tag]

  def apply(label: String): Tag =
    Tag(tagId = TagId(SlugHelper(label)), label = label)
}
