package org.make.core

import org.make.core.proposal.ThemeId

// TODO: add request context (theme, debate, and so on)
final case class RequestContext(currentTheme: Option[ThemeId],
                                operation: Option[String],
                                source: Option[String],
                                location: Option[String],
                                question: Option[String])

object RequestContext {
  val empty: RequestContext = RequestContext(None, None, None, None, None)
}
