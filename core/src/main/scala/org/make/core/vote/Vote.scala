package org.make.core.vote

import java.time.ZonedDateTime

import org.make.core.StringValue
import org.make.core.citizen.CitizenId
import org.make.core.proposition.PropositionId

case class Vote (
                  voteId: VoteId,
                  citizenId: CitizenId,
                  propositionId: PropositionId,
                  createdAt: ZonedDateTime
                )

case class VoteId(value: String) extends StringValue
