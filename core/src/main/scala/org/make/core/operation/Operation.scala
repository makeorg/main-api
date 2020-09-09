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

import enumeratum.{Enum, EnumEntry}
import enumeratum.Circe
import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.MaxSize
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json}
import io.swagger.annotations.ApiModelProperty
import org.make.core.SprayJsonFormatters._
import org.make.core._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.Language
import org.make.core.sequence.SequenceId
import org.make.core.technical.enumeratum.EnumKeys.StringEnumKeys
import org.make.core.user.UserId
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

import scala.annotation.meta.field

case class QuestionWithDetails(question: Question, details: OperationOfQuestion)

final case class Operation(
  status: OperationStatus,
  operationId: OperationId,
  slug: String,
  defaultLanguage: Language,
  allowedSources: Seq[String],
  events: List[OperationAction],
  questions: Seq[QuestionWithDetails],
  operationKind: OperationKind,
  override val createdAt: Option[ZonedDateTime],
  override val updatedAt: Option[ZonedDateTime]
) extends MakeSerializable
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

final case class IntroCard(
  @(ApiModelProperty @field)(dataType = "boolean", example = "true") enabled: Boolean,
  title: Option[String],
  description: Option[String]
)
object IntroCard extends CirceFormatters {
  implicit val encoder: Encoder[IntroCard] = deriveEncoder[IntroCard]
  implicit val decoder: Decoder[IntroCard] = deriveDecoder[IntroCard]
}

final case class PushProposalCard(@(ApiModelProperty @field)(dataType = "boolean", example = "true") enabled: Boolean)
object PushProposalCard extends CirceFormatters {
  implicit val encoder: Encoder[PushProposalCard] = deriveEncoder[PushProposalCard]
  implicit val decoder: Decoder[PushProposalCard] = deriveDecoder[PushProposalCard]
}

final case class SignUpCard(
  @(ApiModelProperty @field)(dataType = "boolean", example = "true") enabled: Boolean,
  title: Option[String],
  nextCtaText: Option[String]
)
object SignUpCard extends CirceFormatters {
  implicit val encoder: Encoder[SignUpCard] = deriveEncoder[SignUpCard]
  implicit val decoder: Decoder[SignUpCard] = deriveDecoder[SignUpCard]
}

final case class FinalCard(
  @(ApiModelProperty @field)(dataType = "boolean", example = "true") enabled: Boolean,
  @(ApiModelProperty @field)(dataType = "boolean", example = "true") sharingEnabled: Boolean,
  title: Option[String],
  shareDescription: Option[String],
  learnMoreTitle: Option[String],
  learnMoreTextButton: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/link")
  linkUrl: Option[String]
)
object FinalCard extends CirceFormatters {
  implicit val encoder: Encoder[FinalCard] = deriveEncoder[FinalCard]
  implicit val decoder: Decoder[FinalCard] = deriveDecoder[FinalCard]
}

final case class SequenceCardsConfiguration(
  introCard: IntroCard,
  pushProposalCard: PushProposalCard,
  signUpCard: SignUpCard,
  finalCard: FinalCard
)

object SequenceCardsConfiguration extends CirceFormatters {
  implicit val encoder: Encoder[SequenceCardsConfiguration] = deriveEncoder[SequenceCardsConfiguration]
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

final case class Metas(
  title: Option[String],
  description: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/picture.png")
  picture: Option[String]
)

object Metas extends CirceFormatters {
  implicit val encoder: Encoder[Metas] = deriveEncoder[Metas]
  implicit val decoder: Decoder[Metas] = deriveDecoder[Metas]
}

final case class QuestionTheme(
  @(ApiModelProperty @field)(dataType = "string", example = "#214284")
  gradientStart: String,
  @(ApiModelProperty @field)(dataType = "string", example = "#428421")
  gradientEnd: String,
  @(ApiModelProperty @field)(dataType = "string", example = "#842142")
  color: String,
  @(ApiModelProperty @field)(dataType = "string", example = "#ff0000")
  fontColor: String,
  @(ApiModelProperty @field)(dataType = "string", example = "#00ff00")
  secondaryColor: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "#0000ff")
  secondaryFontColor: Option[String]
)

object QuestionTheme {
  implicit val encoder: Encoder[QuestionTheme] = deriveEncoder[QuestionTheme]
  implicit val decoder: Decoder[QuestionTheme] = deriveDecoder[QuestionTheme]

  val default: QuestionTheme = {
    val defaultColor = "#000000"
    QuestionTheme(
      gradientStart = defaultColor,
      gradientEnd = defaultColor,
      color = defaultColor,
      fontColor = defaultColor,
      secondaryColor = None,
      secondaryFontColor = None
    )
  }
}

final case class OperationOfQuestion(
  questionId: QuestionId,
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
  consultationImage: Option[String],
  consultationImageAlt: Option[String Refined MaxSize[W.`130`.T]],
  descriptionImage: Option[String],
  descriptionImageAlt: Option[String Refined MaxSize[W.`130`.T]],
  resultsLink: Option[ResultsLink],
  proposalsCount: Int,
  participantsCount: Int,
  actions: Option[String],
  featured: Boolean
) {

  def status: OperationOfQuestion.Status = {
    val now = DateHelper.now()
    if (startDate.exists(_.isAfter(now))) {
      OperationOfQuestion.Status.Upcoming
    } else if (endDate.exists(_.isBefore(now))) {
      OperationOfQuestion.Status.Finished
    } else {
      OperationOfQuestion.Status.Open
    }
  }
}

object OperationOfQuestion {
  val defaultDescription: String = ""

  sealed abstract class Status extends EnumEntry
  object Status extends Enum[Status] {
    case object Upcoming extends Status
    case object Open extends Status
    case object Finished extends Status
    override def values: IndexedSeq[Status] = findValues
    implicit val decoder: Decoder[Status] = Circe.decodeCaseInsensitive(this)
    implicit val encoder: Encoder[Status] = Circe.encoderLowercase(this)
  }
}

final case class OperationAction(
  date: ZonedDateTime = DateHelper.now(),
  makeUserId: UserId,
  actionType: String,
  arguments: Map[String, String] = Map.empty
)

object OperationAction {
  implicit val operationActionFormatter: RootJsonFormat[OperationAction] =
    DefaultJsonProtocol.jsonFormat4(OperationAction.apply)
}

sealed abstract class OperationActionType(val value: String) extends StringEnumEntry

object OperationActionType extends StringEnum[OperationActionType] {

  case object OperationCreateAction extends OperationActionType("create")
  case object OperationUpdateAction extends OperationActionType("update")
  case object OperationActivateAction extends OperationActionType("activate")
  case object OperationArchiveAction extends OperationActionType("archive")

  override def values: IndexedSeq[OperationActionType] = findValues

}

sealed abstract class OperationStatus(val value: String) extends StringEnumEntry

object OperationStatus
    extends StringEnum[OperationStatus]
    with StringCirceEnum[OperationStatus]
    with StringEnumKeys[OperationStatus] {

  case object Pending extends OperationStatus("Pending")
  case object Active extends OperationStatus("Active")
  case object Archived extends OperationStatus("Archived")

  override def values: IndexedSeq[OperationStatus] = findValues

}

case class SimpleOperation(
  operationId: OperationId,
  status: OperationStatus,
  slug: String,
  allowedSources: Seq[String],
  defaultLanguage: Language,
  operationKind: OperationKind,
  createdAt: Option[ZonedDateTime],
  updatedAt: Option[ZonedDateTime]
)

object SimpleOperation extends CirceFormatters {
  implicit val encoder: Encoder[SimpleOperation] = deriveEncoder[SimpleOperation]
  implicit val decoder: Decoder[SimpleOperation] = deriveDecoder[SimpleOperation]
}

sealed abstract class OperationKind(val value: String) extends StringEnumEntry

object OperationKind
    extends StringEnum[OperationKind]
    with StringCirceEnum[OperationKind]
    with StringEnumKeys[OperationKind] {

  case object GreatCause extends OperationKind("GREAT_CAUSE")
  case object PublicConsultation extends OperationKind("PUBLIC_CONSULTATION")
  case object PrivateConsultation extends OperationKind("PRIVATE_CONSULTATION")
  case object BusinessConsultation extends OperationKind("BUSINESS_CONSULTATION")

  override def values: IndexedSeq[OperationKind] = findValues

}
