package org.make.core.tag

import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import org.make.core.StringValue
import spray.json.{JsString, JsValue, JsonFormat}

final case class TagTypeId(value: String) extends StringValue

object TagTypeId {
  implicit lazy val tagIdEncoder: Encoder[TagTypeId] = (a: TagTypeId) => Json.fromString(a.value)
  implicit lazy val tagIdDecoder: Decoder[TagTypeId] = Decoder.decodeString.map(TagTypeId(_))

  implicit val tagIdFormatter: JsonFormat[TagTypeId] = new JsonFormat[TagTypeId] {
    override def read(json: JsValue): TagTypeId = json match {
      case JsString(s) => TagTypeId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: TagTypeId): JsValue = {
      JsString(obj.value)
    }
  }

}

sealed trait TagTypeDisplay { val shortName: String }

object TagTypeDisplay extends StrictLogging {
  val tagTypeDisplayDefault: TagTypeDisplay = Hidden
  val tagTypeDisplays: Map[String, TagTypeDisplay] =
    Map(Displayed.shortName -> Displayed, Hidden.shortName -> Hidden)

  implicit lazy val tagTypeDisplayEncoder: Encoder[TagTypeDisplay] =
    (tagTypeDisplay: TagTypeDisplay) => Json.fromString(tagTypeDisplay.shortName)
  implicit lazy val tagTypeDisplayDecoder: Decoder[TagTypeDisplay] =
    Decoder.decodeString.emap(tagTypeDisplay => Right(TagTypeDisplay.matchTagTypeDisplayOrDefault(tagTypeDisplay)))

  implicit val tagTypeDisplayFormatter: JsonFormat[TagTypeDisplay] = new JsonFormat[TagTypeDisplay] {
    override def read(json: JsValue): TagTypeDisplay = json match {
      case JsString(s) =>
        TagTypeDisplay.tagTypeDisplays.getOrElse(s, throw new IllegalArgumentException(s"Unable to convert $s"))
      case other => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: TagTypeDisplay): JsValue = {
      JsString(obj.shortName)
    }
  }

  def matchTagTypeDisplayOrDefault(tagTypeDisplay: String): TagTypeDisplay = {
    tagTypeDisplays.getOrElse(tagTypeDisplay, {
      logger.warn(s"$tagTypeDisplay is not a tagTypeDisplay")
      tagTypeDisplayDefault
    })
  }

  case object Displayed extends TagTypeDisplay { override val shortName: String = "DISPLAYED" }
  case object Hidden extends TagTypeDisplay { override val shortName: String = "HIDDEN" }
}

final case class TagType(tagTypeId: TagTypeId, label: String, display: TagTypeDisplay)

object TagType {
  implicit val encoder: ObjectEncoder[TagType] = deriveEncoder[TagType]
  implicit val decoder: Decoder[TagType] = deriveDecoder[TagType]

  val LEGACY: TagType = TagType(
    tagTypeId = TagTypeId("8405aba4-4192-41d2-9a0d-b5aa6cb98d37"),
    label = "Legacy",
    display = TagTypeDisplay.Displayed
  )
}