package org.make.core.session

import io.circe.{Decoder, Encoder, Json}
import org.make.core.StringValue
import spray.json.{JsString, JsValue, JsonFormat}

final case class VisitorId(value: String) extends StringValue

object VisitorId {

  implicit lazy val visitorIdEncoder: Encoder[VisitorId] =
    (a: VisitorId) => Json.fromString(a.value)
  implicit lazy val visitorIdDecoder: Decoder[VisitorId] =
    Decoder.decodeString.map(VisitorId(_))

  implicit val visitorIdFormatter: JsonFormat[VisitorId] = new JsonFormat[VisitorId] {
    override def read(json: JsValue): VisitorId = json match {
      case JsString(s) => VisitorId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: VisitorId): JsValue = {
      JsString(obj.value)
    }
  }

}
