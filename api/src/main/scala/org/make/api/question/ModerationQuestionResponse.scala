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
import java.time.{LocalDate, ZonedDateTime}

import cats.data.NonEmptyList
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}
import io.circe.{Codec, Decoder, Encoder}
import io.circe.refined._
import io.swagger.annotations.ApiModelProperty
import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.MaxSize
import org.make.api.operation.ResultsLinkResponse
import org.make.core.operation._
import org.make.core.operation.indexed.IndexedOperationOfQuestion
import org.make.core.partner.{Partner, PartnerKind}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.user.UserId
import org.make.core.{CirceFormatters, SlugHelper}

import scala.annotation.meta.field

final case class ModerationQuestionResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d2b2694a-25cf-4eaa-9181-026575d58cf8")
  id: QuestionId,
  slug: String,
  question: String,
  shortTitle: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  countries: NonEmptyList[Country],
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language
)
object ModerationQuestionResponse {

  def apply(question: Question): ModerationQuestionResponse = ModerationQuestionResponse(
    id = question.questionId,
    slug = question.slug,
    question = question.question,
    shortTitle = question.shortTitle,
    countries = question.countries,
    language = question.language
  )

  implicit val encoder: Encoder[ModerationQuestionResponse] = deriveEncoder[ModerationQuestionResponse]
  implicit val decoder: Decoder[ModerationQuestionResponse] = deriveDecoder[ModerationQuestionResponse]
}

final case class WordingResponse(title: String, question: String, description: String, metas: Metas)

object WordingResponse {
  implicit val encoder: Encoder[WordingResponse] = deriveEncoder[WordingResponse]
  implicit val decoder: Decoder[WordingResponse] = deriveDecoder[WordingResponse]
}

final case class IntroCardResponse(
  @(ApiModelProperty @field)(dataType = "boolean", example = "true") enabled: Boolean,
  title: Option[String],
  description: Option[String]
)
object IntroCardResponse extends CirceFormatters {
  implicit val encoder: Encoder[IntroCardResponse] = deriveEncoder[IntroCardResponse]
  implicit val decoder: Decoder[IntroCardResponse] = deriveDecoder[IntroCardResponse]
}

final case class PushProposalCardResponse(
  @(ApiModelProperty @field)(dataType = "boolean", example = "true") enabled: Boolean
)
object PushProposalCardResponse extends CirceFormatters {
  implicit val encoder: Encoder[PushProposalCardResponse] = deriveEncoder[PushProposalCardResponse]
  implicit val decoder: Decoder[PushProposalCardResponse] = deriveDecoder[PushProposalCardResponse]
}

final case class SignUpCardResponse(
  @(ApiModelProperty @field)(dataType = "boolean", example = "true") enabled: Boolean,
  title: Option[String],
  nextCtaText: Option[String]
)
object SignUpCardResponse extends CirceFormatters {
  implicit val encoder: Encoder[SignUpCardResponse] = deriveEncoder[SignUpCardResponse]
  implicit val decoder: Decoder[SignUpCardResponse] = deriveDecoder[SignUpCardResponse]
}

final case class FinalCardResponse(
  @(ApiModelProperty @field)(dataType = "boolean", example = "true") enabled: Boolean,
  @(ApiModelProperty @field)(dataType = "boolean", example = "true") withSharing: Boolean,
  title: Option[String],
  share: Option[String],
  learnMoreTitle: Option[String],
  learnMoreTextButton: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/link")
  linkUrl: Option[String]
)
object FinalCardResponse extends CirceFormatters {
  implicit val encoder: Encoder[FinalCardResponse] = deriveEncoder[FinalCardResponse]
  implicit val decoder: Decoder[FinalCardResponse] = deriveDecoder[FinalCardResponse]
}

final case class SequenceCardsConfigurationResponse(
  introCard: IntroCardResponse,
  pushProposalCard: PushProposalCardResponse,
  signUpCard: SignUpCardResponse,
  finalCard: FinalCardResponse
)

object SequenceCardsConfigurationResponse extends CirceFormatters {
  implicit val encoder: Encoder[SequenceCardsConfigurationResponse] =
    deriveEncoder[SequenceCardsConfigurationResponse]
  implicit val decoder: Decoder[SequenceCardsConfigurationResponse] = deriveDecoder[SequenceCardsConfigurationResponse]

  def apply(sequenceCardConfiguration: SequenceCardsConfiguration): SequenceCardsConfigurationResponse = {
    SequenceCardsConfigurationResponse(
      introCard = IntroCardResponse(
        enabled = sequenceCardConfiguration.introCard.enabled,
        title = sequenceCardConfiguration.introCard.title,
        description = sequenceCardConfiguration.introCard.description
      ),
      pushProposalCard = PushProposalCardResponse(enabled = sequenceCardConfiguration.pushProposalCard.enabled),
      signUpCard = SignUpCardResponse(
        enabled = sequenceCardConfiguration.signUpCard.enabled,
        title = sequenceCardConfiguration.signUpCard.title,
        nextCtaText = sequenceCardConfiguration.signUpCard.nextCtaText
      ),
      finalCard = FinalCardResponse(
        enabled = sequenceCardConfiguration.finalCard.enabled,
        withSharing = sequenceCardConfiguration.finalCard.sharingEnabled,
        title = sequenceCardConfiguration.finalCard.title,
        share = sequenceCardConfiguration.finalCard.shareDescription,
        learnMoreTitle = sequenceCardConfiguration.finalCard.learnMoreTitle,
        learnMoreTextButton = sequenceCardConfiguration.finalCard.learnMoreTextButton,
        linkUrl = sequenceCardConfiguration.finalCard.linkUrl
      )
    )
  }
}

final case class OrganisationPartnerResponse(organisationId: UserId, slug: String)

object OrganisationPartnerResponse {
  implicit val encoder: Encoder[OrganisationPartnerResponse] = deriveEncoder[OrganisationPartnerResponse]
  implicit val decoder: Decoder[OrganisationPartnerResponse] = deriveDecoder[OrganisationPartnerResponse]
}

final case class QuestionPartnerResponse(
  name: String,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/logo.png")
  logo: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/link")
  link: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55")
  organisation: Option[OrganisationPartnerResponse],
  @(ApiModelProperty @field)(dataType = "string", example = "FOUNDER")
  partnerKind: PartnerKind,
  weight: Float
)

object QuestionPartnerResponse extends CirceFormatters {
  def apply(partner: Partner): QuestionPartnerResponse = QuestionPartnerResponse(
    name = partner.name,
    logo = partner.logo,
    link = partner.link,
    organisation = partner.organisationId.map { id =>
      OrganisationPartnerResponse(organisationId = id, slug = SlugHelper(partner.name))
    },
    partnerKind = partner.partnerKind,
    weight = partner.weight
  )

  implicit val encoder: Encoder[QuestionPartnerResponse] = deriveEncoder[QuestionPartnerResponse]
  implicit val decoder: Decoder[QuestionPartnerResponse] = deriveDecoder[QuestionPartnerResponse]
}

final case class OperationOfQuestionHighlightsResponse(
  votesTarget: Int,
  votesCount: Int,
  participantsCount: Int,
  proposalsCount: Int
)

object OperationOfQuestionHighlightsResponse {
  implicit val codec: Codec[OperationOfQuestionHighlightsResponse] = deriveCodec
}

final case class OperationOfQuestionTimelineResponse(
  startDate: ZonedDateTime,
  endDate: ZonedDateTime,
  resultDate: Option[LocalDate],
  workshopDate: Option[LocalDate],
  actionDate: Option[LocalDate]
)

object OperationOfQuestionTimelineResponse extends CirceFormatters {
  implicit val codec: Codec[OperationOfQuestionTimelineResponse] = deriveCodec
}

final case class QuestionDetailsResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d2b2694a-25cf-4eaa-9181-026575d58cf8")
  questionId: QuestionId,
  @(ApiModelProperty @field)(dataType = "string", example = "49207ae1-0732-42f5-a0d0-af4ff8c4c2de")
  operationId: OperationId,
  wording: WordingResponse,
  question: String,
  shortTitle: Option[String],
  slug: String,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  countries: NonEmptyList[Country],
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language,
  @(ApiModelProperty @field)(dataType = "dateTime")
  startDate: ZonedDateTime,
  @(ApiModelProperty @field)(dataType = "dateTime")
  endDate: ZonedDateTime,
  @(ApiModelProperty @field)(dataType = "string", example = "fd735649-e63d-4464-9d93-10da54510a12")
  landingSequenceId: SequenceId,
  canPropose: Boolean,
  @(ApiModelProperty @field)(dataType = "string", example = "BUSINESS_CONSULTATION")
  operationKind: OperationKind,
  sequenceConfig: SequenceCardsConfigurationResponse,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/about")
  aboutUrl: Option[String],
  partners: Seq[QuestionPartnerResponse],
  theme: QuestionThemeResponse,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/consultation.png")
  consultationImage: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "consultation alternative")
  consultationImageAlt: Option[String Refined MaxSize[W.`130`.T]],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/description.png")
  descriptionImage: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "description alternative")
  descriptionImageAlt: Option[String Refined MaxSize[W.`130`.T]],
  displayResults: Boolean,
  operation: QuestionsOfOperationResponse,
  activeFeatures: Seq[String],
  featured: Boolean,
  highlights: OperationOfQuestionHighlightsResponse,
  timeline: OperationOfQuestionTimelineResponse,
  controversyCount: Int,
  topProposalCount: Int
)

object QuestionDetailsResponse extends CirceFormatters {
  def apply(
    question: Question,
    operation: Operation,
    operationOfQuestion: OperationOfQuestion,
    partners: Seq[Partner],
    questionsOfOperation: Seq[QuestionOfOperationResponse],
    activeFeatures: Seq[String]
  ): QuestionDetailsResponse = QuestionDetailsResponse(
    questionId = question.questionId,
    operationId = operation.operationId,
    wording = WordingResponse(
      title = operationOfQuestion.operationTitle,
      question = question.question,
      description = operationOfQuestion.description,
      metas = operationOfQuestion.metas
    ),
    question = question.question,
    shortTitle = question.shortTitle,
    slug = question.slug,
    countries = question.countries,
    language = question.language,
    startDate = operationOfQuestion.startDate,
    endDate = operationOfQuestion.endDate,
    landingSequenceId = operationOfQuestion.landingSequenceId,
    canPropose = operationOfQuestion.canPropose,
    operationKind = operation.operationKind,
    sequenceConfig = SequenceCardsConfigurationResponse.apply(operationOfQuestion.sequenceCardsConfiguration),
    aboutUrl = operationOfQuestion.aboutUrl,
    partners = partners.map(QuestionPartnerResponse.apply),
    theme = QuestionThemeResponse.fromQuestionTheme(operationOfQuestion.theme),
    consultationImage = operationOfQuestion.consultationImage,
    consultationImageAlt = operationOfQuestion.consultationImageAlt,
    descriptionImage = operationOfQuestion.descriptionImage,
    descriptionImageAlt = operationOfQuestion.descriptionImageAlt,
    displayResults = operationOfQuestion.resultsLink.isDefined,
    operation = QuestionsOfOperationResponse(questionsOfOperation),
    activeFeatures = activeFeatures,
    featured = operationOfQuestion.featured,
    highlights = OperationOfQuestionHighlightsResponse(
      votesTarget = operationOfQuestion.votesTarget,
      votesCount = operationOfQuestion.votesCount,
      participantsCount = operationOfQuestion.participantsCount,
      proposalsCount = operationOfQuestion.proposalsCount
    ),
    timeline = OperationOfQuestionTimelineResponse(
      startDate = operationOfQuestion.startDate,
      endDate = operationOfQuestion.endDate,
      resultDate = operationOfQuestion.resultDate,
      workshopDate = operationOfQuestion.workshopDate,
      actionDate = operationOfQuestion.actionDate
    ),
    controversyCount = 0,
    topProposalCount = 0
  )

  implicit val encoder: Encoder[QuestionDetailsResponse] = deriveEncoder[QuestionDetailsResponse]
  implicit val decoder: Decoder[QuestionDetailsResponse] = deriveDecoder[QuestionDetailsResponse]
}

final case class QuestionOfOperationResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "1783f622-b9ea-4f8d-bb39-35bbfdc9ce88")
  questionId: QuestionId,
  questionSlug: String,
  question: String,
  shortTitle: Option[String],
  operationTitle: String,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/consultation.png")
  consultationImage: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "consultation image alternative")
  consultationImageAlt: Option[String Refined MaxSize[W.`130`.T]],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/description.png")
  descriptionImage: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "description image alternative")
  descriptionImageAlt: Option[String Refined MaxSize[W.`130`.T]],
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  countries: NonEmptyList[Country],
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language,
  @(ApiModelProperty @field)(dataType = "dateTime")
  startDate: ZonedDateTime,
  @(ApiModelProperty @field)(dataType = "dateTime")
  endDate: ZonedDateTime,
  theme: QuestionThemeResponse,
  displayResults: Boolean,
  resultsLink: Option[ResultsLinkResponse],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/about")
  aboutUrl: Option[String],
  actions: Option[String],
  featured: Boolean,
  participantsCount: Int,
  proposalsCount: Int
)

object QuestionOfOperationResponse extends CirceFormatters {
  def apply(indexedOperationOfQuestion: IndexedOperationOfQuestion): QuestionOfOperationResponse =
    QuestionOfOperationResponse(
      questionId = indexedOperationOfQuestion.questionId,
      questionSlug = indexedOperationOfQuestion.slug,
      question = indexedOperationOfQuestion.question,
      shortTitle = indexedOperationOfQuestion.questionShortTitle,
      operationTitle = indexedOperationOfQuestion.operationTitle,
      consultationImage = indexedOperationOfQuestion.consultationImage,
      consultationImageAlt = indexedOperationOfQuestion.consultationImageAlt,
      descriptionImage = indexedOperationOfQuestion.descriptionImage,
      descriptionImageAlt = indexedOperationOfQuestion.descriptionImageAlt,
      countries = indexedOperationOfQuestion.countries,
      language = indexedOperationOfQuestion.language,
      startDate = indexedOperationOfQuestion.startDate,
      endDate = indexedOperationOfQuestion.endDate,
      theme = QuestionThemeResponse.fromQuestionTheme(indexedOperationOfQuestion.theme),
      displayResults = indexedOperationOfQuestion.resultsLink.isDefined,
      resultsLink = indexedOperationOfQuestion.resultsLink
        .flatMap(ResultsLink.parse)
        .map(ResultsLinkResponse.apply),
      aboutUrl = indexedOperationOfQuestion.aboutUrl,
      actions = indexedOperationOfQuestion.actions,
      featured = indexedOperationOfQuestion.featured,
      participantsCount = indexedOperationOfQuestion.participantsCount,
      proposalsCount = indexedOperationOfQuestion.proposalsCount
    )

  implicit val encoder: Encoder[QuestionOfOperationResponse] = deriveEncoder[QuestionOfOperationResponse]
  implicit val decoder: Decoder[QuestionOfOperationResponse] = deriveDecoder[QuestionOfOperationResponse]
}

final case class QuestionThemeResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "#214284") gradientStart: String,
  @(ApiModelProperty @field)(dataType = "string", example = "#428421") gradientEnd: String,
  @(ApiModelProperty @field)(dataType = "string", example = "#842142") color: String,
  @(ApiModelProperty @field)(dataType = "string", example = "#ff0000") fontColor: String,
  @(ApiModelProperty @field)(dataType = "string", example = "#00ff00") secondaryColor: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "#0000ff") secondaryFontColor: Option[String]
)

object QuestionThemeResponse {
  def fromQuestionTheme(theme: QuestionTheme): QuestionThemeResponse = {
    QuestionThemeResponse(
      gradientStart = theme.gradientStart,
      gradientEnd = theme.gradientEnd,
      color = theme.color,
      fontColor = theme.fontColor,
      secondaryColor = theme.secondaryColor,
      secondaryFontColor = theme.secondaryFontColor
    )
  }

  implicit val encoder: Encoder[QuestionThemeResponse] = deriveEncoder[QuestionThemeResponse]
  implicit val decoder: Decoder[QuestionThemeResponse] = deriveDecoder[QuestionThemeResponse]
}

final case class QuestionsOfOperationResponse(questions: Seq[QuestionOfOperationResponse])

object QuestionsOfOperationResponse {
  implicit val encoder: Encoder[QuestionsOfOperationResponse] = deriveEncoder[QuestionsOfOperationResponse]
  implicit val decoder: Decoder[QuestionsOfOperationResponse] = deriveDecoder[QuestionsOfOperationResponse]
}
