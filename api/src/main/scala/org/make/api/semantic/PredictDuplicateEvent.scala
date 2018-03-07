package org.make.api.semantic

import java.time.ZonedDateTime

import org.make.api.semantic.PredictDuplicateEvent.AnyPredictDuplicateEventEvent
import org.make.core.EventWrapper
import org.make.core.proposal.ProposalId
import shapeless.{:+:, CNil}

case class PredictDuplicateEvent(proposalId: ProposalId,
                                 predictedDuplicates: Seq[ProposalId],
                                 predictedScores: Seq[Double],
                                 algoLabel: String)

final case class PredictDuplicateEventWrapper(version: Int,
                                              id: String,
                                              date: ZonedDateTime,
                                              eventType: String,
                                              event: AnyPredictDuplicateEventEvent)
    extends EventWrapper

object PredictDuplicateEvent {
  type AnyPredictDuplicateEventEvent = PredictDuplicateEvent :+: CNil
}
