package org.make.core.tag

import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import org.make.core.operation.OperationId
import org.make.core.reference.ThemeId
import org.make.core.{MakeSerializable, StringValue}
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

sealed trait TagDisplay { val shortName: String }

object TagDisplay extends StrictLogging {
  val tagDisplayDefault: TagDisplay = Inherit
  val tagDisplays: Map[String, TagDisplay] =
    Map(Displayed.shortName -> Displayed, Hidden.shortName -> Hidden, Inherit.shortName -> Inherit)

  implicit lazy val tagDisplayEncoder: Encoder[TagDisplay] =
    (tagDisplay: TagDisplay) => Json.fromString(tagDisplay.shortName)
  implicit lazy val tagDisplayDecoder: Decoder[TagDisplay] =
    Decoder.decodeString.emap(
      tagDisplay =>
        Right(
          TagDisplay
            .matchTagDisplayOrDefault(tagDisplay)
      )
    )

  implicit val tagDisplayFormatter: JsonFormat[TagDisplay] = new JsonFormat[TagDisplay] {
    override def read(json: JsValue): TagDisplay = json match {
      case JsString(s) =>
        TagDisplay.tagDisplays.getOrElse(s, throw new IllegalArgumentException(s"Unable to convert $s"))
      case other => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: TagDisplay): JsValue = {
      JsString(obj.shortName)
    }
  }

  def matchTagDisplayOrDefault(tagDisplay: String): TagDisplay = {
    tagDisplays.getOrElse(tagDisplay, {
      logger.warn(s"$tagDisplay is not a tagDisplay")
      tagDisplayDefault
    })
  }

  case object Displayed extends TagDisplay { override val shortName: String = "DISPLAYED" }
  case object Hidden extends TagDisplay { override val shortName: String = "HIDDEN" }
  case object Inherit extends TagDisplay { override val shortName: String = "INHERIT" }

}

final case class Tag(tagId: TagId,
                     label: String,
                     display: TagDisplay,
                     tagTypeId: TagTypeId,
                     weight: Float,
                     operationId: Option[OperationId],
                     themeId: Option[ThemeId],
                     country: String,
                     language: String)
    extends MakeSerializable

object Tag {
  implicit val encoder: ObjectEncoder[Tag] = deriveEncoder[Tag]
  implicit val decoder: Decoder[Tag] = deriveDecoder[Tag]
}
