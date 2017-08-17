package org.make.core.proposal

import java.time.ZonedDateTime

import org.make.core.{EventWrapper, MakeSerializable, RequestContext}
import org.make.core.user.UserId
import shapeless.{:+:, CNil, Coproduct}

sealed trait ProposalEvent extends MakeSerializable {
  def id: ProposalId
  def context: RequestContext
}

object ProposalEvent {

  type AnyProposalEvent =
    ProposalProposed :+: ProposalViewed :+: ProposalUpdated :+: CNil

  case class ProposalEventWrapper(version: Int,
                                  id: String,
                                  date: ZonedDateTime,
                                  eventType: String,
                                  event: AnyProposalEvent)
      extends EventWrapper

  object ProposalEventWrapper {
    def wrapEvent(event: ProposalEvent): AnyProposalEvent = event match {
      case e: ProposalProposed => Coproduct[AnyProposalEvent](e)
      case e: ProposalViewed   => Coproduct[AnyProposalEvent](e)
      case e: ProposalUpdated  => Coproduct[AnyProposalEvent](e)
    }
  }

  case class ProposalProposed(id: ProposalId,
                              slug: String,
                              context: RequestContext,
                              author: ProposalAuthorInfo,
                              userId: UserId,
                              createdAt: ZonedDateTime,
                              content: String)
      extends ProposalEvent

  case class ProposalAuthorInfo(userId: UserId, firstName: Option[String], postalCode: Option[String], age: Option[Int])

  case class ProposalViewed(id: ProposalId, context: RequestContext) extends ProposalEvent

  case class ProposalUpdated(id: ProposalId, context: RequestContext, updatedAt: ZonedDateTime, content: String)
      extends ProposalEvent
}
