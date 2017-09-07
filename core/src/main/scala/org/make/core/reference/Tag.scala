package org.make.core.reference

import io.circe.{Decoder, Encoder, Json}
import org.make.core.{MakeSerializable, SlugHelper, StringValue}
final case class Tag(tagId: TagId, label: String) extends MakeSerializable

final case class TagId(value: String) extends StringValue

object TagId {
  implicit lazy val tagIdEncoder: Encoder[TagId] =
    (a: TagId) => Json.fromString(a.value)
  implicit lazy val tagIdDecoder: Decoder[TagId] =
    Decoder.decodeString.map(TagId(_))
}

object Tag {
  def apply(label: String): Tag =
    Tag(tagId = TagId(SlugHelper(label)), label = label)
}
