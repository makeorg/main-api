package org.make.core.user

import org.make.core.RequestContext

sealed trait UserHistoryCommand {
  def userId: Option[UserId]
}

case class SearchProposalsHistoryCommand(userId: Option[UserId], queryString: String, context: RequestContext)
    extends UserHistoryCommand
