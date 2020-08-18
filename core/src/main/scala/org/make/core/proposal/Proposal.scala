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

package org.make.core.proposal

import java.time.ZonedDateTime

import com.sksamuel.avro4s.AvroSortPriority
import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json}
import io.swagger.annotations.ApiModelProperty
import org.make.core.SprayJsonFormatters._
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.question.QuestionId
import org.make.core.reference.{LabelId, ThemeId}
import org.make.core.tag.{TagId, TagType, TagTypeId}
import org.make.core.user.UserId
import org.make.core.{MakeSerializable, RequestContext, StringValue, Timestamped}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

import scala.annotation.meta.field

final case class Proposal(
  proposalId: ProposalId,
  slug: String,
  content: String,
  author: UserId,
  labels: Seq[LabelId],
  theme: Option[ThemeId] = None,
  status: ProposalStatus = ProposalStatus.Pending,
  refusalReason: Option[String] = None,
  tags: Seq[TagId] = Seq.empty,
  votes: Seq[Vote],
  // @deprecated "Use the organisationIds field instead"
  organisations: Seq[OrganisationInfo] = Seq.empty,
  organisationIds: Seq[UserId] = Seq.empty,
  questionId: Option[QuestionId] = None,
  creationContext: RequestContext,
  idea: Option[IdeaId] = None,
  operation: Option[OperationId] = None,
  override val createdAt: Option[ZonedDateTime],
  override val updatedAt: Option[ZonedDateTime],
  events: List[ProposalAction],
  initialProposal: Boolean = false
) extends Timestamped
    with MakeSerializable

object Proposal {
  implicit val proposalFormatter: RootJsonFormat[Proposal] =
    DefaultJsonProtocol.jsonFormat20(Proposal.apply)

  def needsEnrichment(status: ProposalStatus, tagTypes: Seq[TagType], proposalTagTypes: Seq[TagTypeId]): Boolean = {
    val proposalTypesSet = proposalTagTypes.toSet
    status == Accepted && !tagTypes.filter(_.requiredForEnrichment).map(_.tagTypeId).forall(proposalTypesSet.contains)
  }

}

final case class ProposalId(value: String) extends StringValue

object ProposalId {
  implicit lazy val proposalIdEncoder: Encoder[ProposalId] =
    (a: ProposalId) => Json.fromString(a.value)
  implicit lazy val proposalIdDecoder: Decoder[ProposalId] =
    Decoder.decodeString.map(ProposalId(_))

  implicit val proposalIdFormatter: JsonFormat[ProposalId] = new JsonFormat[ProposalId] {
    override def read(json: JsValue): ProposalId = json match {
      case JsString(s) => ProposalId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: ProposalId): JsValue = {
      JsString(obj.value)
    }
  }
}

final case class OrganisationInfo(organisationId: UserId, organisationName: Option[String])

object OrganisationInfo {
  implicit val encoder: Encoder[OrganisationInfo] = deriveEncoder[OrganisationInfo]
  implicit val decoder: Decoder[OrganisationInfo] = deriveDecoder[OrganisationInfo]

  implicit lazy val organisationInfoFormatter: RootJsonFormat[OrganisationInfo] =
    DefaultJsonProtocol.jsonFormat2(OrganisationInfo.apply)
}

final case class AuthorInfo(userId: UserId, firstName: Option[String], postalCode: Option[String], age: Option[Int])

final case class ProposalAction(date: ZonedDateTime, user: UserId, actionType: String, arguments: Map[String, String])

object ProposalAction {
  implicit val proposalActionFormatter: RootJsonFormat[ProposalAction] =
    DefaultJsonProtocol.jsonFormat4(ProposalAction.apply)
}

sealed abstract class ProposalActionType(val value: String) extends StringEnumEntry

object ProposalActionType extends StringEnum[ProposalActionType] {

  case object ProposalProposeAction extends ProposalActionType("propose")
  case object ProposalUpdateAction extends ProposalActionType("update")
  case object ProposalUpdateVoteVerifiedAction extends ProposalActionType("update-votes-verified")
  case object ProposalAcceptAction extends ProposalActionType("accept")
  case object ProposalVoteAction extends ProposalActionType("vote")
  case object ProposalUnvoteAction extends ProposalActionType("unvote")
  case object ProposalQualifyAction extends ProposalActionType("qualify")
  case object ProposalUnqualifyAction extends ProposalActionType("unqualify")

  override def values: IndexedSeq[ProposalActionType] = findValues

}

sealed abstract class QualificationKey(val value: String) extends StringEnumEntry

object QualificationKey extends StringEnum[QualificationKey] with StringCirceEnum[QualificationKey] {

  case object LikeIt extends QualificationKey("likeIt")
  case object Doable extends QualificationKey("doable")
  case object PlatitudeAgree extends QualificationKey("platitudeAgree")
  case object NoWay extends QualificationKey("noWay")
  case object Impossible extends QualificationKey("impossible")
  case object PlatitudeDisagree extends QualificationKey("platitudeDisagree")
  case object DoNotUnderstand extends QualificationKey("doNotUnderstand")
  case object NoOpinion extends QualificationKey("noOpinion")
  case object DoNotCare extends QualificationKey("doNotCare")

  override def values: IndexedSeq[QualificationKey] = findValues

}

trait BaseQualification {
  def key: QualificationKey
  def count: Int
  def countVerified: Int
  def countSequence: Int
  def countSegment: Int
}

final case class Qualification(
  @(ApiModelProperty @field)(dataType = "string", example = "likeIt")
  override val key: QualificationKey,
  override val count: Int,
  override val countVerified: Int,
  override val countSequence: Int,
  override val countSegment: Int
) extends BaseQualification

object Qualification {
  implicit val encoder: Encoder[Qualification] = deriveEncoder[Qualification]
  implicit val decoder: Decoder[Qualification] = deriveDecoder[Qualification]

  implicit val qualificationFormatter: RootJsonFormat[Qualification] =
    DefaultJsonProtocol.jsonFormat5(Qualification.apply)

}

trait BaseVote {
  def key: VoteKey
  def count: Int
  def countVerified: Int
  def countSequence: Int
  def countSegment: Int
  def qualifications: Seq[BaseQualification]
}

final case class Vote(
  @(ApiModelProperty @field)(dataType = "string", example = "agree")
  override val key: VoteKey,
  override val count: Int,
  override val countVerified: Int,
  override val countSequence: Int,
  override val countSegment: Int,
  override val qualifications: Seq[Qualification]
) extends BaseVote

object Vote {
  implicit val encoder: Encoder[Vote] = deriveEncoder[Vote]
  implicit val decoder: Decoder[Vote] = deriveDecoder[Vote]

  implicit val voteFormatter: RootJsonFormat[Vote] =
    DefaultJsonProtocol.jsonFormat6(Vote.apply)

  def empty(key: VoteKey): Vote = Vote(key, 0, 0, 0, 0, Seq.empty)
}

sealed abstract class VoteKey(val value: String) extends StringEnumEntry

object VoteKey extends StringEnum[VoteKey] with StringCirceEnum[VoteKey] {

  case object Agree extends VoteKey("agree")
  case object Disagree extends VoteKey("disagree")
  case object Neutral extends VoteKey("neutral")

  override def values: IndexedSeq[VoteKey] = findValues

}

sealed abstract class ProposalStatus(val value: String) extends StringEnumEntry

object ProposalStatus extends StringEnum[ProposalStatus] with StringCirceEnum[ProposalStatus] {

  @AvroSortPriority(5)
  case object Pending extends ProposalStatus("Pending")

  @AvroSortPriority(1)
  case object Accepted extends ProposalStatus("Accepted")

  @AvroSortPriority(3)
  case object Refused extends ProposalStatus("Refused")

  @AvroSortPriority(4)
  case object Postponed extends ProposalStatus("Postponed")

  @AvroSortPriority(2)
  case object Archived extends ProposalStatus("Archived")

  override def values: IndexedSeq[ProposalStatus] = findValues

}
