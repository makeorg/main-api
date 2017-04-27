package org.make.core.vote

import java.time.ZonedDateTime

import shapeless.{:+:, CNil, Coproduct}
import org.make.core.EventWrapper
import org.make.core.citizen.CitizenId
import org.make.core.proposition.PropositionId

object VoteEvent {

  type AnyVoteEvent = VoteAgreed :+: VoteDisagreed :+: VoteUnsured :+: CNil

  case class VoteEventWrapper(version: Int, id: String, date: ZonedDateTime, eventType: String, event: AnyVoteEvent)
    extends EventWrapper

  object VoteEventWrapper {
    def wrapEvent(event: VoteEvent): AnyVoteEvent = event match {
      case e: VoteAgreed => Coproduct[AnyVoteEvent](e)
      case e: VoteDisagreed => Coproduct[AnyVoteEvent](e)
      case e: VoteUnsured => Coproduct[AnyVoteEvent](e)
    }
  }

  sealed trait VoteEvent {
    def id: VoteId
  }

  case class VoteAgreed(id: VoteId, propositionId: PropositionId, citizenId: CitizenId) extends VoteEvent

  case class VoteDisagreed(id: VoteId, propositionId: PropositionId, citizenId: CitizenId) extends VoteEvent

  case class VoteUnsured(id: VoteId, propositionId: PropositionId, citizenId: CitizenId) extends VoteEvent
}
