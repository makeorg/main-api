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

import cats.data.NonEmptyList
import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe.generic.semiauto._
import io.circe.{Codec, Decoder, Encoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import com.github.plokhotnyuk.jsoniter_scala.macros._
import com.github.plokhotnyuk.jsoniter_scala.core._
import org.make.api.proposal.ProposalScorer
import org.make.core.elasticsearch.ElasticsearchFieldName
import org.make.core.idea.IdeaId
import org.make.core.operation.{OperationId, OperationKind}
import org.make.core.proposal._
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.{UserId, UserType}
import org.make.core.{CirceFormatters, RequestContext}
import org.make.core.jsoniter.{JsoniterCodecs, JsoniterEnum}

import java.time.ZonedDateTime
import scala.annotation.meta.field

sealed abstract class ProposalElasticsearchFieldName(val value: String, val sortable: Boolean = false)
    extends ElasticsearchFieldName

object ProposalElasticsearchFieldName extends StringEnum[ProposalElasticsearchFieldName] {

  sealed abstract class Simple(val field: String, override val sortable: Boolean = false)
      extends ProposalElasticsearchFieldName(field, sortable) {
    override def parameter: String = field
  }

  sealed abstract class Alias(
    val parameter: String,
    aliased: ProposalElasticsearchFieldName,
    override val sortable: Boolean = false
  ) extends ProposalElasticsearchFieldName(parameter, sortable) {
    override def field: String = aliased.field
  }

  case object id extends Simple("id")

  case object agreementRate extends Simple("agreementRate", sortable = true)
  case object authorAge extends Simple("author.age")
  case object authorFirstName extends Simple("author.firstName")
  case object authorPostalCode extends Simple("author.postalCode")
  case object authorUserType extends Simple("author.userType")
  case object authorAvatarUrl extends Simple("author.avatarUrl")
  case object content extends Simple("content")
  case object contentBg extends Simple("content.bg")
  case object contentBgStemmed extends Simple("content.stemmed-bg")
  case object contentCs extends Simple("content.cs")
  case object contentCsStemmed extends Simple("content.stemmed-cs")
  case object contentDa extends Simple("content.da")
  case object contentDaStemmed extends Simple("content.stemmed-da")
  case object contentDe extends Simple("content.de")
  case object contentDeStemmed extends Simple("content.stemmed-de")
  case object contentEl extends Simple("content.el")
  case object contentElStemmed extends Simple("content.stemmed-el")
  case object contentEn extends Simple("content.en")
  case object contentEnStemmed extends Simple("content.stemmed-en")
  case object contentEs extends Simple("content.es")
  case object contentEsStemmed extends Simple("content.stemmed-es")
  case object contentEt extends Simple("content.et")
  case object contentFi extends Simple("content.fi")
  case object contentFiStemmed extends Simple("content.stemmed-fi")
  case object contentFr extends Simple("content.fr")
  case object contentFrStemmed extends Simple("content.stemmed-fr")
  case object contentHr extends Simple("content.hr")
  case object contentHu extends Simple("content.hu")
  case object contentHuStemmed extends Simple("content.stemmed-hu")
  case object contentIt extends Simple("content.it")
  case object contentItStemmed extends Simple("content.stemmed-it")
  case object contentLt extends Simple("content.lt")
  case object contentLtStemmed extends Simple("content.stemmed-lt")
  case object contentLv extends Simple("content.lv")
  case object contentLvStemmed extends Simple("content.stemmed-lv")
  case object contentMt extends Simple("content.mt")
  case object contentNl extends Simple("content.nl")
  case object contentNlStemmed extends Simple("content.stemmed-nl")
  case object contentPl extends Simple("content.pl")
  case object contentPlStemmed extends Simple("content.stemmed-pl")
  case object contentPt extends Simple("content.pt")
  case object contentPtStemmed extends Simple("content.stemmed-pt")
  case object contentRo extends Simple("content.ro")
  case object contentRoStemmed extends Simple("content.stemmed-ro")
  case object contentSk extends Simple("content.sk")
  case object contentSl extends Simple("content.sl")
  case object contentSv extends Simple("content.sv")
  case object contentSvStemmed extends Simple("content.stemmed-sv")
  case object contentGeneral extends Simple("content.general")
  case object contentKeyword extends Simple("content.keyword", sortable = true)
  case object contextCountry extends Simple("context.country")
  case object contextLanguage extends Simple("context.language")
  case object contextLocation extends Simple("context.location")
  case object contextOperation extends Simple("context.operation")
  case object contextQuestion extends Simple("context.question")
  case object contextSource extends Simple("context.source")
  case object controversyLowerBound extends Simple("scores.controversy.lowerBound")
  case object controversyLowerBoundLegacy extends Alias("scores.controversyLowerBound", controversyLowerBound)
  case object country extends Alias("country", contextCountry, sortable = true)
  case object createdAt extends Simple("createdAt", sortable = true)
  case object ideaId extends Simple("ideaId")
  case object initialProposal extends Simple("initialProposal")
  case object labels extends Simple("labels", sortable = true)
  case object language extends Alias("language", contextLanguage, sortable = true)
  case object operationId extends Simple("operationId")
  case object operationKind extends Simple("operationKind")
  case object organisationId extends Simple("organisations.organisationId")
  case object organisationName extends Simple("organisations.organisationName")
  case object organisations extends Simple("organisations")
  case object questionCountries extends Simple("question.countries", sortable = true)
  case object questionId extends Simple("question.questionId")
  case object questionIsOpen extends Simple("question.isOpen")
  case object questionLanguage extends Simple("question.language", sortable = true)
  case object refusalReason extends Simple("refusalReason")
  case object scores extends Simple("scores")
  case object scoreAgreement extends Simple("scores.agreement.lowerBound")
  case object scoreAgreementLegacy extends Alias("scores.agreement", scoreAgreement)
  case object scoreRealistic extends Simple("scores.realistic.lowerBound")
  case object scoreRealisticLegacy extends Alias("scores.realistic", scoreRealistic)
  case object scoreUpperBound extends Simple("scores.topScore.upperBound")
  case object scoreUpperBoundLegacy extends Alias("scores.scoreUpperBound", scoreUpperBound)
  case object scoreLowerBound extends Simple("scores.topScore.lowerBound")
  case object scoreLowerBoundLegacy extends Alias("scores.scoreLowerBound", scoreLowerBound)
  case object segment extends Simple("segment")
  case object sequenceSegmentPool extends Simple("sequenceSegmentPool")
  case object sequencePool extends Simple("sequencePool")
  case object slug extends Simple("slug", sortable = true)
  case object status extends Simple("status")
  case object tagId extends Simple("tags.tagId")
  case object tags extends Simple("tags")
  case object selectedStakeTagId extends Simple("selectedStakeTag.tagId")
  case object toEnrich extends Simple("toEnrich")
  case object topScore extends Simple("scores.topScore.score")
  case object topScoreLegacy extends Alias("scores.topScore", topScore)
  case object topScoreAjustedWithVotesLegacy
      extends Alias("scores.topScoreAjustedWithVotes", scoreLowerBound, sortable = true)
  case object trending extends Simple("trending", sortable = true)
  case object updatedAt extends Simple("updatedAt", sortable = true)
  case object userId extends Simple("userId")
  case object votesCount extends Simple("votesCount")
  case object zone extends Simple("scores.zone")
  case object segmentZone extends Simple("segmentScores.zone")
  case object keywordKey extends Simple("keywords.key")

  override def values: IndexedSeq[ProposalElasticsearchFieldName] = findValues
}

final case class IndexedProposal(
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
  segmentScores: IndexedScores,
  agreementRate: Double,
  context: Option[IndexedContext],
  trending: Option[String],
  labels: Seq[String],
  author: IndexedAuthor,
  organisations: Seq[IndexedOrganisationInfo],
  question: Option[IndexedProposalQuestion],
  tags: Seq[IndexedTag],
  selectedStakeTag: Option[IndexedTag],
  @(ApiModelProperty @field)(dataType = "string", example = "2a774774-33ca-41a3-a0fa-65931397fbfc")
  ideaId: Option[IdeaId],
  @(ApiModelProperty @field)(dataType = "string", example = "3a9cd696-7e0b-4758-952c-04ae6798039a")
  operationId: Option[OperationId],
  @(ApiModelProperty @field)(dataType = "string", example = "tested")
  sequencePool: SequencePool,
  @(ApiModelProperty @field)(dataType = "string", example = "tested")
  sequenceSegmentPool: SequencePool,
  initialProposal: Boolean,
  refusalReason: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "GREAT_CAUSE")
  operationKind: Option[OperationKind],
  segment: Option[String],
  keywords: Seq[IndexedProposalKeyword]
)

object IndexedProposal extends CirceFormatters with JsoniterCodecs {

  implicit val codec: JsonValueCodec[IndexedProposal] =
    JsonCodecMaker.makeWithRequiredCollectionFields

  implicit val encoder: Encoder[IndexedProposal] = deriveEncoder[IndexedProposal]
  implicit val decoder: Decoder[IndexedProposal] = deriveDecoder[IndexedProposal]
}

final case class IndexedProposalQuestion(
  @(ApiModelProperty @field)(dataType = "string", example = "3a9cd696-7e0b-4758-952c-04ae6798039a")
  questionId: QuestionId,
  slug: String,
  title: String,
  question: String,
  countries: NonEmptyList[Country],
  language: Language,
  startDate: ZonedDateTime,
  endDate: ZonedDateTime,
  isOpen: Boolean
)

object IndexedProposalQuestion extends CirceFormatters with JsoniterCodecs {

  implicit val codec: JsonValueCodec[IndexedProposalQuestion] =
    JsonCodecMaker.makeWithRequiredCollectionFields

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
  country: Option[Country],
  language: Option[Language],
  getParameters: Seq[IndexedGetParameters]
)

object IndexedContext {

  def apply(context: RequestContext, isBeforeContextSourceFeature: Boolean = false): IndexedContext =
    IndexedContext(
      operation = context.operationId,
      source = context.source.filter(!_.isEmpty) match {
        case None if isBeforeContextSourceFeature => Some("core")
        case other                                => other
      },
      location = context.location,
      question = context.question,
      country = context.country,
      language = context.language,
      getParameters = context.getParameters
        .map(_.toSeq.map {
          case (key, value) => IndexedGetParameters(key, value)
        })
        .getOrElse(Seq.empty)
    )

  implicit val codec: JsonValueCodec[IndexedContext] =
    JsonCodecMaker.makeWithRequiredCollectionFields

  implicit val encoder: Encoder[IndexedContext] = deriveEncoder[IndexedContext]
  implicit val decoder: Decoder[IndexedContext] = deriveDecoder[IndexedContext]
}

final case class IndexedGetParameters(key: String, value: String)

object IndexedGetParameters {
  implicit val codec: JsonValueCodec[IndexedGetParameters] =
    JsonCodecMaker.makeWithRequiredCollectionFields

  implicit val encoder: Encoder[IndexedGetParameters] = deriveEncoder[IndexedGetParameters]
  implicit val decoder: Decoder[IndexedGetParameters] = deriveDecoder[IndexedGetParameters]
}

final case class IndexedAuthor(
  firstName: Option[String],
  displayName: Option[String],
  organisationName: Option[String],
  organisationSlug: Option[String],
  postalCode: Option[String],
  @(ApiModelProperty @field)(example = "21", dataType = "int")
  age: Option[Int],
  avatarUrl: Option[String],
  anonymousParticipation: Boolean,
  userType: UserType,
  profession: Option[String]
)

object IndexedAuthor {
  implicit val codec: JsonValueCodec[IndexedAuthor] =
    JsonCodecMaker.makeWithRequiredCollectionFields

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
  implicit val codec: JsonValueCodec[IndexedOrganisationInfo] =
    JsonCodecMaker.makeWithRequiredCollectionFields

  implicit val encoder: Encoder[IndexedOrganisationInfo] = deriveEncoder[IndexedOrganisationInfo]
  implicit val decoder: Decoder[IndexedOrganisationInfo] = deriveDecoder[IndexedOrganisationInfo]
}

final case class IndexedVote(
  @(ApiModelProperty @field)(dataType = "string", example = "agree")
  override val key: VoteKey,
  override val count: Int,
  override val countVerified: Int,
  override val countSequence: Int,
  override val countSegment: Int,
  override val qualifications: Seq[IndexedQualification]
) extends BaseVote

object IndexedVote {
  implicit val codec: JsonValueCodec[IndexedVote] =
    JsonCodecMaker.makeWithRequiredCollectionFields

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

  def empty(key: VoteKey): IndexedVote = IndexedVote(key, 0, 0, 0, 0, Seq.empty)
}

final case class IndexedQualification(
  @(ApiModelProperty @field)(dataType = "string", example = "LikeIt")
  override val key: QualificationKey,
  override val count: Int,
  override val countVerified: Int,
  override val countSequence: Int,
  override val countSegment: Int
) extends BaseQualification

object IndexedQualification {
  implicit val encoder: Encoder[IndexedQualification] = deriveEncoder[IndexedQualification]
  implicit val decoder: Decoder[IndexedQualification] = deriveDecoder[IndexedQualification]

  implicit val codec: JsonValueCodec[IndexedQualification] =
    JsonCodecMaker.makeWithRequiredCollectionFields

  def apply(qualification: Qualification): IndexedQualification =
    IndexedQualification(
      key = qualification.key,
      count = qualification.count,
      countVerified = qualification.countVerified,
      countSequence = qualification.countSequence,
      countSegment = qualification.countSegment
    )

  def empty(key: QualificationKey): IndexedQualification = IndexedQualification(key, 0, 0, 0, 0)
}

sealed abstract class Zone(val value: String) extends StringEnumEntry

object Zone extends StringEnum[Zone] with StringCirceEnum[Zone] with JsoniterEnum[Zone] {

  case object Consensus extends Zone("consensus")
  case object Rejection extends Zone("rejection")
  case object Limbo extends Zone("limbo")
  case object Controversy extends Zone("controversy")

  override def values: IndexedSeq[Zone] = findValues
}

final case class IndexedScores(
  engagement: IndexedScore,
  agreement: IndexedScore,
  adhesion: IndexedScore,
  realistic: IndexedScore,
  platitude: IndexedScore,
  topScore: IndexedScore,
  controversy: IndexedScore,
  rejection: IndexedScore,
  zone: Zone
)

object IndexedScores {
  implicit val codec: JsonValueCodec[IndexedScores] =
    JsonCodecMaker.makeWithRequiredCollectionFields

  implicit val encoder: Encoder[IndexedScores] = deriveEncoder[IndexedScores]
  implicit val decoder: Decoder[IndexedScores] = deriveDecoder[IndexedScores]

  def empty: IndexedScores =
    IndexedScores(
      IndexedScore.empty,
      IndexedScore.empty,
      IndexedScore.empty,
      IndexedScore.empty,
      IndexedScore.empty,
      IndexedScore.empty,
      IndexedScore.empty,
      IndexedScore.empty,
      Zone.Limbo
    )

  def apply(scorer: ProposalScorer): IndexedScores =
    IndexedScores(
      engagement = IndexedScore(scorer.engagement),
      agreement = IndexedScore(scorer.agree),
      adhesion = IndexedScore(scorer.adhesion),
      realistic = IndexedScore(scorer.realistic),
      platitude = IndexedScore(scorer.platitude),
      topScore = IndexedScore(scorer.topScore),
      controversy = IndexedScore(scorer.controversy),
      rejection = IndexedScore(scorer.rejection),
      zone = scorer.zone
    )

}

final case class IndexedScore(score: Double, lowerBound: Double, upperBound: Double)

object IndexedScore {
  implicit val indexedScoreCodec: JsonValueCodec[IndexedScore] =
    JsonCodecMaker.makeWithRequiredCollectionFields
  implicit val codec: Codec[IndexedScore] = deriveCodec
  val empty: IndexedScore = IndexedScore(0, 0, 0)

  def apply(score: ProposalScorer.Score): IndexedScore =
    IndexedScore(score = score.score, lowerBound = score.lowerBound, upperBound = score.upperBound)
}

final case class ProposalsSearchResult(total: Long, results: Seq[IndexedProposal])

object ProposalsSearchResult {
  implicit val codec: JsonValueCodec[ProposalsSearchResult] =
    JsonCodecMaker.makeWithRequiredCollectionFields

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
  implicit val codec: JsonValueCodec[IndexedTag] =
    JsonCodecMaker.makeWithRequiredCollectionFields

  implicit val encoder: Encoder[IndexedTag] = deriveEncoder[IndexedTag]
  implicit val decoder: Decoder[IndexedTag] = deriveDecoder[IndexedTag]
}

sealed abstract class SequencePool(val value: String) extends StringEnumEntry

object SequencePool
    extends StringEnum[SequencePool]
    with StringCirceEnum[SequencePool]
    with JsoniterEnum[SequencePool] {

  case object New extends SequencePool("new")
  case object Tested extends SequencePool("tested")
  case object Excluded extends SequencePool("excluded")

  override def values: IndexedSeq[SequencePool] = findValues
}

final case class IndexedProposalKeyword(key: ProposalKeywordKey, label: String)

object IndexedProposalKeyword {
  implicit val codec: Codec[IndexedProposalKeyword] = deriveCodec[IndexedProposalKeyword]

  def apply(keyword: ProposalKeyword): IndexedProposalKeyword = {
    IndexedProposalKeyword(keyword.key, keyword.label)
  }
}
