package org.make.core.proposal

import java.time.ZonedDateTime

import org.make.core.{EventWrapper, MakeSerializable}
import org.make.core.user.UserId
import shapeless.{:+:, CNil, Coproduct}

sealed trait ProposalEvent extends MakeSerializable {
  def id: ProposalId
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

  case class ProposalProposed(id: ProposalId, userId: UserId, createdAt: ZonedDateTime, content: String)
      extends ProposalEvent

  case class ProposalViewed(id: ProposalId) extends ProposalEvent

  case class ProposalUpdated(id: ProposalId, updatedAt: ZonedDateTime, content: String) extends ProposalEvent
}
