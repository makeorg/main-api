/*
 *  Make.org Core API
 *  Copyright (C) 2019 Make.org
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

package org.make.api.views
import java.net.URL
import java.time.ZonedDateTime

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations.ApiModelProperty
import org.make.api.organisation.OrganisationsSearchResultResponse
import org.make.api.proposal.{ProposalResponse, ProposalsResultSeededResponse}
import org.make.api.question.QuestionOfOperationResponse
import org.make.api.views.HomePageViewResponse.{Highlights, PostResponse}
import org.make.core.CirceFormatters
import org.make.core.operation.indexed.OperationOfQuestionSearchResult
import org.make.core.operation.{CurrentOperation, FeaturedOperation}
import org.make.core.post.indexed.IndexedPost
import org.make.core.question.QuestionId

import scala.annotation.meta.field

final case class HomeViewResponse(
  popularProposals: Seq[ProposalResponse],
  controverseProposals: Seq[ProposalResponse],
  businessConsultations: Seq[BusinessConsultationResponse],
  featuredConsultations: Seq[FeaturedConsultationResponse],
  currentConsultations: Seq[CurrentConsultationResponse]
)

object HomeViewResponse {
  implicit val encoder: Encoder[HomeViewResponse] = deriveEncoder[HomeViewResponse]
}

final case class HomePageViewResponse(
  highlights: Highlights,
  currentQuestions: Seq[QuestionOfOperationResponse],
  featuredQuestions: Seq[QuestionOfOperationResponse],
  posts: Seq[PostResponse]
)

object HomePageViewResponse extends CirceFormatters {
  final case class PostResponse(
    title: String,
    description: String,
    @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/picture.png") picture: URL,
    @(ApiModelProperty @field)(dataType = "string", example = "picture alternative") alt: Option[String],
    @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/link") link: URL
  )
  object PostResponse {
    def fromIndexedPost(post: IndexedPost): PostResponse =
      PostResponse(
        title = post.name,
        description = post.summary,
        picture = post.thumbnailUrl,
        alt = post.thumbnailAlt,
        link = post.sourceUrl
      )

  }
  final case class Highlights(participantsCount: Int, proposalsCount: Int, partnersCount: Int)

  implicit val postEncoder: Encoder[PostResponse] = deriveEncoder
  implicit val highlightsEncoder: Encoder[Highlights] = deriveEncoder
  implicit val encoder: Encoder[HomePageViewResponse] = deriveEncoder
}

final case class BusinessConsultationThemeResponse(gradientStart: String, gradientEnd: String)

object BusinessConsultationThemeResponse {
  implicit val encoder: Encoder[BusinessConsultationThemeResponse] =
    deriveEncoder[BusinessConsultationThemeResponse]
  implicit val decoder: Decoder[BusinessConsultationThemeResponse] = deriveDecoder[BusinessConsultationThemeResponse]
}

final case class BusinessConsultationResponse(
  theme: BusinessConsultationThemeResponse,
  @(ApiModelProperty @field)(dataType = "dateTime") startDate: Option[ZonedDateTime],
  @(ApiModelProperty @field)(dataType = "dateTime") endDate: Option[ZonedDateTime],
  slug: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/about") aboutUrl: Option[String],
  question: String
)

object BusinessConsultationResponse extends CirceFormatters {
  implicit val encoder: Encoder[BusinessConsultationResponse] = deriveEncoder[BusinessConsultationResponse]
  implicit val decoder: Decoder[BusinessConsultationResponse] = deriveDecoder[BusinessConsultationResponse]
}

final case class FeaturedConsultationResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "fd7dedff-79ba-4eef-8c3a-c12002d3453e")
  questionId: Option[QuestionId],
  questionSlug: Option[String],
  title: String,
  description: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/landscape-picture.png") landscapePicture: String,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/portrait-picture.png") portraitPicture: String,
  @(ApiModelProperty @field)(dataType = "string", example = "picture alternative") altPicture: String,
  label: String,
  buttonLabel: String,
  internalLink: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/external") externalLink: Option[
    String
  ],
  slot: Int
)

object FeaturedConsultationResponse {
  implicit val encoder: Encoder[FeaturedConsultationResponse] = deriveEncoder[FeaturedConsultationResponse]
  implicit val decoder: Decoder[FeaturedConsultationResponse] = deriveDecoder[FeaturedConsultationResponse]

  def apply(featured: FeaturedOperation, slug: Option[String]): FeaturedConsultationResponse =
    FeaturedConsultationResponse(
      questionId = featured.questionId,
      questionSlug = slug,
      title = featured.title,
      description = featured.description,
      landscapePicture = featured.landscapePicture,
      portraitPicture = featured.portraitPicture,
      altPicture = featured.altPicture,
      label = featured.label,
      buttonLabel = featured.buttonLabel,
      internalLink = featured.internalLink,
      externalLink = featured.externalLink,
      slot = featured.slot
    )
}

final case class CurrentConsultationResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "fd7dedff-79ba-4eef-8c3a-c12002d3453e")
  questionId: Option[QuestionId],
  questionSlug: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/picture.png") picture: String,
  @(ApiModelProperty @field)(dataType = "string", example = "picture alternative") altPicture: String,
  description: String,
  label: String,
  linkLabel: String,
  internalLink: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/external") externalLink: Option[
    String
  ],
  proposalsNumber: Long,
  @(ApiModelProperty @field)(dataType = "dateTime") startDate: Option[ZonedDateTime],
  @(ApiModelProperty @field)(dataType = "dateTime") endDate: Option[ZonedDateTime]
)

object CurrentConsultationResponse extends CirceFormatters {
  implicit val encoder: Encoder[CurrentConsultationResponse] = deriveEncoder[CurrentConsultationResponse]
  implicit val decoder: Decoder[CurrentConsultationResponse] = deriveDecoder[CurrentConsultationResponse]

  def apply(
    current: CurrentOperation,
    slug: Option[String],
    startDate: Option[ZonedDateTime],
    endDate: Option[ZonedDateTime],
    proposalsNumber: Long
  ): CurrentConsultationResponse =
    CurrentConsultationResponse(
      questionId = Some(current.questionId),
      questionSlug = slug,
      picture = current.picture,
      altPicture = current.altPicture,
      description = current.description,
      label = current.label,
      linkLabel = current.linkLabel,
      internalLink = current.internalLink,
      externalLink = current.externalLink,
      proposalsNumber = proposalsNumber,
      startDate = startDate,
      endDate = endDate
    )
}

final case class SearchViewResponse(
  proposals: ProposalsResultSeededResponse,
  questions: OperationOfQuestionSearchResult,
  organisations: OrganisationsSearchResultResponse
)

object SearchViewResponse {
  implicit val encoder: Encoder[SearchViewResponse] = deriveEncoder[SearchViewResponse]
  implicit val decoder: Decoder[SearchViewResponse] = deriveDecoder[SearchViewResponse]
}
