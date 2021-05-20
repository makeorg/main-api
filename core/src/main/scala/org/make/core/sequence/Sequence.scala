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

package org.make.core.sequence

import java.time.ZonedDateTime
import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe.generic.semiauto._
import io.circe.{Codec, Decoder, Encoder, Json}
import org.make.core.SprayJsonFormatters._
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalId
import org.make.core.question.QuestionId
import org.make.core.reference.Language
import org.make.core.user.UserId
import org.make.core.{MakeSerializable, RequestContext, SprayJsonFormatters, StringValue, Timestamped}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

final case class SequenceTranslation(slug: String, title: String, language: Language) extends MakeSerializable

object SequenceTranslation {
  implicit val encoder: Encoder[SequenceTranslation] = deriveEncoder[SequenceTranslation]
  implicit val decoder: Decoder[SequenceTranslation] = deriveDecoder[SequenceTranslation]

  implicit val sequenceTranslationFormatter: RootJsonFormat[SequenceTranslation] =
    DefaultJsonProtocol.jsonFormat3(SequenceTranslation.apply)

}
final case class SequenceAction(date: ZonedDateTime, user: UserId, actionType: String, arguments: Map[String, String])

object SequenceAction {
  implicit val sequenceActionFormatter: RootJsonFormat[SequenceAction] =
    DefaultJsonProtocol.jsonFormat4(SequenceAction.apply)
}

final case class Sequence(
  sequenceId: SequenceId,
  title: String,
  slug: String,
  proposalIds: Seq[ProposalId] = Seq.empty,
  operationId: Option[OperationId] = None,
  override val createdAt: Option[ZonedDateTime],
  override val updatedAt: Option[ZonedDateTime],
  status: SequenceStatus = SequenceStatus.Unpublished,
  creationContext: RequestContext,
  sequenceTranslation: Seq[SequenceTranslation] = Seq.empty,
  events: List[SequenceAction],
  searchable: Boolean
) extends MakeSerializable
    with Timestamped

object Sequence {
  implicit val sequenceFormatter: RootJsonFormat[Sequence] =
    DefaultJsonProtocol.jsonFormat12(Sequence.apply)
}

final case class SequenceId(value: String) extends StringValue

object SequenceId {
  implicit lazy val sequenceIdEncoder: Encoder[SequenceId] =
    (a: SequenceId) => Json.fromString(a.value)
  implicit lazy val sequenceIdDecoder: Decoder[SequenceId] =
    Decoder.decodeString.map(SequenceId(_))

  implicit val sequenceIdFormatter: JsonFormat[SequenceId] = SprayJsonFormatters.forStringValue(SequenceId.apply)
}

sealed abstract class SequenceStatus(val value: String) extends StringEnumEntry

object SequenceStatus extends StringEnum[SequenceStatus] with StringCirceEnum[SequenceStatus] {

  case object Unpublished extends SequenceStatus("Unpublished")
  case object Published extends SequenceStatus("Published")

  override def values: IndexedSeq[SequenceStatus] = findValues

}

sealed abstract class SelectionAlgorithmName(val value: String) extends StringEnumEntry
object SelectionAlgorithmName extends StringEnum[SelectionAlgorithmName] with StringCirceEnum[SelectionAlgorithmName] {

  final case object Bandit extends SelectionAlgorithmName("Bandit")
  final case object RoundRobin extends SelectionAlgorithmName("RoundRobin")
  final case object Random extends SelectionAlgorithmName("Random")

  override def values: IndexedSeq[SelectionAlgorithmName] = findValues

}

final case class SequenceConfiguration(
  sequenceId: SequenceId,
  questionId: QuestionId,
  mainSequence: SpecificSequenceConfiguration,
  controversial: SpecificSequenceConfiguration,
  popular: SpecificSequenceConfiguration,
  keyword: SpecificSequenceConfiguration,
  newProposalsVoteThreshold: Int,
  testedProposalsEngagementThreshold: Option[Double],
  testedProposalsScoreThreshold: Option[Double],
  testedProposalsControversyThreshold: Option[Double],
  testedProposalsMaxVotesThreshold: Option[Int],
  nonSequenceVotesWeight: Double
)

object SequenceConfiguration {
  implicit val decoder: Decoder[SequenceConfiguration] = deriveDecoder[SequenceConfiguration]
  implicit val encoder: Encoder[SequenceConfiguration] = deriveEncoder[SequenceConfiguration]

  val default: SequenceConfiguration = SequenceConfiguration(
    sequenceId = SequenceId("default-sequence"),
    questionId = QuestionId("default-question"),
    mainSequence = SpecificSequenceConfiguration(SpecificSequenceConfigurationId("default-main")),
    controversial =
      SpecificSequenceConfiguration.otherSequenceDefault(SpecificSequenceConfigurationId("default-controversial")),
    popular = SpecificSequenceConfiguration.otherSequenceDefault(SpecificSequenceConfigurationId("default-popular")),
    keyword = SpecificSequenceConfiguration.otherSequenceDefault(SpecificSequenceConfigurationId("default-keyword")),
    newProposalsVoteThreshold = 10,
    testedProposalsEngagementThreshold = None,
    testedProposalsScoreThreshold = None,
    testedProposalsControversyThreshold = None,
    testedProposalsMaxVotesThreshold = Some(1500),
    nonSequenceVotesWeight = 0.5
  )

}

sealed trait BasicSequenceConfiguration {
  def sequenceSize: Int
  def maxTestedProposalCount: Int
}

final case class ExplorationConfiguration(
  sequenceSize: Int,
  maxTestedProposalCount: Int,
  newRatio: Double,
  controversyRation: Double,
  topSorter: String,
  controversySorter: String
) extends BasicSequenceConfiguration

object ExplorationConfiguration {
  val default: ExplorationConfiguration = ExplorationConfiguration(
    sequenceSize = 12,
    maxTestedProposalCount = 1000,
    newRatio = 0.5,
    controversyRation = 0.1,
    topSorter = "bandit",
    controversySorter = "bandit"
  )
}

final case class SpecificSequenceConfiguration(
  specificSequenceConfigurationId: SpecificSequenceConfigurationId,
  sequenceSize: Int = 12,
  newProposalsRatio: Double = 0.3,
  maxTestedProposalCount: Int = 1000,
  selectionAlgorithmName: SelectionAlgorithmName = SelectionAlgorithmName.Bandit,
  intraIdeaEnabled: Boolean = true,
  intraIdeaMinCount: Int = 1,
  intraIdeaProposalsRatio: Double = 0.0,
  interIdeaCompetitionEnabled: Boolean = true,
  interIdeaCompetitionTargetCount: Int = 20,
  interIdeaCompetitionControversialRatio: Double = 0.0,
  interIdeaCompetitionControversialCount: Int = 2
) extends BasicSequenceConfiguration

object SpecificSequenceConfiguration {
  implicit val decoder: Decoder[SpecificSequenceConfiguration] = deriveDecoder[SpecificSequenceConfiguration]
  implicit val encoder: Encoder[SpecificSequenceConfiguration] = deriveEncoder[SpecificSequenceConfiguration]

  def otherSequenceDefault(id: SpecificSequenceConfigurationId): SpecificSequenceConfiguration =
    SpecificSequenceConfiguration(
      specificSequenceConfigurationId = id,
      newProposalsRatio = 0,
      selectionAlgorithmName = SelectionAlgorithmName.Random,
      intraIdeaEnabled = false,
      intraIdeaMinCount = 0,
      interIdeaCompetitionEnabled = false,
      interIdeaCompetitionTargetCount = 0,
      interIdeaCompetitionControversialCount = 0
    )
}

final case class SpecificSequenceConfigurationId(value: String) extends StringValue

object SpecificSequenceConfigurationId {
  implicit val specificSequenceConfigurationIdCodec: Codec[SpecificSequenceConfigurationId] =
    Codec.from(Decoder[String].map(SpecificSequenceConfigurationId.apply), Encoder[String].contramap(_.value))

  implicit val specificSequenceConfigurationIdFormatter: JsonFormat[SpecificSequenceConfigurationId] =
    SprayJsonFormatters.forStringValue(SpecificSequenceConfigurationId.apply)
}
