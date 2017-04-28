package org.make.core.vote

import java.time.ZonedDateTime

import org.make.core.StringValue
import org.make.core.citizen.CitizenId
import org.make.core.proposition.PropositionId
import org.make.core.vote.VoteStatus.VoteStatus

object VoteStatus extends Enumeration {
  type VoteStatus = Value
  val AGREE, DISAGREE, UNSURE = Value
}

case class Vote (
                  voteId: VoteId,
                  citizenId: CitizenId,
                  propositionId: PropositionId,
                  createdAt: ZonedDateTime,
                  status: VoteStatus
                )

case class VoteId(value: String) extends StringValue
