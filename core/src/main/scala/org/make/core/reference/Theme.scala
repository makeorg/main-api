package org.make.core.reference

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import org.make.core.{MakeSerializable, StringValue}
import spray.json.{JsString, JsValue, JsonFormat}

final case class GradientColor(from: String, to: String) extends MakeSerializable

object GradientColor {
  implicit val encoder: ObjectEncoder[GradientColor] = deriveEncoder[GradientColor]
  implicit val decoder: Decoder[GradientColor] = deriveDecoder[GradientColor]
}

final case class ThemeTranslation(slug: String, title: String, language: String) extends MakeSerializable

object ThemeTranslation {
  implicit val encoder: ObjectEncoder[ThemeTranslation] = deriveEncoder[ThemeTranslation]
  implicit val decoder: Decoder[ThemeTranslation] = deriveDecoder[ThemeTranslation]
}

final case class Theme(themeId: ThemeId,
                       translations: Seq[ThemeTranslation],
                       actionsCount: Int,
                       proposalsCount: Int,
                       votesCount: Int,
                       country: String,
                       color: String,
                       gradient: Option[GradientColor] = None,
                       tags: Seq[Tag] = Seq.empty)
    extends MakeSerializable

object Theme {
  implicit val encoder: ObjectEncoder[Theme] = deriveEncoder[Theme]
  implicit val decoder: Decoder[Theme] = deriveDecoder[Theme]
}

final case class ThemeId(value: String) extends StringValue

object ThemeId {
  implicit lazy val themeIdEncoder: Encoder[ThemeId] =
    (a: ThemeId) => Json.fromString(a.value)
  implicit lazy val themeIdDecoder: Decoder[ThemeId] =
    Decoder.decodeString.map(ThemeId(_))

  implicit val themeIdFormatter: JsonFormat[ThemeId] = new JsonFormat[ThemeId] {
    override def read(json: JsValue): ThemeId = json match {
      case JsString(s) => ThemeId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: ThemeId): JsValue = {
      JsString(obj.value)
    }
  }

}
