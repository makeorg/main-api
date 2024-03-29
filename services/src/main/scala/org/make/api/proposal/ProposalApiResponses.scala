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

package org.make.api.proposal

import java.time.{LocalDate, ZonedDateTime}
import java.time.temporal.ChronoUnit
import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}
import io.circe.{Codec, Decoder, Encoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.api.question.{SimpleQuestionResponse, SimpleQuestionWordingResponse}
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.question.QuestionId
import org.make.core.reference._
import org.make.core.tag.{Tag, TagId, TagTypeId}
import org.make.core.user.{User, UserId, UserType}
import org.make.core.{CirceFormatters, RequestContext, SlugHelper}

import scala.annotation.meta.field

final case class ModerationProposalAuthorResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "c39c35cd-87dc-430f-841b-609b776ab720")
  userId: UserId,
  firstName: Option[String],
  lastName: Option[String],
  displayName: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "12345")
  postalCode: Option[String],
  @(ApiModelProperty @field)(dataType = "int")
  age: Option[Int],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/avatar.png")
  avatarUrl: Option[String],
  organisationName: Option[String],
  organisationSlug: Option[String],
  reachable: Boolean
)

object ModerationProposalAuthorResponse {
  def apply(user: User): ModerationProposalAuthorResponse = {
    if (user.anonymousParticipation) {
      ModerationProposalAuthorResponse(
        userId = user.userId,
        firstName = None,
        lastName = None,
        displayName = None,
        postalCode = None,
        age = None,
        avatarUrl = None,
        organisationName = None,
        organisationSlug = None,
        reachable = false
      )
    } else {
      ModerationProposalAuthorResponse(
        userId = user.userId,
        firstName = user.firstName,
        lastName = user.lastName,
        displayName = user.displayName,
        postalCode = user.profile.flatMap(_.postalCode),
        age = user.profile
          .flatMap(_.dateOfBirth)
          .map(date => ChronoUnit.YEARS.between(date, LocalDate.now()).toInt),
        avatarUrl = user.profile.flatMap(_.avatarUrl),
        organisationName = user.organisationName,
        organisationSlug = user.organisationName.map(SlugHelper.apply),
        reachable = user.emailVerified
      )
    }
  }

  implicit val encoder: Encoder[ModerationProposalAuthorResponse] = deriveEncoder[ModerationProposalAuthorResponse]
  implicit val decoder: Decoder[ModerationProposalAuthorResponse] = deriveDecoder[ModerationProposalAuthorResponse]
}

final case class ModerationAuthorResponse(
  author: ModerationProposalAuthorResponse,
  proposals: Seq[ModerationProposalResponse],
  total: Int
)

object ModerationAuthorResponse {
  implicit val codec: Codec[ModerationAuthorResponse] = deriveCodec
}

final case class ModerationProposalResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "927074a0-a51f-4183-8e7a-bebc705c081b")
  id: ProposalId,
  @(ApiModelProperty @field)(dataType = "string", example = "927074a0-a51f-4183-8e7a-bebc705c081b")
  proposalId: ProposalId,
  slug: String,
  content: String,
  author: ModerationProposalAuthorResponse,
  @(ApiModelProperty @field)(dataType = "list[string]")
  labels: Seq[LabelId],
  @(ApiModelProperty @field)(dataType = "string", example = "Accepted")
  status: ProposalStatus,
  @(ApiModelProperty @field)(dataType = "string", example = "other")
  refusalReason: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "list[string]")
  tags: Seq[TagId] = Seq.empty,
  votes: Seq[Vote],
  context: RequestContext,
  @(ApiModelProperty @field)(example = "2019-01-23T16:32:00.000Z")
  createdAt: Option[ZonedDateTime],
  @(ApiModelProperty @field)(example = "2019-01-23T16:32:00.000Z")
  updatedAt: Option[ZonedDateTime],
  events: Seq[ProposalActionResponse],
  @(ApiModelProperty @field)(dataType = "string", example = "2a774774-33ca-41a3-a0fa-65931397fbfc")
  idea: Option[IdeaId],
  @(ApiModelProperty @field)(hidden = true)
  ideaProposals: Seq[IndexedProposal],
  @(ApiModelProperty @field)(dataType = "string", example = "2d791a66-3cd5-4a2e-a117-9daa68bd3a33")
  questionId: Option[QuestionId],
  @(ApiModelProperty @field)(dataType = "string", example = "3a9cd696-7e0b-4758-952c-04ae6798039a")
  operationId: Option[OperationId],
  keywords: Seq[ProposalKeyword]
)

object ModerationProposalResponse extends CirceFormatters {
  implicit val encoder: Encoder[ModerationProposalResponse] = deriveEncoder[ModerationProposalResponse]
  implicit val decoder: Decoder[ModerationProposalResponse] = deriveDecoder[ModerationProposalResponse]
}

final case class ProposalActionResponse(
  @(ApiModelProperty @field)(example = "2019-01-23T16:32:00.000Z")
  date: ZonedDateTime,
  user: Option[ProposalActionAuthorResponse],
  actionType: String,
  arguments: Map[String, String]
)

object ProposalActionResponse extends CirceFormatters {
  implicit val encoder: Encoder[ProposalActionResponse] = deriveEncoder[ProposalActionResponse]
  implicit val decoder: Decoder[ProposalActionResponse] = deriveDecoder[ProposalActionResponse]
}

final case class ProposalActionAuthorResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "927074a0-a51f-4183-8e7a-bebc705c081b")
  id: UserId,
  displayName: Option[String]
)

object ProposalActionAuthorResponse {
  implicit val codec: Codec[ProposalActionAuthorResponse] = deriveCodec
}

@ApiModel
final case class ProposalIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "927074a0-a51f-4183-8e7a-bebc705c081b")
  id: ProposalId,
  @(ApiModelProperty @field)(dataType = "string", example = "927074a0-a51f-4183-8e7a-bebc705c081b")
  proposalId: ProposalId
)

object ProposalIdResponse {
  implicit val encoder: Encoder[ProposalIdResponse] = deriveEncoder[ProposalIdResponse]
  implicit val decoder: Decoder[ProposalIdResponse] = deriveDecoder[ProposalIdResponse]

  def apply(id: ProposalId): ProposalIdResponse = ProposalIdResponse(id, id)
}

final case class AuthorResponse(
  firstName: Option[String],
  displayName: Option[String],
  organisationName: Option[String],
  organisationSlug: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "12345")
  postalCode: Option[String],
  @(ApiModelProperty @field)(dataType = "int", example = "21")
  age: Option[Int],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/avatar.png")
  avatarUrl: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "USER", allowableValues = "USER,ORGANISATION,PERSONALITY")
  userType: Option[UserType]
)

object AuthorResponse {
  implicit val encoder: Encoder[AuthorResponse] = deriveEncoder[AuthorResponse]
  implicit val decoder: Decoder[AuthorResponse] = deriveDecoder[AuthorResponse]

  def fromIndexedAuthor(author: IndexedAuthor): AuthorResponse = {
    if (author.anonymousParticipation) {
      AuthorResponse(
        firstName = None,
        displayName = None,
        organisationName = None,
        organisationSlug = None,
        postalCode = None,
        age = None,
        avatarUrl = None,
        userType = None
      )
    } else {
      AuthorResponse(
        firstName = author.firstName,
        displayName = author.displayName,
        organisationName = author.organisationName,
        organisationSlug = author.organisationSlug,
        postalCode = author.postalCode,
        age = author.age,
        avatarUrl = author.avatarUrl,
        userType = Some(author.userType)
      )
    }
  }
}

final case class ProposalContextResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "0bac0a8b-5faa-4872-90aa-6f9f3b07aa59")
  operation: Option[OperationId],
  source: Option[String],
  location: Option[String],
  question: Option[String],
  country: Option[Country],
  language: Option[Language],
  getParameters: Seq[GetParameterResponse]
)

object ProposalContextResponse {
  implicit val encoder: Encoder[ProposalContextResponse] = deriveEncoder[ProposalContextResponse]
  implicit val decoder: Decoder[ProposalContextResponse] = deriveDecoder[ProposalContextResponse]

  def fromIndexedContext(context: IndexedContext): ProposalContextResponse = {
    ProposalContextResponse(
      context.operation,
      context.source,
      context.location,
      context.question,
      context.country,
      context.language,
      context.getParameters.map(GetParameterResponse.fromIndexedGetParameters)
    )
  }
}

final case class GetParameterResponse(key: String, value: String)

object GetParameterResponse {
  implicit val encoder: Encoder[GetParameterResponse] = deriveEncoder[GetParameterResponse]
  implicit val decoder: Decoder[GetParameterResponse] = deriveDecoder[GetParameterResponse]

  def fromIndexedGetParameters(parameter: IndexedGetParameters): GetParameterResponse = {
    GetParameterResponse(key = parameter.key, value = parameter.value)
  }
}

final case class OrganisationInfoResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  organisationId: UserId,
  organisationName: Option[String],
  organisationSlug: Option[String]
)

object OrganisationInfoResponse {
  implicit val encoder: Encoder[OrganisationInfoResponse] = deriveEncoder[OrganisationInfoResponse]
  implicit val decoder: Decoder[OrganisationInfoResponse] = deriveDecoder[OrganisationInfoResponse]

  def fromIndexedOrganisationInfo(indexedOrganisationInfo: IndexedOrganisationInfo): OrganisationInfoResponse = {
    OrganisationInfoResponse(
      indexedOrganisationInfo.organisationId,
      indexedOrganisationInfo.organisationName,
      indexedOrganisationInfo.organisationSlug
    )
  }
}

@ApiModel
final case class ProposalResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "927074a0-a51f-4183-8e7a-bebc705c081b")
  id: ProposalId,
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  userId: UserId,
  content: String,
  slug: String,
  @(ApiModelProperty @field)(dataType = "string", example = "Accepted")
  status: ProposalStatus,
  @(ApiModelProperty @field)(dataType = "dateTime")
  createdAt: ZonedDateTime,
  @(ApiModelProperty @field)(dataType = "dateTime")
  updatedAt: Option[ZonedDateTime],
  votes: Seq[VoteResponse],
  context: Option[ProposalContextResponse],
  trending: Option[String],
  labels: Seq[String],
  author: AuthorResponse,
  organisations: Seq[OrganisationInfoResponse],
  tags: Seq[IndexedTag],
  selectedStakeTag: Option[IndexedTag],
  myProposal: Boolean,
  @(ApiModelProperty @field)(dataType = "string", example = "2a774774-33ca-41a3-a0fa-65931397fbfc")
  idea: Option[IdeaId],
  question: Option[SimpleQuestionResponse],
  @(ApiModelProperty @field)(dataType = "string", example = "3a9cd696-7e0b-4758-952c-04ae6798039a")
  operationId: Option[OperationId],
  proposalKey: String,
  keywords: Seq[IndexedProposalKeyword]
)

object ProposalResponse extends CirceFormatters {
  implicit val encoder: Encoder[ProposalResponse] = deriveEncoder[ProposalResponse]
  implicit val decoder: Decoder[ProposalResponse] = deriveDecoder[ProposalResponse]

  def apply(
    indexedProposal: IndexedProposal,
    myProposal: Boolean,
    voteAndQualifications: Option[VoteAndQualifications],
    proposalKey: String
  ): ProposalResponse =
    ProposalResponse(
      id = indexedProposal.id,
      userId = indexedProposal.userId,
      content = indexedProposal.content,
      slug = indexedProposal.slug,
      status = indexedProposal.status,
      createdAt = indexedProposal.createdAt,
      updatedAt = indexedProposal.updatedAt,
      votes = indexedProposal.votes.map { indexedVote =>
        VoteResponse
          .parseVote(indexedVote, hasVoted = voteAndQualifications match {
            case Some(VoteAndQualifications(indexedVote.key, _, _, _)) => true
            case _                                                     => false
          }, voteAndQualifications)
      },
      context = indexedProposal.context.map(ProposalContextResponse.fromIndexedContext),
      trending = indexedProposal.trending,
      labels = indexedProposal.labels,
      author = AuthorResponse.fromIndexedAuthor(indexedProposal.author),
      organisations = indexedProposal.organisations.map(OrganisationInfoResponse.fromIndexedOrganisationInfo),
      tags = indexedProposal.tags,
      selectedStakeTag = indexedProposal.selectedStakeTag,
      myProposal = myProposal,
      idea = indexedProposal.ideaId,
      question = indexedProposal.question.map { proposalQuestion =>
        SimpleQuestionResponse(
          questionId = proposalQuestion.questionId,
          slug = proposalQuestion.slug,
          wording = SimpleQuestionWordingResponse(title = proposalQuestion.title, question = proposalQuestion.question),
          countries = proposalQuestion.countries,
          language = proposalQuestion.language,
          startDate = proposalQuestion.startDate,
          endDate = proposalQuestion.endDate
        )
      },
      operationId = indexedProposal.operationId,
      proposalKey = proposalKey,
      keywords = indexedProposal.keywords
    )
}

@ApiModel
final case class ProposalsResultResponse(total: Long, results: Seq[ProposalResponse])

object ProposalsResultResponse {
  implicit val encoder: Encoder[ProposalsResultResponse] = deriveEncoder[ProposalsResultResponse]
  implicit val decoder: Decoder[ProposalsResultResponse] = deriveDecoder[ProposalsResultResponse]
}

final case class ProposalsResultSeededResponse(
  total: Long,
  results: Seq[ProposalResponse],
  @(ApiModelProperty @field)(dataType = "int", example = "42") seed: Option[Int]
)

object ProposalsResultSeededResponse {
  implicit val encoder: Encoder[ProposalsResultSeededResponse] = deriveEncoder[ProposalsResultSeededResponse]
  implicit val decoder: Decoder[ProposalsResultSeededResponse] = deriveDecoder[ProposalsResultSeededResponse]

  val empty: ProposalsResultSeededResponse = ProposalsResultSeededResponse(0, Seq.empty, None)
}

final case class ProposalResultWithUserVote(
  proposal: ProposalResponse,
  @(ApiModelProperty @field)(dataType = "string", example = "agree")
  vote: VoteKey,
  @(ApiModelProperty @field)(dataType = "dateTime")
  voteDate: ZonedDateTime,
  voteDetails: Option[VoteResponse]
)
object ProposalResultWithUserVote extends CirceFormatters {
  implicit val encoder: Encoder[ProposalResultWithUserVote] = deriveEncoder[ProposalResultWithUserVote]
  implicit val decoder: Decoder[ProposalResultWithUserVote] = deriveDecoder[ProposalResultWithUserVote]
}

@ApiModel
final case class ProposalsResultWithUserVoteSeededResponse(
  total: Long,
  results: Seq[ProposalResultWithUserVote],
  @(ApiModelProperty @field)(dataType = "int", example = "42")
  seed: Option[Int]
)

object ProposalsResultWithUserVoteSeededResponse {
  implicit val encoder: Encoder[ProposalsResultWithUserVoteSeededResponse] =
    deriveEncoder[ProposalsResultWithUserVoteSeededResponse]
  implicit val decoder: Decoder[ProposalsResultWithUserVoteSeededResponse] =
    deriveDecoder[ProposalsResultWithUserVoteSeededResponse]
}

@ApiModel
final case class VoteResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "agree")
  voteKey: VoteKey,
  count: Int,
  qualifications: Seq[QualificationResponse],
  hasVoted: Boolean
)

object VoteResponse {

  implicit val encoder: Encoder[VoteResponse] = deriveEncoder[VoteResponse]
  implicit val decoder: Decoder[VoteResponse] = deriveDecoder[VoteResponse]

  def parseVote(vote: Vote, hasVoted: Boolean, voteAndQualifications: Option[VoteAndQualifications]): VoteResponse =
    VoteResponse(
      voteKey = vote.key,
      count = vote.count,
      qualifications = vote.qualifications
        .map(
          qualification =>
            QualificationResponse.parseQualification(qualification, hasQualified = voteAndQualifications match {
              case Some(VoteAndQualifications(_, keys, _, _)) if keys.contains(qualification.key) => true
              case _                                                                              => false
            })
        ),
      hasVoted = hasVoted
    )

  def parseVote(
    vote: IndexedVote,
    hasVoted: Boolean,
    voteAndQualifications: Option[VoteAndQualifications]
  ): VoteResponse =
    VoteResponse(
      voteKey = vote.key,
      count = vote.count,
      qualifications = vote.qualifications
        .map(
          qualification =>
            QualificationResponse.parseQualification(qualification, hasQualified = voteAndQualifications match {
              case Some(VoteAndQualifications(_, keys, _, _)) if keys.contains(qualification.key) => true
              case _                                                                              => false
            })
        ),
      hasVoted = hasVoted
    )
}

@ApiModel
final case class QualificationResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "likeIt")
  qualificationKey: QualificationKey,
  count: Int,
  hasQualified: Boolean
)

object QualificationResponse {
  implicit val encoder: Encoder[QualificationResponse] = deriveEncoder[QualificationResponse]
  implicit val decoder: Decoder[QualificationResponse] = deriveDecoder[QualificationResponse]

  def parseQualification(qualification: Qualification, hasQualified: Boolean): QualificationResponse =
    QualificationResponse(
      qualificationKey = qualification.key,
      count = qualification.count,
      hasQualified = hasQualified
    )
  def parseQualification(qualification: IndexedQualification, hasQualified: Boolean): QualificationResponse =
    QualificationResponse(
      qualificationKey = qualification.key,
      count = qualification.count,
      hasQualified = hasQualified
    )
}

final case class DuplicateResponse(
  ideaId: IdeaId,
  ideaName: String,
  proposalId: ProposalId,
  proposalContent: String,
  score: Double
)

object DuplicateResponse {
  implicit val encoder: Encoder[DuplicateResponse] = deriveEncoder[DuplicateResponse]
  implicit val decoder: Decoder[DuplicateResponse] = deriveDecoder[DuplicateResponse]
}

@ApiModel
final case class TagForProposalResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "tag-slug") id: TagId,
  label: String,
  @(ApiModelProperty @field)(dataType = "string", example = "8405aba4-4192-41d2-9a0d-b5aa6cb98d37") tagTypeId: TagTypeId,
  weight: Float,
  @(ApiModelProperty @field)(dataType = "string", example = "c1a3222e-b0d0-4142-a051-eb6ff774ed33") questionId: Option[
    QuestionId
  ],
  checked: Boolean,
  predicted: Boolean
)

object TagForProposalResponse {
  implicit val encoder: Encoder[TagForProposalResponse] = deriveEncoder[TagForProposalResponse]
  implicit val decoder: Decoder[TagForProposalResponse] = deriveDecoder[TagForProposalResponse]

  def apply(tag: Tag, checked: Boolean, predicted: Boolean): TagForProposalResponse =
    TagForProposalResponse(
      id = tag.tagId,
      label = tag.label,
      tagTypeId = tag.tagTypeId,
      weight = tag.weight,
      questionId = tag.questionId,
      checked = checked,
      predicted = predicted
    )
}

final case class TagsForProposalResponse(
  tags: Seq[TagForProposalResponse],
  @(ApiModelProperty @field)(dataType = "string", example = "auto") modelName: String
)

object TagsForProposalResponse {
  implicit val encoder: Encoder[TagsForProposalResponse] = deriveEncoder[TagsForProposalResponse]
  implicit val decoder: Decoder[TagsForProposalResponse] = deriveDecoder[TagsForProposalResponse]

  val empty: TagsForProposalResponse = TagsForProposalResponse(tags = Seq.empty, modelName = "")
}

sealed abstract class ProposalKeywordsResponseStatus(val value: String) extends StringEnumEntry

object ProposalKeywordsResponseStatus
    extends StringEnum[ProposalKeywordsResponseStatus]
    with StringCirceEnum[ProposalKeywordsResponseStatus] {

  case object Ok extends ProposalKeywordsResponseStatus("Ok")
  case object Error extends ProposalKeywordsResponseStatus("Error")

  override def values: IndexedSeq[ProposalKeywordsResponseStatus] = findValues

}

final case class ProposalKeywordsResponse(
  proposalId: ProposalId,
  status: ProposalKeywordsResponseStatus,
  message: Option[String]
)

object ProposalKeywordsResponse {
  implicit val codec: Codec[ProposalKeywordsResponse] = deriveCodec[ProposalKeywordsResponse]
}

final case class BulkActionResponse(successes: Seq[ProposalId], failures: Seq[SingleActionResponse])

object BulkActionResponse {
  implicit val codec: Codec[BulkActionResponse] = deriveCodec[BulkActionResponse]
}

final case class SingleActionResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "ab26f127-9230-4d20-82a3-08e70d58c2d4")
  proposalId: ProposalId,
  @(ApiModelProperty @field)(dataType = "string", example = "200")
  key: ActionKey,
  @(ApiModelProperty @field)(dataType = "string")
  message: Option[String]
)

object SingleActionResponse extends CirceFormatters {
  implicit val codec: Codec[SingleActionResponse] = deriveCodec[SingleActionResponse]
}

sealed abstract class ActionKey(val value: String) extends StringEnumEntry

object ActionKey extends StringEnum[ActionKey] with StringCirceEnum[ActionKey] {
  final case class ValidationError(key: String) extends ActionKey(key)
  case object NotFound extends ActionKey("not_found")
  case object OK extends ActionKey("ok")
  case object QuestionNotFound extends ActionKey("question_not_found")
  case object Unknown extends ActionKey("unknown")

  override def values: IndexedSeq[ActionKey] = findValues

}
