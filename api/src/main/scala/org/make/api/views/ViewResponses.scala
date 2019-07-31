package org.make.api.views
import java.time.ZonedDateTime

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.swagger.annotations.ApiModelProperty
import org.make.api.proposal.{ProposalResponse, ProposalsResultSeededResponse}
import org.make.api.question.QuestionDetailsResponse
import org.make.core.CirceFormatters
import org.make.core.operation.{CurrentOperation, FeaturedOperation}
import org.make.core.question.QuestionId
import org.make.core.user.indexed.OrganisationSearchResult

import scala.annotation.meta.field

final case class HomeViewResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  popularProposals: Seq[ProposalResponse],
  controverseProposals: Seq[ProposalResponse],
  businessConsultations: Seq[BusinessConsultationResponse],
  featuredConsultations: Seq[FeaturedConsultationResponse],
  currentConsultations: Seq[CurrentConsultationResponse]
)

object HomeViewResponse {
  implicit val encoder: Encoder[HomeViewResponse] = deriveEncoder[HomeViewResponse]
}

final case class BusinessConsultationThemeResponse(gradientStart: String, gradientEnd: String)

object BusinessConsultationThemeResponse {
  implicit val encoder: Encoder[BusinessConsultationThemeResponse] =
    deriveEncoder[BusinessConsultationThemeResponse]
  implicit val decoder: Decoder[BusinessConsultationThemeResponse] = deriveDecoder[BusinessConsultationThemeResponse]
}

final case class BusinessConsultationResponse(theme: BusinessConsultationThemeResponse,
                                              startDate: Option[ZonedDateTime],
                                              endDate: Option[ZonedDateTime],
                                              slug: Option[String],
                                              aboutUrl: Option[String],
                                              question: String)

object BusinessConsultationResponse extends CirceFormatters {
  implicit val encoder: Encoder[BusinessConsultationResponse] = deriveEncoder[BusinessConsultationResponse]
  implicit val decoder: Decoder[BusinessConsultationResponse] = deriveDecoder[BusinessConsultationResponse]
}

final case class FeaturedConsultationResponse(questionId: Option[QuestionId],
                                              questionSlug: Option[String],
                                              title: String,
                                              description: Option[String],
                                              landscapePicture: String,
                                              portraitPicture: String,
                                              altPicture: String,
                                              label: String,
                                              buttonLabel: String,
                                              internalLink: Option[String],
                                              externalLink: Option[String],
                                              slot: Int)

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

final case class CurrentConsultationResponse(questionId: Option[QuestionId],
                                             questionSlug: Option[String],
                                             picture: String,
                                             altPicture: String,
                                             description: String,
                                             linkLabel: String,
                                             internalLink: Option[String],
                                             externalLink: Option[String],
                                             proposalsNumber: Long,
                                             startDate: Option[ZonedDateTime],
                                             endDate: Option[ZonedDateTime])

object CurrentConsultationResponse extends CirceFormatters {
  implicit val encoder: Encoder[CurrentConsultationResponse] = deriveEncoder[CurrentConsultationResponse]
  implicit val decoder: Decoder[CurrentConsultationResponse] = deriveDecoder[CurrentConsultationResponse]

  def apply(current: CurrentOperation,
            slug: Option[String],
            startDate: Option[ZonedDateTime],
            endDate: Option[ZonedDateTime],
            proposalsNumber: Long): CurrentConsultationResponse =
    CurrentConsultationResponse(
      questionId = Some(current.questionId),
      questionSlug = slug,
      picture = current.picture,
      altPicture = current.altPicture,
      description = current.description,
      linkLabel = current.linkLabel,
      internalLink = current.internalLink,
      externalLink = current.externalLink,
      proposalsNumber = proposalsNumber,
      startDate = startDate,
      endDate = endDate
    )
}

final case class SearchViewResponse(proposals: ProposalsResultSeededResponse,
                                    questions: Seq[QuestionDetailsResponse],
                                    organisations: OrganisationSearchResult)

object SearchViewResponse {
  implicit val encoder: Encoder[SearchViewResponse] = deriveEncoder[SearchViewResponse]
  implicit val decoder: Decoder[SearchViewResponse] = deriveDecoder[SearchViewResponse]
}
