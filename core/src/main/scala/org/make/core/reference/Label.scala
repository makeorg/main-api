package org.make.core.reference

import io.circe.{Decoder, Encoder, Json}
import org.make.core.{MakeSerializable, StringValue}

final case class Label(labelId: LabelId, label: String) extends MakeSerializable

final case class LabelId(value: String) extends StringValue

object LabelId {
  implicit lazy val labelIdEncoder: Encoder[LabelId] =
    (a: LabelId) => Json.fromString(a.value)
  implicit lazy val labelIdDecoder: Decoder[LabelId] =
    Decoder.decodeString.map(LabelId(_))
}
