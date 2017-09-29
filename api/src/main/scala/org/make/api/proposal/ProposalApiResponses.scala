package org.make.api.proposal

import java.time.ZonedDateTime

import org.make.api.user.UserResponse
import org.make.core.RequestContext
import org.make.core.proposal.indexed.Vote
import org.make.core.proposal.{ProposalId, ProposalStatus}
import org.make.core.reference.{LabelId, TagId, ThemeId}

final case class ProposalResponse(proposalId: ProposalId,
                                  slug: String,
                                  content: String,
                                  author: UserResponse,
                                  labels: Seq[LabelId],
                                  theme: Option[ThemeId] = None,
                                  status: ProposalStatus,
                                  refusalReason: Option[String] = None,
                                  tags: Seq[TagId] = Seq.empty,
                                  votes: Seq[Vote],
                                  creationContext: RequestContext,
                                  createdAt: Option[ZonedDateTime],
                                  updatedAt: Option[ZonedDateTime],
                                  events: Seq[ProposalActionResponse])

final case class ProposalActionResponse(date: ZonedDateTime,
                                        user: Option[UserResponse],
                                        actionType: String,
                                        arguments: Map[String, String])
