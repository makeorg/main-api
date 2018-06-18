package org.make.api.tagtype

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.core.tag.{TagType, TagTypeDisplay, TagTypeId}

import scala.annotation.meta.field

@ApiModel
final case class TagTypeResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "12345678-90ab-cdef-1234-567890abcdef") id: TagTypeId,
  label: String,
  @(ApiModelProperty @field)(dataType = "string", example = "DISPLAYED or HIDDEN") display: TagTypeDisplay,
  weight: Float
)

object TagTypeResponse {
  implicit val encoder: ObjectEncoder[TagTypeResponse] = deriveEncoder[TagTypeResponse]
  implicit val decoder: Decoder[TagTypeResponse] = deriveDecoder[TagTypeResponse]

  def apply(tagType: TagType): TagTypeResponse =
    TagTypeResponse(id = tagType.tagTypeId, label = tagType.label, display = tagType.display, weight = tagType.weight)
}
