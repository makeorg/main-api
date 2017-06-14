package org.make.core.vote

import java.time.ZonedDateTime

import org.make.core.{EventWrapper, MakeSerializable}
import org.make.core.user.UserId
import org.make.core.proposition.PropositionId
import org.make.core.vote.VoteStatus.VoteStatus
import shapeless.{:+:, CNil, Coproduct}

sealed trait VoteEvent extends MakeSerializable {
  def id: VoteId
}

object VoteEvent {

  type AnyVoteEvent = VotedAgree :+: VotedDisagree :+: VotedUnsure :+: CNil

  case class VoteEventWrapper(version: Int, id: String, date: ZonedDateTime, eventType: String, event: AnyVoteEvent)
      extends EventWrapper

  object VoteEventWrapper {
    def wrapEvent(event: VoteEvent): AnyVoteEvent = event match {
      case e: VotedAgree    => Coproduct[AnyVoteEvent](e)
      case e: VotedDisagree => Coproduct[AnyVoteEvent](e)
      case e: VotedUnsure   => Coproduct[AnyVoteEvent](e)
      case other            => throw new IllegalStateException(s"Unknown event: $other")
    }
  }

  case class VotedAgree(id: VoteId,
                        propositionId: PropositionId,
                        userId: UserId,
                        createdAt: ZonedDateTime,
                        status: VoteStatus)
      extends VoteEvent

  case class VotedDisagree(id: VoteId,
                           propositionId: PropositionId,
                           userId: UserId,
                           createdAt: ZonedDateTime,
                           status: VoteStatus)
      extends VoteEvent

  case class VotedUnsure(id: VoteId,
                         propositionId: PropositionId,
                         userId: UserId,
                         createdAt: ZonedDateTime,
                         status: VoteStatus)
      extends VoteEvent

  case class VoteViewed(id: VoteId, propositionId: PropositionId) extends VoteEvent
}
