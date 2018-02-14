package org.make.api.tag

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.core.MakeSerializable
import org.make.core.reference.{Tag, TagId}

import scala.annotation.meta.field

@ApiModel
case class TagResponse(@(ApiModelProperty @field)(dataType = "string", example = "tag-slug") id: TagId, label: String)
    extends MakeSerializable

object TagResponse {
  implicit val encoder: ObjectEncoder[TagResponse] = deriveEncoder[TagResponse]
  implicit val decoder: Decoder[TagResponse] = deriveDecoder[TagResponse]

  def apply(tag: Tag): TagResponse =
    TagResponse(id = tag.tagId, label = tag.label)
}
