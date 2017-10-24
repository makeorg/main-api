package org.make.api.sequence

import java.time.ZonedDateTime

import io.swagger.annotations.{Api, ApiModelProperty}
import org.make.api.user.UserResponse
import org.make.core.RequestContext
import org.make.core.proposal.ProposalId
import org.make.core.reference.{TagId, ThemeId}
import org.make.core.sequence.{SequenceId, SequenceStatus, SequenceTranslation}

import scala.annotation.meta.field

@Api
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

final case class SequenceActionResponse(date: ZonedDateTime,
                                        user: Option[UserResponse],
                                        actionType: String,
                                        arguments: Map[String, String])
