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

package org.make.core.proposal.indexed

import java.time.ZonedDateTime

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.core.CirceFormatters
import org.make.core.idea.IdeaId
import org.make.core.operation.{OperationId, OperationKind}
import org.make.core.proposal._
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.tag.TagId
import org.make.core.user.UserId

import scala.annotation.meta.field

object ProposalElasticsearchFieldNames {
  val id: String = "id"

  val authorAge: String = "author.age"
  val authorFirstName: String = "author.firstName"
  val authorPostalCode: String = "author.postalCode"
  val content: String = "content"
  val contentBg: String = "content.bg"
  val contentBgStemmed: String = "content.stemmed-bg"
  val contentCs: String = "content.cs"
  val contentCsStemmed: String = "content.stemmed-cs"
  val contentDa: String = "content.da"
  val contentDaStemmed: String = "content.stemmed-da"
  val contentDe: String = "content.de"
  val contentDeStemmed: String = "content.stemmed-de"
  val contentEl: String = "content.el"
  val contentElStemmed: String = "content.stemmed-el"
  val contentEn: String = "content.en"
  val contentEnStemmed: String = "content.stemmed-en"
  val contentEs: String = "content.es"
  val contentEsStemmed: String = "content.stemmed-es"
  val contentEt: String = "content.et"
  val contentFi: String = "content.fi"
  val contentFiStemmed: String = "content.stemmed-fi"
  val contentFr: String = "content.fr"
  val contentFrStemmed: String = "content.stemmed-fr"
  val contentHr: String = "content.hr"
  val contentHu: String = "content.hu"
  val contentHuStemmed: String = "content.stemmed-hu"
  val contentIt: String = "content.it"
  val contentItStemmed: String = "content.stemmed-it"
  val contentLt: String = "content.lt"
  val contentLtStemmed: String = "content.stemmed-lt"
  val contentLv: String = "content.lv"
  val contentLvStemmed: String = "content.stemmed-lv"
  val contentMt: String = "content.mt"
  val contentNl: String = "content.nl"
  val contentNlStemmed: String = "content.stemmed-nl"
  val contentPl: String = "content.pl"
  val contentPlStemmed: String = "content.stemmed-pl"
  val contentPt: String = "content.pt"
  val contentPtStemmed: String = "content.stemmed-pt"
  val contentRo: String = "content.ro"
  val contentRoStemmed: String = "content.stemmed-ro"
  val contentSk: String = "content.sk"
  val contentSl: String = "content.sl"
  val contentSv: String = "content.sv"
  val contentSvStemmed: String = "content.stemmed-sv"
  val contentGeneral: String = "content.general"
  val contextLocation: String = "context.location"
  val contextOperation: String = "context.operation"
  val contextQuestion: String = "context.question"
  val contextSource: String = "context.source"
  val controversy: String = "scores.controversy"
  val country: String = "country"
  val createdAt: String = "createdAt"
  val ideaId: String = "ideaId"
  val initialProposal: String = "initialProposal"
  val labels: String = "labels"
  val language: String = "language"
  val operationId: String = "operationId"
  val operationKind: String = "operationKind"
  val organisationId: String = "organisations.organisationId"
  val organisationName: String = "organisations.organisationName"
  val organisations: String = "organisations"
  val questionId: String = "question.questionId"
  val questionIsOpen: String = "question.isOpen"
  val refusalReason: String = "refusalReason"
  val scores: String = "scores"
  val scoreUpperBound: String = "scores.scoreUpperBound"
  val segment: String = "segment"
  val sequencePool: String = "sequencePool"
  val slug: String = "slug"
  val status: String = "status"
  val tagId: String = "tags.tagId"
  val tags: String = "tags"
  val themeId: String = "themeId"
  val toEnrich: String = "toEnrich"
  val topScore: String = "scores.topScore"
  val trending: String = "trending"
  val updatedAt: String = "updatedAt"
  val userId: String = "userId"
  val votesCount: String = "votesCount"
}

case class IndexedProposal(
  @(ApiModelProperty @field)(dataType = "string", example = "927074a0-a51f-4183-8e7a-bebc705c081b")
  id: ProposalId,
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  userId: UserId,
  content: String,
  slug: String,
  @(ApiModelProperty @field)(dataType = "string", example = "Accepted")
  status: ProposalStatus,
  @(ApiModelProperty @field)(example = "2019-01-23T16:32:00.000Z")
  createdAt: ZonedDateTime,
  @(ApiModelProperty @field)(example = "2019-01-23T16:32:00.000Z")
  updatedAt: Option[ZonedDateTime],
  votes: Seq[IndexedVote],
  votesCount: Int,
  votesVerifiedCount: Int,
  votesSequenceCount: Int,
  votesSegmentCount: Int,
  toEnrich: Boolean,
  scores: IndexedScores,
  context: Option[IndexedContext],
  trending: Option[String],
  labels: Seq[String],
  author: IndexedAuthor,
  organisations: Seq[IndexedOrganisationInfo],
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language,
  @(ApiModelProperty @field)(dataType = "string", example = "9aff4846-3cb8-4737-aea0-2c4a608f30fd")
  themeId: Option[ThemeId],
  question: Option[IndexedProposalQuestion],
  tags: Seq[IndexedTag],
  @(ApiModelProperty @field)(dataType = "string", example = "2a774774-33ca-41a3-a0fa-65931397fbfc")
  ideaId: Option[IdeaId],
  @(ApiModelProperty @field)(dataType = "string", example = "3a9cd696-7e0b-4758-952c-04ae6798039a")
  operationId: Option[OperationId],
  @(ApiModelProperty @field)(dataType = "string", example = "tested")
  sequencePool: SequencePool,
  initialProposal: Boolean,
  refusalReason: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "GREAT_CAUSE")
  operationKind: Option[OperationKind],
  segment: Option[String]
)

object IndexedProposal extends CirceFormatters {
  implicit val encoder: Encoder[IndexedProposal] = deriveEncoder[IndexedProposal]
  implicit val decoder: Decoder[IndexedProposal] = deriveDecoder[IndexedProposal]
}

final case class IndexedProposalQuestion(
  @(ApiModelProperty @field)(dataType = "string", example = "3a9cd696-7e0b-4758-952c-04ae6798039a")
  questionId: QuestionId,
  slug: String,
  title: String,
  question: String,
  startDate: Option[ZonedDateTime],
  endDate: Option[ZonedDateTime],
  isOpen: Boolean
)

object IndexedProposalQuestion extends CirceFormatters {
  implicit val encoder: Encoder[IndexedProposalQuestion] = deriveEncoder[IndexedProposalQuestion]
  implicit val decoder: Decoder[IndexedProposalQuestion] = deriveDecoder[IndexedProposalQuestion]
}

@ApiModel
final case class IndexedContext(
  @(ApiModelProperty @field)(dataType = "string", example = "3a9cd696-7e0b-4758-952c-04ae6798039a")
  operation: Option[OperationId],
  source: Option[String],
  location: Option[String],
  question: Option[String],
  getParameters: Seq[IndexedGetParameters]
)

object IndexedContext {
  implicit val encoder: Encoder[IndexedContext] = deriveEncoder[IndexedContext]
  implicit val decoder: Decoder[IndexedContext] = deriveDecoder[IndexedContext]
}

final case class IndexedGetParameters(key: String, value: String)

object IndexedGetParameters {
  implicit val encoder: Encoder[IndexedGetParameters] = deriveEncoder[IndexedGetParameters]
  implicit val decoder: Decoder[IndexedGetParameters] = deriveDecoder[IndexedGetParameters]
}

final case class IndexedAuthor(firstName: Option[String],
                               organisationName: Option[String],
                               organisationSlug: Option[String],
                               postalCode: Option[String],
                               @(ApiModelProperty @field)(example = "21", dataType = "int")
                               age: Option[Int],
                               avatarUrl: Option[String],
                               anonymousParticipation: Boolean)

object IndexedAuthor {
  implicit val encoder: Encoder[IndexedAuthor] = deriveEncoder[IndexedAuthor]
  implicit val decoder: Decoder[IndexedAuthor] = deriveDecoder[IndexedAuthor]
}

@ApiModel
final case class IndexedOrganisationInfo(
  @(ApiModelProperty)(dataType = "string", example = "b0ae05b3-fa4e-4555-ae71-34b1bea5b21a")
  organisationId: UserId,
  organisationName: Option[String],
  organisationSlug: Option[String]
)

object IndexedOrganisationInfo {
  implicit val encoder: Encoder[IndexedOrganisationInfo] = deriveEncoder[IndexedOrganisationInfo]
  implicit val decoder: Decoder[IndexedOrganisationInfo] = deriveDecoder[IndexedOrganisationInfo]
}

final case class IndexedVote(@(ApiModelProperty @field)(dataType = "string", example = "agree")
                             override val key: VoteKey,
                             override val count: Int = 0,
                             override val countVerified: Int = 0,
                             override val countSequence: Int = 0,
                             override val countSegment: Int = 0,
                             override val qualifications: Seq[IndexedQualification])
    extends BaseVote

object IndexedVote {
  implicit val encoder: Encoder[IndexedVote] = deriveEncoder[IndexedVote]
  implicit val decoder: Decoder[IndexedVote] = deriveDecoder[IndexedVote]

  def apply(vote: Vote): IndexedVote =
    IndexedVote(
      key = vote.key,
      count = vote.count,
      countVerified = vote.countVerified,
      countSequence = vote.countSequence,
      countSegment = vote.countSegment,
      qualifications = vote.qualifications.map(IndexedQualification.apply)
    )
}

final case class IndexedQualification(@(ApiModelProperty @field)(dataType = "string", example = "LikeIt")
                                      override val key: QualificationKey,
                                      override val count: Int = 0,
                                      override val countVerified: Int = 0,
                                      override val countSequence: Int = 0,
                                      override val countSegment: Int = 0)
    extends BaseQualification

object IndexedQualification {
  implicit val encoder: Encoder[IndexedQualification] = deriveEncoder[IndexedQualification]
  implicit val decoder: Decoder[IndexedQualification] = deriveDecoder[IndexedQualification]

  def apply(qualification: Qualification): IndexedQualification =
    IndexedQualification(
      key = qualification.key,
      count = qualification.count,
      countVerified = qualification.countVerified,
      countSequence = qualification.countSequence,
      countSegment = qualification.countSegment
    )
}

final case class IndexedScores(boost: Double = 0,
                               engagement: Double,
                               agreement: Double,
                               adhesion: Double,
                               realistic: Double,
                               platitude: Double,
                               topScore: Double,
                               controversy: Double,
                               rejection: Double,
                               scoreUpperBound: Double)

object IndexedScores {
  implicit val encoder: Encoder[IndexedScores] = deriveEncoder[IndexedScores]
  implicit val decoder: Decoder[IndexedScores] = deriveDecoder[IndexedScores]

  def empty: IndexedScores = IndexedScores(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
}

final case class ProposalsSearchResult(total: Long, results: Seq[IndexedProposal])

object ProposalsSearchResult {
  implicit val encoder: Encoder[ProposalsSearchResult] = deriveEncoder[ProposalsSearchResult]
  implicit val decoder: Decoder[ProposalsSearchResult] = deriveDecoder[ProposalsSearchResult]

  def empty: ProposalsSearchResult = ProposalsSearchResult(0, Seq.empty)
}

@ApiModel
final case class IndexedTag(
  @(ApiModelProperty @field)(dataType = "string", example = "78187c0f-7e9b-4229-8638-2a1ec37416d3")
  tagId: TagId,
  label: String,
  display: Boolean
)

object IndexedTag {
  implicit val encoder: Encoder[IndexedTag] = deriveEncoder[IndexedTag]
  implicit val decoder: Decoder[IndexedTag] = deriveDecoder[IndexedTag]
}

sealed trait SequencePool {
  val shortName: String
}

object SequencePool {
  case object New extends SequencePool { override val shortName: String = "new" }
  case object Tested extends SequencePool { override val shortName: String = "tested" }
  case object Excluded extends SequencePool { override val shortName: String = "excluded" }

  val sequencePools: Map[String, SequencePool] =
    Map(New.shortName -> New, Tested.shortName -> Tested, Excluded.shortName -> Excluded)

  implicit lazy val sequencePoolEncoder: Encoder[SequencePool] =
    (sequencePool: SequencePool) => Json.fromString(sequencePool.shortName)
  implicit lazy val sequencePoolDecoder: Decoder[SequencePool] =
    Decoder.decodeString.emap(
      sequencePool =>
        sequencePools.get(sequencePool).map(Right.apply).getOrElse(Left(s"$sequencePool is not a SequencePool"))
    )

}
