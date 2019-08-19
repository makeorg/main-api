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
import org.make.core.{CirceFormatters, SprayJsonFormatters}
import org.make.core.idea.IdeaId
import org.make.core.operation.{OperationId, OperationKind}
import org.make.core.proposal._
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.tag.TagId
import org.make.core.user.UserId
import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, JsonFormat, RootJsonFormat}
import spray.json._
import spray.json.DefaultJsonProtocol._

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
  val refusalReason: String = "refusalReason"
  val scores: String = "scores"
  val scoreUpperBound: String = "scores.scoreUpperBound"
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
  toEnrich: Boolean,
  scores: IndexedScores,
  context: Option[Context],
  trending: Option[String],
  labels: Seq[String],
  author: Author,
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
  operationKind: Option[OperationKind]
)

object IndexedProposal extends CirceFormatters with SprayJsonFormatters {
  implicit val encoder: Encoder[IndexedProposal] = deriveEncoder[IndexedProposal]
  implicit val decoder: Decoder[IndexedProposal] = deriveDecoder[IndexedProposal]

  implicit val format: RootJsonFormat[IndexedProposal] = new RootJsonFormat[IndexedProposal] {
    override def read(json: JsValue): IndexedProposal = {
      val fields = json.asJsObject.fields
      IndexedProposal(
        id = fields("id").convertTo[ProposalId],
        userId = fields("userId").convertTo[UserId],
        content = fields("content").convertTo[String],
        slug = fields("slug").convertTo[String],
        status = fields("status").convertTo[ProposalStatus],
        createdAt = fields("createdAt").convertTo[ZonedDateTime],
        updatedAt = fields("updatedAt").convertTo[Option[ZonedDateTime]],
        votes = fields("votes").convertTo[Seq[IndexedVote]],
        votesCount = fields("votesCount").convertTo[Int],
        votesVerifiedCount = fields("votesVerifiedCount").convertTo[Int],
        toEnrich = fields("toEnrich").convertTo[Boolean],
        scores = fields("scores").convertTo[IndexedScores],
        context = fields("context").convertTo[Option[Context]],
        trending = fields("trending").convertTo[Option[String]],
        labels = fields("labels").convertTo[Seq[String]],
        author = fields("author").convertTo[Author],
        organisations = fields("organisations").convertTo[Seq[IndexedOrganisationInfo]],
        country = fields("country").convertTo[Country],
        language = fields("language").convertTo[Language],
        themeId = fields("themeId").convertTo[Option[ThemeId]],
        question = fields("question").convertTo[Option[IndexedProposalQuestion]],
        tags = fields("tags").convertTo[Seq[IndexedTag]],
        ideaId = fields("ideaId").convertTo[Option[IdeaId]],
        operationId = fields("operationId").convertTo[Option[OperationId]],
        sequencePool = fields("sequencePool").convertTo[SequencePool],
        initialProposal = fields("initialProposal").convertTo[Boolean],
        refusalReason = fields("refusalReason").convertTo[Option[String]],
        operationKind = fields("operationKind").convertTo[Option[OperationKind]]
      )
    }

    override def write(obj: IndexedProposal) = JsObject(
      "id" -> obj.id.toJson,
      "userId" -> obj.userId.toJson,
      "content" -> JsString(obj.content),
      "slug" -> JsString(obj.slug),
      "status" -> obj.status.toJson,
      "createdAt" -> obj.createdAt.toJson,
      "updatedAt" -> obj.updatedAt.toJson,
      "votes" -> obj.votes.toJson,
      "votesCount" -> JsNumber(obj.votesCount),
      "votesVerifiedCount" -> JsNumber(obj.votesVerifiedCount),
      "toEnrich" -> JsBoolean(obj.toEnrich),
      "scores" -> obj.scores.toJson,
      "context" -> obj.context.toJson,
      "trending" -> obj.trending.toJson,
      "labels" -> obj.labels.toJson,
      "author" -> obj.author.toJson,
      "organisations" -> obj.organisations.toJson,
      "country" -> obj.country.toJson,
      "language" -> obj.language.toJson,
      "themeId" -> obj.themeId.toJson,
      "question" -> obj.question.toJson,
      "tags" -> obj.tags.toJson,
      "ideaId" -> obj.ideaId.toJson,
      "operationId" -> obj.operationId.toJson,
      "sequencePool" -> obj.sequencePool.toJson,
      "initialProposal" -> JsBoolean(obj.initialProposal),
      "refusalReason" -> obj.refusalReason.toJson,
      "operationKind" -> obj.operationKind.toJson,
    )
  }

}

final case class IndexedProposalQuestion(
  @(ApiModelProperty @field)(dataType = "string", example = "3a9cd696-7e0b-4758-952c-04ae6798039a")
  questionId: QuestionId,
  slug: String,
  title: String,
  question: String,
  startDate: Option[ZonedDateTime],
  endDate: Option[ZonedDateTime]
)

object IndexedProposalQuestion extends CirceFormatters with SprayJsonFormatters {
  implicit val encoder: Encoder[IndexedProposalQuestion] = deriveEncoder[IndexedProposalQuestion]
  implicit val decoder: Decoder[IndexedProposalQuestion] = deriveDecoder[IndexedProposalQuestion]

  implicit val format: RootJsonFormat[IndexedProposalQuestion] =
    DefaultJsonProtocol.jsonFormat6(IndexedProposalQuestion.apply)
}

@ApiModel
final case class Context(
  @(ApiModelProperty @field)(dataType = "string", example = "3a9cd696-7e0b-4758-952c-04ae6798039a")
  operation: Option[OperationId],
  source: Option[String],
  location: Option[String],
  question: Option[String],
  getParameters: Seq[IndexedGetParameters]
)

object Context {
  implicit val encoder: Encoder[Context] = deriveEncoder[Context]
  implicit val decoder: Decoder[Context] = deriveDecoder[Context]

  implicit val format: RootJsonFormat[Context] =
    DefaultJsonProtocol.jsonFormat5(Context.apply)
}

final case class IndexedGetParameters(key: String, value: String)

object IndexedGetParameters {
  implicit val encoder: Encoder[IndexedGetParameters] = deriveEncoder[IndexedGetParameters]
  implicit val decoder: Decoder[IndexedGetParameters] = deriveDecoder[IndexedGetParameters]

  implicit val format: RootJsonFormat[IndexedGetParameters] =
    DefaultJsonProtocol.jsonFormat2(IndexedGetParameters.apply)
}

final case class Author(firstName: Option[String],
                        organisationName: Option[String],
                        organisationSlug: Option[String],
                        postalCode: Option[String],
                        @(ApiModelProperty @field)(example = "21", dataType = "int")
                        age: Option[Int],
                        avatarUrl: Option[String])

object Author {
  implicit val encoder: Encoder[Author] = deriveEncoder[Author]
  implicit val decoder: Decoder[Author] = deriveDecoder[Author]

  implicit val format: RootJsonFormat[Author] =
    DefaultJsonProtocol.jsonFormat6(Author.apply)
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

  implicit val format: RootJsonFormat[IndexedOrganisationInfo] =
    DefaultJsonProtocol.jsonFormat3(IndexedOrganisationInfo.apply)
}

final case class IndexedVote(@(ApiModelProperty @field)(dataType = "string", example = "agree")
                             override val key: VoteKey,
                             override val count: Int = 0,
                             override val countVerified: Int = 0,
                             override val qualifications: Seq[IndexedQualification])
    extends BaseVote

object IndexedVote {
  implicit val encoder: Encoder[IndexedVote] = deriveEncoder[IndexedVote]
  implicit val decoder: Decoder[IndexedVote] = deriveDecoder[IndexedVote]

  implicit val format: RootJsonFormat[IndexedVote] =
    DefaultJsonProtocol.jsonFormat4(IndexedVote.apply)

  def apply(vote: Vote): IndexedVote =
    IndexedVote(
      key = vote.key,
      count = vote.count,
      countVerified = vote.countVerified,
      qualifications = vote.qualifications.map(IndexedQualification.apply)
    )
}

final case class IndexedQualification(@(ApiModelProperty @field)(dataType = "string", example = "LikeIt")
                                      override val key: QualificationKey,
                                      override val count: Int = 0,
                                      override val countVerified: Int = 0)
    extends BaseQualification

object IndexedQualification {
  implicit val encoder: Encoder[IndexedQualification] = deriveEncoder[IndexedQualification]
  implicit val decoder: Decoder[IndexedQualification] = deriveDecoder[IndexedQualification]

  implicit val format: RootJsonFormat[IndexedQualification] =
    DefaultJsonProtocol.jsonFormat3(IndexedQualification.apply)

  def apply(qualification: Qualification): IndexedQualification =
    IndexedQualification(
      key = qualification.key,
      count = qualification.count,
      countVerified = qualification.countVerified
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

  implicit val format: RootJsonFormat[IndexedScores] =
    DefaultJsonProtocol.jsonFormat10(IndexedScores.apply)

  def empty: IndexedScores = IndexedScores(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
}

final case class ProposalsSearchResult(total: Long, results: Seq[IndexedProposal])

object ProposalsSearchResult {
  implicit val encoder: Encoder[ProposalsSearchResult] = deriveEncoder[ProposalsSearchResult]
  implicit val decoder: Decoder[ProposalsSearchResult] = deriveDecoder[ProposalsSearchResult]

  implicit val format: RootJsonFormat[ProposalsSearchResult] =
    DefaultJsonProtocol.jsonFormat2(ProposalsSearchResult.apply)

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

  implicit val format: RootJsonFormat[IndexedTag] =
    DefaultJsonProtocol.jsonFormat3(IndexedTag.apply)
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

  implicit val sequencePoolFormatter: JsonFormat[SequencePool] = new JsonFormat[SequencePool] {
    override def read(json: JsValue): SequencePool = json match {
      case JsString(sequencePool) =>
        sequencePools
          .getOrElse(sequencePool, throw new IllegalArgumentException(s"$sequencePool is not a SequencePool"))
      case other => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: SequencePool): JsValue = {
      JsString(obj.shortName)
    }
  }

}
