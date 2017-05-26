package org.make.core.proposition

import java.time.ZonedDateTime

import org.make.core.citizen.CitizenId

sealed trait PropositionCommand {
  def propositionId: PropositionId
}

case class ProposeCommand(propositionId: PropositionId,
                          citizenId: CitizenId,
                          createdAt: ZonedDateTime,
                          content: String)
    extends PropositionCommand

case class UpdatePropositionCommand(propositionId: PropositionId,
                                    updatedAt: ZonedDateTime,
                                    content: String)
    extends PropositionCommand

case class ViewPropositionCommand(propositionId: PropositionId)
    extends PropositionCommand

case class GetProposition(propositionId: PropositionId)
    extends PropositionCommand

case class KillPropositionShard(propositionId: PropositionId)
    extends PropositionCommand
