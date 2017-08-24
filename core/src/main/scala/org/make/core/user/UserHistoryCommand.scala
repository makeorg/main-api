package org.make.core.user

import org.make.core.RequestContext
import org.make.core.proposal.SearchQuery

sealed trait UserHistoryCommand {
  def userId: Option[UserId]
}

case class SearchProposalsHistoryCommand(userId: Option[UserId], query: SearchQuery, context: RequestContext)
    extends UserHistoryCommand
