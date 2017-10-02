package org.make.core.reference

import io.circe.{Decoder, Encoder, Json}
import org.make.core.{MakeSerializable, StringValue}
import spray.json.{JsString, JsValue, JsonFormat}

final case class Label(labelId: LabelId, label: String) extends MakeSerializable

final case class LabelId(value: String) extends StringValue

object LabelId {
  implicit lazy val labelIdEncoder: Encoder[LabelId] =
    (a: LabelId) => Json.fromString(a.value)
  implicit lazy val labelIdDecoder: Decoder[LabelId] =
    Decoder.decodeString.map(LabelId(_))

  implicit val labelIdFormatter: JsonFormat[LabelId] = new JsonFormat[LabelId] {
    override def read(json: JsValue): LabelId = json match {
      case JsString(s) => LabelId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: LabelId): JsValue = {
      JsString(obj.value)
    }
  }

}
