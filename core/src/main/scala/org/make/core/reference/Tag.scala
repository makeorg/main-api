package org.make.core.reference

import io.circe.{Decoder, Encoder, Json}
import org.make.core.{MakeSerializable, SlugHelper, StringValue}
import spray.json.{JsString, JsValue, JsonFormat}
final case class Tag(tagId: TagId, label: String) extends MakeSerializable

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

object Tag {
  def apply(label: String): Tag =
    Tag(tagId = TagId(SlugHelper(label)), label = label)
}
