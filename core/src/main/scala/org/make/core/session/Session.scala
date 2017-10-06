package org.make.core.session

import org.make.core.StringValue
import spray.json.{JsString, JsValue, JsonFormat}

final case class SessionId(value: String) extends StringValue

object SessionId {

  implicit val sessionIdFormatter: JsonFormat[SessionId] = new JsonFormat[SessionId] {
    override def read(json: JsValue): SessionId = json match {
      case JsString(s) => SessionId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: SessionId): JsValue = {
      JsString(obj.value)
    }
  }

}
