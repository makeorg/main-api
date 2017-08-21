package org.make.core.theme

import io.circe.{Decoder, Encoder, Json}
import org.make.core.{MakeSerializable, StringValue}

final case class Theme(themeId: ThemeId, label: String) extends MakeSerializable

final case class ThemeId(value: String) extends StringValue

object ThemeId {
  implicit lazy val themeIdEncoder: Encoder[ThemeId] =
    (a: ThemeId) => Json.fromString(a.value)
  implicit lazy val themeIdDecoder: Decoder[ThemeId] =
    Decoder.decodeString.map(ThemeId(_))
}
