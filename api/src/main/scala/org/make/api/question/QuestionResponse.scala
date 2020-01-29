/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
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

package org.make.api.question

import java.time.ZonedDateTime

import io.circe.{Decoder, Encoder}
import io.swagger.annotations.ApiModelProperty
import org.make.core.CirceFormatters
import org.make.core.question.QuestionId
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.make.core.idea.{CommentQualificationKey, CommentVoteKey, IdeaId, TopIdeaCommentId, TopIdeaId, TopIdeaScores}
import org.make.core.tag.TagId
import org.make.core.user.UserId

import scala.annotation.meta.field

final case class SimpleQuestionResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "3a9cd696-7e0b-4758-952c-04ae6798039a")
  questionId: QuestionId,
  slug: String,
  wording: SimpleQuestionWordingResponse,
  startDate: Option[ZonedDateTime],
  endDate: Option[ZonedDateTime]
)

object SimpleQuestionResponse extends CirceFormatters {
  implicit val encoder: Encoder[SimpleQuestionResponse] = deriveEncoder[SimpleQuestionResponse]
  implicit val decoder: Decoder[SimpleQuestionResponse] = deriveDecoder[SimpleQuestionResponse]
}

final case class SimpleQuestionWordingResponse(title: String, question: String)

object SimpleQuestionWordingResponse extends CirceFormatters {
  implicit val encoder: Encoder[SimpleQuestionWordingResponse] =
    deriveEncoder[SimpleQuestionWordingResponse]
  implicit val decoder: Decoder[SimpleQuestionWordingResponse] =
    deriveDecoder[SimpleQuestionWordingResponse]
}

final case class PopularTagResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "7353ae89-0d05-4014-8aa0-1d7cb0b3aea3") tagId: TagId,
  label: String,
  proposalCount: Long
)

object PopularTagResponse {
  implicit val decoder: Decoder[PopularTagResponse] = deriveDecoder[PopularTagResponse]
  implicit val encoder: Encoder[PopularTagResponse] = deriveEncoder[PopularTagResponse]
}

final case class QuestionPersonalityResponseWithTotal(total: Int, results: Seq[QuestionPersonalityResponse])

object QuestionPersonalityResponseWithTotal {
  implicit val decoder: Decoder[QuestionPersonalityResponseWithTotal] =
    deriveDecoder[QuestionPersonalityResponseWithTotal]
  implicit val encoder: Encoder[QuestionPersonalityResponseWithTotal] =
    deriveEncoder[QuestionPersonalityResponseWithTotal]
}

final case class QuestionPersonalityResponse(userId: UserId,
                                             firstName: Option[String],
                                             lastName: Option[String],
                                             politicalParty: Option[String],
                                             avatarUrl: Option[String],
                                             gender: Option[String])

object QuestionPersonalityResponse {
  implicit val decoder: Decoder[QuestionPersonalityResponse] = deriveDecoder[QuestionPersonalityResponse]
  implicit val encoder: Encoder[QuestionPersonalityResponse] = deriveEncoder[QuestionPersonalityResponse]
}

final case class QuestionTopIdeaWithAvatarResponse(id: TopIdeaId,
                                                   ideaId: IdeaId,
                                                   questionId: QuestionId,
                                                   name: String,
                                                   label: String,
                                                   scores: TopIdeaScores,
                                                   proposalsCount: Int,
                                                   avatars: Seq[String],
                                                   weight: Float,
                                                   commentsCount: Int)

object QuestionTopIdeaWithAvatarResponse {
  implicit val encoder: Encoder[QuestionTopIdeaWithAvatarResponse] = deriveEncoder[QuestionTopIdeaWithAvatarResponse]
}

final case class QuestionTopIdeaCommentsPersonalityResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d5612156-4954-49f7-9c78-0eda3d44164c")
  personalityId: UserId,
  displayName: Option[String],
  avatarUrl: Option[String],
  politicalParty: Option[String]
)

object QuestionTopIdeaCommentsPersonalityResponse {
  implicit val encoder: Encoder[QuestionTopIdeaCommentsPersonalityResponse] =
    deriveEncoder[QuestionTopIdeaCommentsPersonalityResponse]
}

final case class QuestionTopIdeaCommentsResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d5612156-4954-49f7-9c78-0eda3d44164c")
  id: TopIdeaCommentId,
  personality: QuestionTopIdeaCommentsPersonalityResponse,
  comment1: Option[String],
  comment2: Option[String],
  comment3: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "agree")
  vote: CommentVoteKey,
  @(ApiModelProperty @field)(dataType = "string", example = "doable")
  qualification: Option[CommentQualificationKey]
)

object QuestionTopIdeaCommentsResponse {
  implicit val encoder: Encoder[QuestionTopIdeaCommentsResponse] = deriveEncoder[QuestionTopIdeaCommentsResponse]
}

final case class QuestionTopIdeaWithAvatarAndCommentsResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d5612156-4954-49f7-9c78-0eda3d44164c")
  id: TopIdeaId,
  @(ApiModelProperty @field)(dataType = "string", example = "d5612156-4954-49f7-9c78-0eda3d44164c")
  ideaId: IdeaId,
  @(ApiModelProperty @field)(dataType = "string", example = "d5612156-4954-49f7-9c78-0eda3d44164c")
  questionId: QuestionId,
  name: String,
  label: String,
  scores: TopIdeaScores,
  proposalsCount: Int,
  avatars: Seq[String],
  weight: Float,
  comments: Seq[QuestionTopIdeaCommentsResponse]
)

object QuestionTopIdeaWithAvatarAndCommentsResponse {
  implicit val encoder: Encoder[QuestionTopIdeaWithAvatarAndCommentsResponse] =
    deriveEncoder[QuestionTopIdeaWithAvatarAndCommentsResponse]
}

final case class QuestionTopIdeasResponseWithSeed(questionTopIdeas: Seq[QuestionTopIdeaWithAvatarResponse], seed: Int)

object QuestionTopIdeasResponseWithSeed {
  implicit val encoder: Encoder[QuestionTopIdeasResponseWithSeed] = deriveEncoder[QuestionTopIdeasResponseWithSeed]
}

final case class QuestionTopIdeaResponseWithSeed(questionTopIdea: QuestionTopIdeaWithAvatarAndCommentsResponse,
                                                 seed: Int)

object QuestionTopIdeaResponseWithSeed {
  implicit val encoder: Encoder[QuestionTopIdeaResponseWithSeed] = deriveEncoder[QuestionTopIdeaResponseWithSeed]
}
