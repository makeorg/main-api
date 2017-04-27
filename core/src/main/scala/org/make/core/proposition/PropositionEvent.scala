package org.make.core.proposition

import java.time.ZonedDateTime

import shapeless.{:+:, CNil, Coproduct}
import org.make.core.EventWrapper
import org.make.core.citizen.CitizenId

object PropositionEvent {

  type AnyPropositionEvent = PropositionProposed :+: PropositionViewed :+: PropositionUpdated :+: CNil

  case class PropositionEventWrapper(version: Int, id: String, date: ZonedDateTime, eventType: String, event: AnyPropositionEvent)
    extends EventWrapper

  object PropositionEventWrapper {
    def wrapEvent(event: PropositionEvent): AnyPropositionEvent = event match {
      case e: PropositionProposed => Coproduct[AnyPropositionEvent](e)
      case e: PropositionViewed => Coproduct[AnyPropositionEvent](e)
      case e: PropositionUpdated => Coproduct[AnyPropositionEvent](e)
    }
  }

  sealed trait PropositionEvent {
    def id: PropositionId
  }

  case class PropositionProposed(
                          id: PropositionId,
                          citizenId: CitizenId,
                          content: String
                          ) extends PropositionEvent

  case class PropositionViewed(id: PropositionId)

  case class PropositionUpdated(id: PropositionId)
}
