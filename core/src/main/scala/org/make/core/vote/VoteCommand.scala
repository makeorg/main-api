package org.make.core.vote

import java.time.ZonedDateTime

import org.make.core.citizen.CitizenId
import org.make.core.proposition.PropositionId

trait VoteCommand {
  def voteId: VoteId
  def propositionId: PropositionId
}

case class AgreeCommand(
                         voteId: VoteId,
                         propositionId: PropositionId,
                         citizenId: CitizenId,
                         createdAt: ZonedDateTime
                       ) extends VoteCommand

case class DisagreeCommand(
                            voteId: VoteId,
                            propositionId: PropositionId,
                            citizenId: CitizenId,
                            createdAt: ZonedDateTime
                          ) extends VoteCommand

case class UnsureCommand(
                          voteId: VoteId,
                          propositionId: PropositionId,
                          citizenId: CitizenId,
                          createdAt: ZonedDateTime
                        ) extends VoteCommand

case class GetVote(voteId: VoteId)