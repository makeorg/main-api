package org.make.api.sequence

import java.time.ZonedDateTime

import org.make.api.user.UserResponse
import org.make.core.RequestContext
import org.make.core.proposal.ProposalId
import org.make.core.reference.{TagId, ThemeId}
import org.make.core.sequence.{SequenceId, SequenceStatus, SequenceTranslation}

final case class SequenceResponse(sequenceId: SequenceId,
                                  slug: String,
                                  title: String,
                                  tagIds: Seq[TagId] = Seq.empty,
                                  proposalIds: Seq[ProposalId] = Seq.empty,
                                  themeIds: Seq[ThemeId],
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
