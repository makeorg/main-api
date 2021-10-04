/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.personality

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.api.question.{QuestionTopIdeaWithAvatarResponse, SimpleQuestionResponse}
import org.make.core.idea.{CommentQualificationKey, CommentVoteKey, TopIdeaComment, TopIdeaCommentId, TopIdeaId}
import org.make.core.user.UserId

import scala.annotation.meta.field

@ApiModel
final case class PersonalityOpinionResponse(
  @(ApiModelProperty @field)(dataType = "org.make.api.question.SimpleQuestionResponse") question: SimpleQuestionResponse,
  @(ApiModelProperty @field)(dataType = "org.make.api.question.QuestionTopIdeaWithAvatarResponse") topIdea: QuestionTopIdeaWithAvatarResponse,
  @(ApiModelProperty @field)(dataType = "org.make.api.personality.TopIdeaCommentResponse") comment: Option[
    TopIdeaCommentResponse
  ]
)

object PersonalityOpinionResponse {
  implicit val decoder: Decoder[PersonalityOpinionResponse] = deriveDecoder[PersonalityOpinionResponse]
  implicit val encoder: Encoder[PersonalityOpinionResponse] = deriveEncoder[PersonalityOpinionResponse]
}

@ApiModel
final case class TopIdeaCommentResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "828cada5-52e1-4a27-9d45-756766c485d2")
  id: TopIdeaCommentId,
  @(ApiModelProperty @field)(dataType = "string", example = "886251c3-e302-49eb-add5-84cabf46878a")
  topIdeaId: TopIdeaId,
  @(ApiModelProperty @field)(dataType = "string", example = "6002582e-60b9-409a-8aec-6eaf0863101a")
  personalityId: UserId,
  comment1: Option[String],
  comment2: Option[String],
  comment3: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "agree", allowableValues = "agree,disagree,other")
  vote: CommentVoteKey,
  @(ApiModelProperty @field)(
    dataType = "string",
    example = "doable",
    allowableValues = "priority,doable,noWay,nonPriority,exists,toBePrecised"
  )
  qualification: Option[CommentQualificationKey]
)

object TopIdeaCommentResponse {
  implicit val decoder: Decoder[TopIdeaCommentResponse] = deriveDecoder[TopIdeaCommentResponse]
  implicit val encoder: Encoder[TopIdeaCommentResponse] = deriveEncoder[TopIdeaCommentResponse]

  def apply(comment: TopIdeaComment): TopIdeaCommentResponse =
    TopIdeaCommentResponse(
      id = comment.topIdeaCommentId,
      topIdeaId = comment.topIdeaId,
      personalityId = comment.personalityId,
      comment1 = comment.comment1,
      comment2 = comment.comment2,
      comment3 = comment.comment3,
      vote = comment.vote,
      qualification = comment.qualification
    )
}
