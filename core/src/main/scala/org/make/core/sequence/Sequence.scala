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

import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric._
import io.circe.{Codec, Decoder, Encoder, Json}
import org.make.core._
import org.make.core.question.QuestionId
import org.make.core.technical.RefinedTypes.Ratio
import spray.json.JsonFormat

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
  mainSequence: ExplorationSequenceConfiguration,
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

  val default: SequenceConfiguration = SequenceConfiguration(
    sequenceId = SequenceId("default-sequence"),
    questionId = QuestionId("default-question"),
    mainSequence = ExplorationSequenceConfiguration.default(ExplorationSequenceConfigurationId("default-sequence")),
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
  def sequenceSize: PosInt
  def maxTestedProposalCount: PosInt
}

final case class ExplorationSequenceConfiguration(
  explorationSequenceConfigurationId: ExplorationSequenceConfigurationId,
  sequenceSize: PosInt,
  maxTestedProposalCount: PosInt,
  newRatio: Ratio,
  controversyRatio: Ratio,
  topSorter: ExplorationSortAlgorithm,
  controversySorter: ExplorationSortAlgorithm,
  keywordsThreshold: Ratio,
  candidatesPoolSize: Int
) extends BasicSequenceConfiguration

object ExplorationSequenceConfiguration {
  def default(id: ExplorationSequenceConfigurationId): ExplorationSequenceConfiguration =
    ExplorationSequenceConfiguration(
      id,
      sequenceSize = 12,
      maxTestedProposalCount = 1000,
      newRatio = 0.3,
      controversyRatio = 0.1,
      topSorter = ExplorationSortAlgorithm.Bandit,
      controversySorter = ExplorationSortAlgorithm.Bandit,
      keywordsThreshold = 0.2,
      candidatesPoolSize = 10
    )
}

final case class ExplorationSequenceConfigurationId(value: String) extends StringValue

object ExplorationSequenceConfigurationId {
  implicit val codec: Codec[ExplorationSequenceConfigurationId] =
    Codec.from(Decoder[String].map(ExplorationSequenceConfigurationId.apply), Encoder[String].contramap(_.value))
}

sealed abstract class ExplorationSortAlgorithm(val value: String) extends StringEnumEntry

object ExplorationSortAlgorithm
    extends StringEnum[ExplorationSortAlgorithm]
    with StringCirceEnum[ExplorationSortAlgorithm] {

  case object Bandit extends ExplorationSortAlgorithm("bandit")
  case object Random extends ExplorationSortAlgorithm("random")
  case object Equalizer extends ExplorationSortAlgorithm("equalizer")

  override def values: IndexedSeq[ExplorationSortAlgorithm] = findValues

}

final case class SpecificSequenceConfiguration(
  specificSequenceConfigurationId: SpecificSequenceConfigurationId,
  sequenceSize: PosInt = 12,
  newProposalsRatio: Double = 0.3,
  maxTestedProposalCount: PosInt = 1000,
  selectionAlgorithmName: SelectionAlgorithmName = SelectionAlgorithmName.Bandit
) extends BasicSequenceConfiguration

object SpecificSequenceConfiguration {

  def otherSequenceDefault(id: SpecificSequenceConfigurationId): SpecificSequenceConfiguration =
    SpecificSequenceConfiguration(
      specificSequenceConfigurationId = id,
      newProposalsRatio = 0,
      selectionAlgorithmName = SelectionAlgorithmName.Random
    )
}

final case class SpecificSequenceConfigurationId(value: String) extends StringValue

object SpecificSequenceConfigurationId {
  implicit val specificSequenceConfigurationIdCodec: Codec[SpecificSequenceConfigurationId] =
    Codec.from(Decoder[String].map(SpecificSequenceConfigurationId.apply), Encoder[String].contramap(_.value))
}
