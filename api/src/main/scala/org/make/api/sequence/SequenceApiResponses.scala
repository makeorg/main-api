package org.make.api.sequence

import java.time.ZonedDateTime

import io.circe.ObjectEncoder
import io.circe.generic.semiauto.deriveEncoder
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.api.proposal.ProposalResult
import org.make.api.user.UserResponse
import org.make.core.RequestContext
import org.make.core.history.HistoryActions
import org.make.core.proposal.ProposalId
import org.make.core.reference.ThemeId
import org.make.core.sequence.indexed.IndexedStartSequence
import org.make.core.sequence.{SequenceId, SequenceStatus, SequenceTranslation}
import org.make.core.user.UserId
import org.make.core.CirceFormatters
import org.make.core.tag.TagId

import scala.annotation.meta.field

@ApiModel
final case class SequenceResponse(sequenceId: SequenceId,
                                  slug: String,
                                  title: String,
                                  @(ApiModelProperty @field)(dataType = "list[string]") tagIds: Seq[TagId] = Seq.empty,
                                  @(ApiModelProperty @field)(dataType = "list[string]") proposalIds: Seq[ProposalId] =
                                    Seq.empty,
                                  @(ApiModelProperty @field)(dataType = "list[string]") themeIds: Seq[ThemeId],
                                  status: SequenceStatus,
                                  creationContext: RequestContext,
                                  createdAt: Option[ZonedDateTime],
                                  updatedAt: Option[ZonedDateTime],
                                  sequenceTranslation: Seq[SequenceTranslation] = Seq.empty,
                                  events: Seq[SequenceActionResponse])

object SequenceResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[SequenceResponse] = deriveEncoder[SequenceResponse]
}

final case class SequenceActionResponse(date: ZonedDateTime,
                                        user: Option[UserResponse],
                                        actionType: String,
                                        arguments: Map[String, String])

object SequenceActionResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[SequenceActionResponse] = deriveEncoder[SequenceActionResponse]
}

final case class SequenceResult(id: SequenceId, title: String, slug: String, proposals: Seq[ProposalResult])

object SequenceResult {
  implicit val encoder: ObjectEncoder[SequenceResult] = deriveEncoder[SequenceResult]

  def apply(sequence: IndexedStartSequence,
            user: Option[UserId],
            votesAndQualifications: Option[HistoryActions.VoteAndQualifications]): SequenceResult = {
    SequenceResult(
      id = sequence.id,
      title = sequence.title,
      slug = sequence.slug,
      proposals = sequence.proposals.map(
        p =>
          ProposalResult(
            indexedProposal = p,
            myProposal = user.contains(p.userId),
            voteAndQualifications = votesAndQualifications
        )
      )
    )
  }
}
