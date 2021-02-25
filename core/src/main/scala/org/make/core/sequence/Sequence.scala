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
import io.circe.{Decoder, Encoder, Json}
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
  mainSequence: SpecificSequenceConfiguration = SpecificSequenceConfiguration(),
  controversial: SpecificSequenceConfiguration = SpecificSequenceConfiguration(),
  popular: SpecificSequenceConfiguration = SpecificSequenceConfiguration(),
  keyword: SpecificSequenceConfiguration = SpecificSequenceConfiguration(),
  newProposalsVoteThreshold: Int = 10,
  testedProposalsEngagementThreshold: Option[Double] = None,
  testedProposalsScoreThreshold: Option[Double] = None,
  testedProposalsControversyThreshold: Option[Double] = None,
  testedProposalsMaxVotesThreshold: Option[Int] = None,
  nonSequenceVotesWeight: Double = 0.5
)

object SequenceConfiguration {
  implicit val decoder: Decoder[SequenceConfiguration] = deriveDecoder[SequenceConfiguration]
  implicit val encoder: Encoder[SequenceConfiguration] = deriveEncoder[SequenceConfiguration]

  val default: SequenceConfiguration = SequenceConfiguration(
    sequenceId = SequenceId("default-sequence"),
    questionId = QuestionId("default-question"),
    mainSequence = SpecificSequenceConfiguration.default,
    controversial = SpecificSequenceConfiguration.default,
    popular = SpecificSequenceConfiguration.default,
    keyword = SpecificSequenceConfiguration.default,
    newProposalsVoteThreshold = 10,
    testedProposalsEngagementThreshold = Some(0.8),
    testedProposalsScoreThreshold = None,
    testedProposalsControversyThreshold = None,
    testedProposalsMaxVotesThreshold = Some(1500),
    nonSequenceVotesWeight = 0.5
  )

}

final case class SpecificSequenceConfiguration(
  sequenceSize: Int = 12,
  newProposalsRatio: Double = 0.5,
  maxTestedProposalCount: Int = 1000,
  selectionAlgorithmName: SelectionAlgorithmName = SelectionAlgorithmName.Bandit,
  intraIdeaEnabled: Boolean = true,
  intraIdeaMinCount: Int = 1,
  intraIdeaProposalsRatio: Double = 0.0,
  interIdeaCompetitionEnabled: Boolean = true,
  interIdeaCompetitionTargetCount: Int = 50,
  interIdeaCompetitionControversialRatio: Double = 0.0,
  interIdeaCompetitionControversialCount: Int = 0
)

object SpecificSequenceConfiguration {
  implicit val decoder: Decoder[SpecificSequenceConfiguration] = deriveDecoder[SpecificSequenceConfiguration]
  implicit val encoder: Encoder[SpecificSequenceConfiguration] = deriveEncoder[SpecificSequenceConfiguration]

  val default: SpecificSequenceConfiguration = SpecificSequenceConfiguration(
    sequenceSize = 12,
    newProposalsRatio = 0.5,
    maxTestedProposalCount = 1000,
    selectionAlgorithmName = SelectionAlgorithmName.Bandit,
    intraIdeaEnabled = true,
    intraIdeaMinCount = 1,
    intraIdeaProposalsRatio = 0.0,
    interIdeaCompetitionEnabled = false,
    interIdeaCompetitionTargetCount = 50,
    interIdeaCompetitionControversialRatio = 0.0,
    interIdeaCompetitionControversialCount = 0
  )
}
