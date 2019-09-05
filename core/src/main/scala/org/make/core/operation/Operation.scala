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

package org.make.core.operation

import java.time.ZonedDateTime

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.core.SprayJsonFormatters._
import org.make.core._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.tag.TagId
import org.make.core.user.UserId
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

import scala.annotation.meta.field

case class QuestionWithDetails(question: Question, details: OperationOfQuestion)

final case class Operation(status: OperationStatus,
                           operationId: OperationId,
                           slug: String,
                           defaultLanguage: Language,
                           allowedSources: Seq[String],
                           events: List[OperationAction],
                           questions: Seq[QuestionWithDetails],
                           operationKind: OperationKind,
                           override val createdAt: Option[ZonedDateTime],
                           override val updatedAt: Option[ZonedDateTime])
    extends MakeSerializable
    with Timestamped

final case class OperationId(value: String) extends StringValue

object OperationId {
  implicit lazy val operationIdEncoder: Encoder[OperationId] =
    (a: OperationId) => Json.fromString(a.value)
  implicit lazy val operationIdDecoder: Decoder[OperationId] =
    Decoder.decodeString.map(OperationId(_))

  implicit val operationIdFormatter: JsonFormat[OperationId] = new JsonFormat[OperationId] {
    override def read(json: JsValue): OperationId = json match {
      case JsString(s) => OperationId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: OperationId): JsValue = {
      JsString(obj.value)
    }
  }
}

final case class IntroCard(@(ApiModelProperty @field)(dataType = "boolean", example = "true") enabled: Boolean,
                           title: Option[String],
                           description: Option[String])
object IntroCard extends CirceFormatters {
  implicit val encoder: ObjectEncoder[IntroCard] = deriveEncoder[IntroCard]
  implicit val decoder: Decoder[IntroCard] = deriveDecoder[IntroCard]
}

final case class PushProposalCard(@(ApiModelProperty @field)(dataType = "boolean", example = "true") enabled: Boolean)
object PushProposalCard extends CirceFormatters {
  implicit val encoder: ObjectEncoder[PushProposalCard] = deriveEncoder[PushProposalCard]
  implicit val decoder: Decoder[PushProposalCard] = deriveDecoder[PushProposalCard]
}

final case class SignUpCard(@(ApiModelProperty @field)(dataType = "boolean", example = "true") enabled: Boolean,
                            title: Option[String],
                            nextCtaText: Option[String])
object SignUpCard extends CirceFormatters {
  implicit val encoder: ObjectEncoder[SignUpCard] = deriveEncoder[SignUpCard]
  implicit val decoder: Decoder[SignUpCard] = deriveDecoder[SignUpCard]
}

final case class FinalCard(@(ApiModelProperty @field)(dataType = "boolean", example = "true") enabled: Boolean,
                           @(ApiModelProperty @field)(dataType = "boolean", example = "true") sharingEnabled: Boolean,
                           title: Option[String],
                           shareDescription: Option[String],
                           learnMoreTitle: Option[String],
                           learnMoreTextButton: Option[String],
                           linkUrl: Option[String])
object FinalCard extends CirceFormatters {
  implicit val encoder: ObjectEncoder[FinalCard] = deriveEncoder[FinalCard]
  implicit val decoder: Decoder[FinalCard] = deriveDecoder[FinalCard]
}

final case class SequenceCardsConfiguration(introCard: IntroCard,
                                            pushProposalCard: PushProposalCard,
                                            signUpCard: SignUpCard,
                                            finalCard: FinalCard)

object SequenceCardsConfiguration extends CirceFormatters {
  implicit val encoder: ObjectEncoder[SequenceCardsConfiguration] = deriveEncoder[SequenceCardsConfiguration]
  implicit val decoder: Decoder[SequenceCardsConfiguration] = deriveDecoder[SequenceCardsConfiguration]

  val default: SequenceCardsConfiguration = SequenceCardsConfiguration(
    introCard = IntroCard(enabled = true, title = None, description = None),
    pushProposalCard = PushProposalCard(enabled = true),
    signUpCard = SignUpCard(enabled = true, title = None, nextCtaText = None),
    finalCard = FinalCard(
      enabled = true,
      sharingEnabled = true,
      title = None,
      shareDescription = None,
      learnMoreTitle = None,
      learnMoreTextButton = None,
      linkUrl = None
    )
  )
}

final case class Metas(title: Option[String], description: Option[String], picture: Option[String])

object Metas extends CirceFormatters {
  implicit val encoder: ObjectEncoder[Metas] = deriveEncoder[Metas]
  implicit val decoder: Decoder[Metas] = deriveDecoder[Metas]
}

final case class QuestionTheme(gradientStart: String, gradientEnd: String, color: String, footerFontColor: String)

object QuestionTheme {
  implicit val encoder: ObjectEncoder[QuestionTheme] = deriveEncoder[QuestionTheme]
  implicit val decoder: Decoder[QuestionTheme] = deriveDecoder[QuestionTheme]

  val default: QuestionTheme = {
    val defaultColor = "#000000"
    QuestionTheme(
      gradientStart = defaultColor,
      gradientEnd = defaultColor,
      color = defaultColor,
      footerFontColor = defaultColor
    )
  }
}

final case class OperationOfQuestion(questionId: QuestionId,
                                     operationId: OperationId,
                                     startDate: Option[ZonedDateTime],
                                     endDate: Option[ZonedDateTime],
                                     operationTitle: String,
                                     landingSequenceId: SequenceId,
                                     canPropose: Boolean,
                                     sequenceCardsConfiguration: SequenceCardsConfiguration,
                                     aboutUrl: Option[String],
                                     metas: Metas,
                                     theme: QuestionTheme,
                                     description: String,
                                     imageUrl: Option[String])

object OperationOfQuestion {
  val defaultDescription: String = ""
}

@ApiModel
final case class OperationCountryConfiguration(
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  countryCode: Country,
  @(ApiModelProperty @field)(dataType = "list[string]") tagIds: Seq[TagId],
  @(ApiModelProperty @field)(dataType = "string", example = "fd735649-e63d-4464-9d93-10da54510a12")
  landingSequenceId: SequenceId,
  startDate: Option[ZonedDateTime],
  @(ApiModelProperty @field)(dataType = "string", example = "d2b2694a-25cf-4eaa-9181-026575d58cf8")
  questionId: Option[QuestionId],
  endDate: Option[ZonedDateTime]
)

object OperationCountryConfiguration extends CirceFormatters {
  implicit val encoder: ObjectEncoder[OperationCountryConfiguration] = deriveEncoder[OperationCountryConfiguration]
  implicit val decoder: Decoder[OperationCountryConfiguration] = deriveDecoder[OperationCountryConfiguration]
}

@ApiModel
final case class OperationTranslation(title: String,
                                      @(ApiModelProperty @field)(dataType = "string", example = "fr")
                                      language: Language)
    extends MakeSerializable

object OperationTranslation {
  implicit val operationTranslationFormatter: RootJsonFormat[OperationTranslation] =
    DefaultJsonProtocol.jsonFormat2(OperationTranslation.apply)

  implicit val encoder: ObjectEncoder[OperationTranslation] = deriveEncoder[OperationTranslation]
  implicit val decoder: Decoder[OperationTranslation] = deriveDecoder[OperationTranslation]
}

final case class OperationAction(date: ZonedDateTime = DateHelper.now(),
                                 makeUserId: UserId,
                                 actionType: String,
                                 arguments: Map[String, String] = Map.empty)

object OperationAction {
  implicit val operationActionFormatter: RootJsonFormat[OperationAction] =
    DefaultJsonProtocol.jsonFormat4(OperationAction.apply)
}

sealed trait OperationActionType { val name: String }
case object OperationCreateAction extends OperationActionType { override val name: String = "create" }
case object OperationUpdateAction extends OperationActionType { override val name: String = "update" }
case object OperationActivateAction extends OperationActionType { override val name: String = "activate" }
case object OperationArchiveAction extends OperationActionType { override val name: String = "archive" }

sealed trait OperationStatus {
  def shortName: String
}

object OperationStatus {
  val statusMap: Map[String, OperationStatus] =
    Map(Pending.shortName -> Pending, Active.shortName -> Active, Archived.shortName -> Archived)

  implicit lazy val operationStatusEncoder: Encoder[OperationStatus] = (status: OperationStatus) =>
    Json.fromString(status.shortName)

  implicit lazy val operationStatusDecoder: Decoder[OperationStatus] =
    Decoder.decodeString.emap { value: String =>
      statusMap.get(value) match {
        case Some(operation) => Right(operation)
        case None            => Left(s"$value is not a operation status")
      }
    }

  implicit val operationStatusFormatted: JsonFormat[OperationStatus] = new JsonFormat[OperationStatus] {
    override def read(json: JsValue): OperationStatus = json match {
      case JsString(s) => OperationStatus.statusMap(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: OperationStatus): JsValue = {
      JsString(obj.shortName)
    }
  }

  case object Pending extends OperationStatus {
    override val shortName = "Pending"
  }
  case object Active extends OperationStatus {
    override val shortName = "Active"
  }
  case object Archived extends OperationStatus {
    override val shortName = "Archived"
  }
}

case class SimpleOperation(operationId: OperationId,
                           status: OperationStatus,
                           slug: String,
                           allowedSources: Seq[String],
                           defaultLanguage: Language,
                           operationKind: OperationKind,
                           createdAt: Option[ZonedDateTime],
                           updatedAt: Option[ZonedDateTime])

object SimpleOperation extends CirceFormatters {
  implicit val encoder: ObjectEncoder[SimpleOperation] = deriveEncoder[SimpleOperation]
  implicit val decoder: Decoder[SimpleOperation] = deriveDecoder[SimpleOperation]
}

sealed trait OperationKind { def shortName: String }

object OperationKind {
  val kindMap: Map[String, OperationKind] =
    Map(
      GreatCause.shortName -> GreatCause,
      PublicConsultation.shortName -> PublicConsultation,
      PrivateConsultation.shortName -> PrivateConsultation,
      BusinessConsultation.shortName -> BusinessConsultation
    )

  implicit lazy val operationKindEncoder: Encoder[OperationKind] = (kind: OperationKind) =>
    Json.fromString(kind.shortName)

  implicit lazy val operationKindDecoder: Decoder[OperationKind] =
    Decoder.decodeString.emap { value: String =>
      kindMap.get(value) match {
        case Some(kind) => Right(kind)
        case None       => Left(s"$value is not a operation kind")
      }
    }

  implicit val operationKindFormatted: JsonFormat[OperationKind] = new JsonFormat[OperationKind] {
    override def read(json: JsValue): OperationKind = json match {
      case JsString(s) => OperationKind.kindMap(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: OperationKind): JsValue = {
      JsString(obj.shortName)
    }
  }

  case object GreatCause extends OperationKind { override val shortName: String = "GREAT_CAUSE" }
  case object PublicConsultation extends OperationKind { override val shortName: String = "PUBLIC_CONSULTATION" }
  case object PrivateConsultation extends OperationKind { override val shortName: String = "PRIVATE_CONSULTATION" }
  case object BusinessConsultation extends OperationKind { override val shortName: String = "BUSINESS_CONSULTATION" }
}

case class FeaturedOperation(featuredOperationId: FeaturedOperationId,
                             questionId: Option[QuestionId],
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

object FeaturedOperation {
  implicit lazy val featuredOperationEncoder: ObjectEncoder[FeaturedOperation] = deriveEncoder[FeaturedOperation]
  implicit lazy val featuredOperationDecoder: Decoder[FeaturedOperation] = deriveDecoder[FeaturedOperation]
}

case class FeaturedOperationId(value: String) extends StringValue

object FeaturedOperationId {
  implicit lazy val featuredOperationIdEncoder: Encoder[FeaturedOperationId] =
    (a: FeaturedOperationId) => Json.fromString(a.value)
  implicit lazy val featuredOperationIdDecoder: Decoder[FeaturedOperationId] =
    Decoder.decodeString.map(FeaturedOperationId(_))

  implicit val featuredOperationIdFormatter: JsonFormat[FeaturedOperationId] = new JsonFormat[FeaturedOperationId] {
    override def read(json: JsValue): FeaturedOperationId = json match {
      case JsString(s) => FeaturedOperationId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: FeaturedOperationId): JsValue = {
      JsString(obj.value)
    }
  }
}

case class CurrentOperation(currentOperationId: CurrentOperationId,
                            questionId: QuestionId,
                            label: String,
                            description: String,
                            picture: String,
                            altPicture: String,
                            linkLabel: String,
                            internalLink: Option[String],
                            externalLink: Option[String])

object CurrentOperation {
  implicit lazy val currentOperationEncoder: ObjectEncoder[CurrentOperation] = deriveEncoder[CurrentOperation]
  implicit lazy val currentOperationDecoder: Decoder[CurrentOperation] = deriveDecoder[CurrentOperation]
}

case class CurrentOperationId(value: String) extends StringValue

object CurrentOperationId {
  implicit lazy val currentOperationIdEncoder: Encoder[CurrentOperationId] =
    (a: CurrentOperationId) => Json.fromString(a.value)
  implicit lazy val currentOperationIdDecoder: Decoder[CurrentOperationId] =
    Decoder.decodeString.map(CurrentOperationId(_))

  implicit val currentOperationIdFormatter: JsonFormat[CurrentOperationId] = new JsonFormat[CurrentOperationId] {
    override def read(json: JsValue): CurrentOperationId = json match {
      case JsString(s) => CurrentOperationId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: CurrentOperationId): JsValue = {
      JsString(obj.value)
    }
  }
}
