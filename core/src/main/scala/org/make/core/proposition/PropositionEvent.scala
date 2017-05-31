package org.make.core.proposition

import java.time.ZonedDateTime

import org.make.core.{EventWrapper, MakeSerializable}
import org.make.core.citizen.CitizenId
import shapeless.{:+:, CNil, Coproduct}

sealed trait PropositionEvent extends MakeSerializable {
  def id: PropositionId
}


object PropositionEvent {

  type AnyPropositionEvent =
    PropositionProposed :+: PropositionViewed :+: PropositionUpdated :+: CNil

  case class PropositionEventWrapper(version: Int,
                                     id: String,
                                     date: ZonedDateTime,
                                     eventType: String,
                                     event: AnyPropositionEvent)
      extends EventWrapper

  object PropositionEventWrapper {
    def wrapEvent(event: PropositionEvent): AnyPropositionEvent = event match {
      case e: PropositionProposed => Coproduct[AnyPropositionEvent](e)
      case e: PropositionViewed   => Coproduct[AnyPropositionEvent](e)
      case e: PropositionUpdated  => Coproduct[AnyPropositionEvent](e)
    }
  }

  case class PropositionProposed(id: PropositionId, citizenId: CitizenId, createdAt: ZonedDateTime, content: String)
      extends PropositionEvent

  case class PropositionViewed(id: PropositionId) extends PropositionEvent

  case class PropositionUpdated(id: PropositionId, updatedAt: ZonedDateTime, content: String) extends PropositionEvent
}
