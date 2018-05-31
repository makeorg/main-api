package org.make.api.tag

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.core.operation.OperationId
import org.make.core.reference.ThemeId
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}

import scala.annotation.meta.field

@ApiModel
case class TagResponse(@(ApiModelProperty @field)(dataType = "string", example = "tag-slug") id: TagId,
                       label: String,
                       @(ApiModelProperty @field)(dataType = "string", example = "INHERIT") display: TagDisplay,
                       @(ApiModelProperty @field)(dataType = "string") tagTypeId: TagTypeId,
                       weight: Float,
                       @(ApiModelProperty @field)(dataType = "string") operationId: Option[OperationId],
                       @(ApiModelProperty @field)(dataType = "string") themeId: Option[ThemeId],
                       country: String,
                       language: String)

object TagResponse {
  implicit val encoder: ObjectEncoder[TagResponse] = deriveEncoder[TagResponse]
  implicit val decoder: Decoder[TagResponse] = deriveDecoder[TagResponse]

  def apply(tag: Tag): TagResponse =
    TagResponse(
      id = tag.tagId,
      label = tag.label,
      display = tag.display,
      tagTypeId = tag.tagTypeId,
      weight = tag.weight,
      operationId = tag.operationId,
      themeId = tag.themeId,
      country = tag.country,
      language = tag.language
    )
}
