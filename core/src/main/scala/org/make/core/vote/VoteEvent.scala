package org.make.core.vote

import java.time.ZonedDateTime

import shapeless.{:+:, CNil, Coproduct}
import org.make.core.EventWrapper
import org.make.core.citizen.CitizenId
import org.make.core.proposition.PropositionId

object VoteEvent {

  type AnyVoteEvent = VotedAgree :+: VotedDisagree :+: VotedUnsure :+: CNil

  case class VoteEventWrapper(version: Int, id: String, date: ZonedDateTime, eventType: String, event: AnyVoteEvent)
    extends EventWrapper

  object VoteEventWrapper {
    def wrapEvent(event: VoteEvent): AnyVoteEvent = event match {
      case e: VotedAgree => Coproduct[AnyVoteEvent](e)
      case e: VotedDisagree => Coproduct[AnyVoteEvent](e)
      case e: VotedUnsure => Coproduct[AnyVoteEvent](e)
    }
  }

  sealed trait VoteEvent {
    def id: VoteId
  }

  case class VotedAgree(
                         id: VoteId,
                         propositionId: PropositionId,
                         citizenId: CitizenId,
                         createdAt: ZonedDateTime
                       ) extends VoteEvent

  case class VotedDisagree(
                            id: VoteId,
                            propositionId: PropositionId,
                            citizenId: CitizenId,
                            createdAt: ZonedDateTime
                          ) extends VoteEvent

  case class VotedUnsure(
                          id: VoteId,
                          propositionId: PropositionId,
                          citizenId: CitizenId,
                          createdAt: ZonedDateTime
                        ) extends VoteEvent
}